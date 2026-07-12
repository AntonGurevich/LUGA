package silverbackgarden.example.luga

import android.content.Context
import android.icu.text.SimpleDateFormat
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import silverbackgarden.example.luga.health.HealthConnectAvailability
import silverbackgarden.example.luga.offline.OfflineSyncQueue
import io.github.jan.supabase.gotrue.auth
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**
 * Background worker that periodically retrieves activity data from Health Connect
 * and synchronizes it with Supabase database.
 *
 * This worker runs every 2 hours (as configured in DashboardFragment) to ensure
 * activity data (steps, cycling, swimming) for the last 30 days is consistently
 * collected and synchronized with the Supabase backend. It handles Health Connect
 * availability/permission checks, data retrieval, duplicate checking, and database
 * insertion in the background without requiring user interaction.
 */
class StepCountWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    companion object {
        private const val TAG = "StepCountWorker"

        // Health Connect exercise types (androidx.health.connect.client.records.ExerciseSessionRecord).
        // Google splits swimming and cycling into several sub-types rather than one generic type
        // each, so all of a category's types must be matched to capture a user's real total distance.
        private val CYCLING_EXERCISE_TYPES = setOf(
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY
        )
        private val SWIMMING_EXERCISE_TYPES = setOf(
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER
        )

        private val REQUIRED_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class)
        )

        // Fine slot width for per-origin bucket slicing in aggregateBestSource() — used to
        // surgically exclude manual-entry windows within one origin.
        private const val BUCKET_MINUTES = 15L

        // Origins that re-export an already-merged composite of other sources rather than
        // recording anything themselves. The Google Fit app syncs Fit's own multi-source
        // merge (phone + anything synced into Fit, incl. the same watch data other origins
        // already provide) into Health Connect, so its totals exceed every real device.
        // These origins only compete when NO primary source has data for the range.
        private val COMPOSITE_ORIGINS = setOf("com.google.android.apps.fitness")

        // Coarse window width for cross-origin stitching: within each window the best
        // single origin wins, so device alternation (watch-only workout in the morning,
        // phone-only afternoon) is captured. Only origins whose records are finer than
        // the window may compete inside windows — a source that writes big delayed
        // blocks can't prove WHEN its steps happened, and letting Health Connect smear
        // it across windows stacked phantom night steps on top of real-time sources
        // (measured: 17.1k reported vs 9.4k on the watch). Chunky origins instead
        // compete with their whole-range total against the stitched result.
        private const val STITCH_WINDOW_MINUTES = 180L
    }

    /**
     * True if [this] record was typed in by the user via manual entry (a fitness app's "Add
     * activity" / "Add steps" screen) rather than recorded by a sensor, wearable, or automatic
     * detection. Fails open (returns false) for RECORDING_METHOD_UNKNOWN, so third-party apps
     * that haven't adopted this newer metadata field aren't wrongly stripped of real data.
     */
    private fun Metadata.isManualEntry(): Boolean {
        return recordingMethod == Metadata.RECORDING_METHOD_MANUAL_ENTRY
    }

    private val supabaseUserManager = SupabaseUserManager()
    private val authStatePrefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    /**
     * Main work method that executes the activity data synchronization process.
     * This method runs on a background thread and handles all the core functionality
     * including Health Connect data retrieval and Supabase synchronization for steps, cycling,
     * and swimming data over the last 30 days.
     *
     * @return Result.success() if work completes successfully, Result.retry() if it should be retried
     */
    override fun doWork(): Result = runBlocking {
        try {
            Log.i(TAG, "🚀 WORKER STARTED: Activity Data Sync (Steps, Bike, Swim) - Last 30 Days")
            Log.d(TAG, "Worker ID: $id, runAttempt: $runAttemptCount")

            if (HealthConnectAvailability.check(applicationContext) != HealthConnectAvailability.Status.AVAILABLE) {
                Log.w(TAG, "❌ Health Connect not available, skipping work")
                return@runBlocking Result.success() // Don't retry, just succeed
            }

            val healthConnectClient = HealthConnectClient.getOrCreate(applicationContext)
            val grantedPermissions = healthConnectClient.permissionController.getGrantedPermissions()
            if (!grantedPermissions.containsAll(REQUIRED_PERMISSIONS)) {
                Log.w(TAG, "❌ User hasn't granted the required Health Connect permissions, skipping work")
                return@runBlocking Result.success() // Don't retry, just succeed
            }

            Log.d(TAG, "✅ Health Connect availability and permissions verified")

            // Ensure auth state is loaded before checking current user in a background worker process.
            val userUid = resolveAuthenticatedUserUid()
            if (userUid == null) {
                val hasLocalLoggedInState = authStatePrefs.getBoolean("is_logged_in", false)
                if (hasLocalLoggedInState) {
                    Log.w(TAG, "❌ Supabase session not yet available in worker, retrying")
                    return@runBlocking Result.retry()
                }
                Log.w(TAG, "❌ No Supabase user signed in, skipping work")
                return@runBlocking Result.success()
            }

            Log.i(TAG, "📊 Processing activity data for user: $userUid")

            // Drain any previously-queued days that failed to sync last time (offline queue)
            // before attempting the normal 30-day sync below.
            val drainedCount = OfflineSyncQueue.drainAndRetry(applicationContext, userUid, supabaseUserManager)
            if (drainedCount > 0) {
                Log.i(TAG, "♻️ Drained $drainedCount previously-queued day(s) from the offline sync queue")
            }

            // Retrieve activity data for the last 30 days concurrently
            val stepDataReports = (1..30).map { dayOffset ->
                async { getStepDataForDay(dayOffset, healthConnectClient) }
            }.awaitAll().filterNotNull()

            val bikeDataReports = (1..30).map { dayOffset ->
                async { getBikeDataForDay(dayOffset, healthConnectClient) }
            }.awaitAll().filterNotNull()

            val swimDataReports = (1..30).map { dayOffset ->
                async { getSwimDataForDay(dayOffset, healthConnectClient) }
            }.awaitAll().filterNotNull()

            // Sync collected activity data with Supabase
            var totalSynced = 0

            if (stepDataReports.isNotEmpty()) {
                Log.d("StepCountWorker", "Syncing ${stepDataReports.size} days of step data to Supabase (last 30 days)")
                val (stepCount, failedSteps) = supabaseUserManager.syncStepDataSuspendDetailed(userUid, stepDataReports)
                totalSynced += stepCount
                OfflineSyncQueue.enqueueSteps(applicationContext, userUid, failedSteps)
            }

            if (bikeDataReports.isNotEmpty()) {
                Log.d("StepCountWorker", "Syncing ${bikeDataReports.size} days of bike data to Supabase (last 30 days)")
                val (bikeCount, failedBike) = supabaseUserManager.syncBikeDataSuspendDetailed(userUid, bikeDataReports)
                totalSynced += bikeCount
                OfflineSyncQueue.enqueueBike(applicationContext, userUid, failedBike)
            }

            if (swimDataReports.isNotEmpty()) {
                Log.d("StepCountWorker", "Syncing ${swimDataReports.size} days of swim data to Supabase (last 30 days)")
                val (swimCount, failedSwim) = supabaseUserManager.syncSwimDataSuspendDetailed(userUid, swimDataReports)
                totalSynced += swimCount
                OfflineSyncQueue.enqueueSwim(applicationContext, userUid, failedSwim)
            }

            if (totalSynced > 0) {
                // Update shared preferences with last sync time
                val prefs = applicationContext.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putLong("supabase_last_sync", System.currentTimeMillis())
                    .putString("supabase_last_sync_steps", stepDataReports.joinToString(",") { it.date })
                    .putString("supabase_last_sync_bike", bikeDataReports.joinToString(",") { it.date })
                    .putString("supabase_last_sync_swim", swimDataReports.joinToString(",") { it.date })
                    .putBoolean("token_data_needs_refresh", true) // Flag to refresh token data after sync
                    .apply()

                Log.d("StepCountWorker", "30-day activity sync completed. Steps: ${stepDataReports.size}, Bike: ${bikeDataReports.size}, Swim: ${swimDataReports.size}")
                Log.d("StepCountWorker", "Token data refresh flag set - DashboardFragment will refresh token data on next resume")
            } else {
                Log.w("StepCountWorker", "No activity data available for 30-day Supabase sync")
            }

            Log.i(TAG, "✅ 30-Day Activity Data Sync Completed Successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Worker failed with exception", e)
            Result.retry()
        }
    }

    /**
     * Retrieves step count data for a specific day from Health Connect.
     * This method specifically targets individual days for Supabase synchronization.
     *
     * @param dayOffset Number of days ago (1 = yesterday, 2 = day before yesterday, etc.)
     * @param client Health Connect client with granted read permissions
     * @return StepDataReport object containing the day's step data, or null if retrieval fails
     */
    private suspend fun getStepDataForDay(dayOffset: Int, client: HealthConnectClient): StepDataReport? {
        return withContext(Dispatchers.IO) {
            try {
                // Calculate the start and end times for the specified day
                val cal = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -dayOffset)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startTime = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val endTime = cal.timeInMillis

                val dateString = getDateString(-dayOffset)
                Log.d("StepCountWorker", "Getting steps for $dateString (day $dayOffset): ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(startTime))} to ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(endTime))}")

                val totalSteps = aggregateBestSource(
                    client = client,
                    recordType = StepsRecord::class,
                    metric = StepsRecord.COUNT_TOTAL,
                    startMillis = startTime,
                    endMillis = endTime,
                    recordWindow = { it.startTime.toEpochMilli() to it.endTime.toEpochMilli() }
                ) { it.toDouble() }.toInt()

                Log.d("StepCountWorker", "Retrieved step data for $dateString: $totalSteps steps")

                // Create and return a StepDataReport with the collected data
                StepDataReport(dateString, totalSteps)
            } catch (e: Exception) {
                Log.e("StepCountWorker", "Failed to get steps for day $dayOffset", e)
                null
            }
        }
    }

    /**
     * Retrieves bike distance data for a specific day from Health Connect.
     * Uses exercise sessions to identify cycling periods and correlates with distance data.
     *
     * @param dayOffset Number of days ago (1 = yesterday, 2 = day before yesterday, etc.)
     * @param client Health Connect client with granted read permissions
     * @return BikeDataReport object containing the day's cycling distance data, or null if retrieval fails
     */
    private suspend fun getBikeDataForDay(dayOffset: Int, client: HealthConnectClient): BikeDataReport? {
        return withContext(Dispatchers.IO) {
            try {
                // Calculate the start and end times for the specified day
                val cal = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -dayOffset)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                val startTime = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                val endTime = cal.timeInMillis

                val dateString = getDateString(-dayOffset)

                // First, get exercise sessions to find cycling periods
                val sessionsResponse = client.readRecords(
                    ReadRecordsRequest(
                        recordType = ExerciseSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(endTime))
                    )
                )

                // Find cycling exercise sessions (all sub-types — see CYCLING_EXERCISE_TYPES),
                // excluding sessions the user typed in manually.
                val cyclingSessions = sessionsResponse.records.filter { session ->
                    session.exerciseType in CYCLING_EXERCISE_TYPES && !session.metadata.isManualEntry()
                }

                if (cyclingSessions.isEmpty()) {
                    Log.d("StepCountWorker", "No cycling activities found for $dateString - returning 0.0m")
                    return@withContext BikeDataReport(dateString, 0.0f)
                }

                Log.d("StepCountWorker", "Found ${cyclingSessions.size} cycling sessions for $dateString")

                // Merge overlapping session time-windows first: if multiple sources log
                // overlapping sessions of different cycling sub-types for the same ride
                // (e.g. a watch logs "road biking" while a phone app logs generic "biking"
                // for the same period), querying distance per-session would double-count
                // that overlap. Querying merged, non-overlapping windows instead guarantees
                // each moment in time is only counted once.
                val mergedRanges = mergeOverlappingTimeRanges(cyclingSessions.map {
                    it.startTime.toEpochMilli() to it.endTime.toEpochMilli()
                })
                var totalCyclingDistance = 0.0f

                for ((rangeStart, rangeEnd) in mergedRanges) {
                    val rangeDistance = getDedupedDistanceMeters(client, rangeStart, rangeEnd)
                    totalCyclingDistance += rangeDistance
                    Log.d("StepCountWorker", "Cycling range: ${rangeDistance}m")
                }

                Log.d("StepCountWorker", "Retrieved bike data for $dateString: ${totalCyclingDistance}m")
                BikeDataReport(dateString, totalCyclingDistance)

            } catch (e: Exception) {
                Log.e("StepCountWorker", "Failed to get bike data for day $dayOffset", e)
                null
            }
        }
    }

    /**
     * Retrieves swimming distance data for a specific day from Health Connect.
     * Uses exercise sessions to identify swimming periods and correlates with distance data.
     *
     * @param dayOffset Number of days ago (1 = yesterday, 2 = day before yesterday, etc.)
     * @param client Health Connect client with granted read permissions
     * @return SwimDataReport object containing the day's swimming distance data, or null if retrieval fails
     */
    private suspend fun getSwimDataForDay(dayOffset: Int, client: HealthConnectClient): SwimDataReport? {
        return withContext(Dispatchers.IO) {
            try {
                // Calculate the start and end times for the specified day
                val cal = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -dayOffset)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                val startTime = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                val endTime = cal.timeInMillis

                val dateString = getDateString(-dayOffset)

                // First, get exercise sessions to find swimming periods
                val sessionsResponse = client.readRecords(
                    ReadRecordsRequest(
                        recordType = ExerciseSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(endTime))
                    )
                )

                // Find swimming exercise sessions (pool/open-water — see SWIMMING_EXERCISE_TYPES),
                // excluding sessions the user typed in manually.
                val swimmingSessions = sessionsResponse.records.filter { session ->
                    session.exerciseType in SWIMMING_EXERCISE_TYPES && !session.metadata.isManualEntry()
                }

                if (swimmingSessions.isEmpty()) {
                    Log.d("StepCountWorker", "No swimming activities found for $dateString - returning 0.0m")
                    return@withContext SwimDataReport(dateString, 0.0f)
                }

                Log.d("StepCountWorker", "Found ${swimmingSessions.size} swimming sessions for $dateString")

                // Merge overlapping session time-windows first (see the matching comment in
                // getBikeDataForDay) so a swim double-logged as both pool + open-water
                // by different sources isn't double-counted.
                val mergedRanges = mergeOverlappingTimeRanges(swimmingSessions.map {
                    it.startTime.toEpochMilli() to it.endTime.toEpochMilli()
                })
                var totalSwimmingDistance = 0.0f

                for ((rangeStart, rangeEnd) in mergedRanges) {
                    val rangeDistance = getDedupedDistanceMeters(client, rangeStart, rangeEnd)
                    totalSwimmingDistance += rangeDistance
                    Log.d("StepCountWorker", "Swimming range: ${rangeDistance}m")
                }

                Log.d("StepCountWorker", "Retrieved swim data for $dateString: ${totalSwimmingDistance}m")
                SwimDataReport(dateString, totalSwimmingDistance)

            } catch (e: Exception) {
                Log.e("StepCountWorker", "Failed to get swim data for day $dayOffset", e)
                null
            }
        }
    }

    /**
     * Multi-source dedup: computes a total for [metric] over [startMillis, endMillis]
     * without double-counting the same physical activity written to Health Connect by
     * multiple apps (watch + phone + sync apps like Samsung Health / Health Sync).
     *
     * How it works:
     * 1. One raw read discovers which apps (data origins) wrote records in the range,
     *    and where any manually-typed entries lie in time.
     * 2. Per origin, Health Connect's aggregateGroupByDuration() slices that origin's
     *    records into aligned [BUCKET_MINUTES]-wide buckets (the platform handles
     *    splitting records across bucket boundaries).
     * 3. Buckets overlapping one of that origin's manual-entry windows are dropped —
     *    typed-in data never reaches the total, and costs at most its own buckets.
     * 4. Fine-grained origins (all records shorter than [STITCH_WINDOW_MINUTES]) are
     *    rolled up into stitching windows; the best single origin wins each window and
     *    windows are summed — capturing device alternation (watch-only workout in the
     *    morning, phone-only afternoon). Chunky origins (any record longer than the
     *    window) can't prove when their steps happened, so they compete only with
     *    their whole-range total. Composite re-aggregator origins ([COMPOSITE_ORIGINS])
     *    compete only when no primary source has data at all. The result is
     *    max(stitched fine total, best chunky total) — sources are alternative
     *    measurements of the same person, never additive parts.
     */
    private suspend fun <R : Record, T : Any> aggregateBestSource(
        client: HealthConnectClient,
        recordType: KClass<R>,
        metric: AggregateMetric<T>,
        startMillis: Long,
        endMillis: Long,
        recordWindow: (R) -> Pair<Long, Long>,
        toDouble: (T) -> Double
    ): Double {
        val timeRange = TimeRangeFilter.between(Instant.ofEpochMilli(startMillis), Instant.ofEpochMilli(endMillis))

        val rawRecords = client.readRecords(ReadRecordsRequest(recordType, timeRangeFilter = timeRange)).records
        if (rawRecords.isEmpty()) return 0.0

        val manualWindowsByOrigin = rawRecords
            .filter { it.metadata.isManualEntry() }
            .groupBy({ it.metadata.dataOrigin }, { recordWindow(it) })
        val origins = rawRecords.map { it.metadata.dataOrigin }.toSet()

        val stitchWindowMillis = STITCH_WINDOW_MINUTES * 60_000L

        // Composite (re-aggregator) origins only compete when no primary source exists.
        val primaryOrigins = origins.filter { it.packageName !in COMPOSITE_ORIGINS }.toSet()
        val consideredOrigins = primaryOrigins.ifEmpty { origins }

        // An origin may only compete inside stitching windows if all its records are
        // finer than the window; otherwise its steps can't be located in time and it
        // competes with its whole-range total instead.
        val chunkyOrigins = rawRecords
            .groupBy { it.metadata.dataOrigin }
            .filterValues { records ->
                records.any {
                    val (recStart, recEnd) = recordWindow(it)
                    recEnd - recStart > stitchWindowMillis
                }
            }
            .keys

        // stitchWindow start -> (fine origin -> origin's total within that window)
        val windowOriginTotals = HashMap<Long, HashMap<String, Double>>()
        var bestChunkyTotal = 0.0

        for (origin in consideredOrigins) {
            val buckets = client.aggregateGroupByDuration(
                AggregateGroupByDurationRequest(
                    metrics = setOf(metric),
                    timeRangeFilter = timeRange,
                    timeRangeSlicer = Duration.ofMinutes(BUCKET_MINUTES),
                    dataOriginFilter = setOf(origin)
                )
            )
            val manualWindows = manualWindowsByOrigin[origin].orEmpty()
            if (manualWindows.isNotEmpty()) {
                Log.w("StepCountWorker", "Origin ${origin.packageName} has ${manualWindows.size} manual ${recordType.simpleName} entr(ies); overlapping buckets excluded")
            }
            val isChunky = origin in chunkyOrigins
            var originTotal = 0.0
            for (bucket in buckets) {
                val bucketStart = bucket.startTime.toEpochMilli()
                val bucketEnd = bucket.endTime.toEpochMilli()
                if (manualWindows.any { (mStart, mEnd) -> mStart < bucketEnd && mEnd > bucketStart }) continue
                val value = bucket.result[metric] ?: continue
                val v = toDouble(value)
                originTotal += v
                if (!isChunky) {
                    val stitchWindowStart = startMillis + ((bucketStart - startMillis) / stitchWindowMillis) * stitchWindowMillis
                    windowOriginTotals.getOrPut(stitchWindowStart) { HashMap() }
                        .merge(origin.packageName, v, Double::plus)
                }
            }
            Log.d("StepCountWorker", "Origin ${origin.packageName}: $originTotal ${recordType.simpleName}${if (isChunky) " (chunky)" else ""}")
            if (isChunky && originTotal > bestChunkyTotal) bestChunkyTotal = originTotal
        }

        // Fine origins stitch window by window; the stitched result competes against the
        // best chunky origin's whole-range total.
        val stitchedFineTotal = windowOriginTotals.values.sumOf { it.values.max() }
        return maxOf(stitchedFineTotal, bestChunkyTotal)
    }

    /**
     * Deduped distance total (meters) for a time window, via [aggregateBestSource].
     */
    private suspend fun getDedupedDistanceMeters(client: HealthConnectClient, rangeStart: Long, rangeEnd: Long): Float {
        return aggregateBestSource(
            client = client,
            recordType = DistanceRecord::class,
            metric = DistanceRecord.DISTANCE_TOTAL,
            startMillis = rangeStart,
            endMillis = rangeEnd,
            recordWindow = { it.startTime.toEpochMilli() to it.endTime.toEpochMilli() }
        ) { it.inMeters }.toFloat()
    }

    /**
     * Merges potentially-overlapping session time-windows into the minimal set of
     * disjoint (start, end) ranges (both in epoch milliseconds). Used before querying
     * DistanceRecord so that when multiple sessions (e.g. different cycling/swimming
     * sub-types from different data sources) cover the same or overlapping time, that
     * overlap is only queried — and summed — once instead of once per matching session.
     */
    private fun mergeOverlappingTimeRanges(segments: List<Pair<Long, Long>>): List<Pair<Long, Long>> {
        if (segments.isEmpty()) return emptyList()

        val sorted = segments.sortedBy { it.first }

        val merged = mutableListOf<Pair<Long, Long>>()
        var (currentStart, currentEnd) = sorted.first()

        for ((start, end) in sorted.drop(1)) {
            if (start <= currentEnd) {
                // Overlaps (or is adjacent to) the current range — extend it.
                currentEnd = maxOf(currentEnd, end)
            } else {
                merged.add(currentStart to currentEnd)
                currentStart = start
                currentEnd = end
            }
        }
        merged.add(currentStart to currentEnd)
        return merged
    }

    /**
     * Generates a date string in "yyyy-MM-dd" format for a specified number of days ago.
     * Used to create date identifiers for step data when storing in Supabase.
     *
     * @param daysAgo Number of days ago (negative value)
     * @return Date string in "yyyy-MM-dd" format
     */
    private fun getDateString(daysAgo: Int): String {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, daysAgo)
        }
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
    }

    /**
     * Resolves the current authenticated user UID, forcing auth session reload when needed.
     */
    private suspend fun resolveAuthenticatedUserUid(): String? = withContext(Dispatchers.IO) {
        val auth = SupabaseClient.client.auth

        // Fast path when session is already in memory.
        auth.currentUserOrNull()?.id?.toString()?.let { return@withContext it }

        return@withContext try {
            // Worker may run in a fresh process where in-memory auth state is not yet restored.
            auth.loadFromStorage(true)
            auth.currentUserOrNull()?.id?.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load auth session from storage", e)
            null
        }
    }

}
