package silverbackgarden.example.luga.offline

import android.content.Context
import android.util.Log
import silverbackgarden.example.luga.BikeDataReport
import silverbackgarden.example.luga.StepDataReport
import silverbackgarden.example.luga.SupabaseUserManager
import silverbackgarden.example.luga.SwimDataReport

private const val TAG = "OfflineSyncQueue"
private const val TYPE_STEPS = "steps"
private const val TYPE_CYCLING = "cycling"
private const val TYPE_SWIMMING = "swimming"

/**
 * Buffers activity-data days that failed to upsert to Supabase (e.g. no network)
 * so they aren't silently dropped, and retries them on the next worker run.
 * Mirrors iOS's OfflineQueueItem Core Data queue.
 */
object OfflineSyncQueue {

    private fun dao(context: Context) = OfflineQueueDatabase.getInstance(context).offlineQueueDao()

    suspend fun enqueueSteps(context: Context, uid: String, reports: List<StepDataReport>) {
        if (reports.isEmpty()) return
        dao(context).insertAll(reports.map { OfflineQueueItem(uid = uid, date = it.date, activityType = TYPE_STEPS, value = it.steps.toDouble()) })
        Log.w(TAG, "Queued ${reports.size} day(s) of step data for retry (Supabase sync failed)")
    }

    suspend fun enqueueBike(context: Context, uid: String, reports: List<BikeDataReport>) {
        if (reports.isEmpty()) return
        dao(context).insertAll(reports.map { OfflineQueueItem(uid = uid, date = it.date, activityType = TYPE_CYCLING, value = it.meters.toDouble()) })
        Log.w(TAG, "Queued ${reports.size} day(s) of bike data for retry (Supabase sync failed)")
    }

    suspend fun enqueueSwim(context: Context, uid: String, reports: List<SwimDataReport>) {
        if (reports.isEmpty()) return
        dao(context).insertAll(reports.map { OfflineQueueItem(uid = uid, date = it.date, activityType = TYPE_SWIMMING, value = it.meters.toDouble()) })
        Log.w(TAG, "Queued ${reports.size} day(s) of swim data for retry (Supabase sync failed)")
    }

    /**
     * Attempts to flush all queued items for [uid], retrying the same suspend sync
     * methods the worker's normal 30-day path uses. Successfully synced items are
     * removed; failures are left queued for the next run. Returns the number of
     * items successfully drained.
     */
    suspend fun drainAndRetry(context: Context, uid: String, supabaseUserManager: SupabaseUserManager): Int {
        val queueDao = dao(context)
        val pending = queueDao.getAllForUser(uid)
        if (pending.isEmpty()) return 0

        var drained = 0

        val stepsItems = pending.filter { it.activityType == TYPE_STEPS }
        if (stepsItems.isNotEmpty()) {
            try {
                val (_, failed) = supabaseUserManager.syncStepDataSuspendDetailed(uid, stepsItems.map { StepDataReport(it.date, it.value.toInt()) })
                val failedDates = failed.map { it.date }.toSet()
                val succeeded = stepsItems.filter { it.date !in failedDates }
                queueDao.delete(succeeded)
                drained += succeeded.size
            } catch (e: Exception) {
                Log.e(TAG, "Retry failed for queued step data, will retry next run", e)
            }
        }

        val bikeItems = pending.filter { it.activityType == TYPE_CYCLING }
        if (bikeItems.isNotEmpty()) {
            try {
                val (_, failed) = supabaseUserManager.syncBikeDataSuspendDetailed(uid, bikeItems.map { BikeDataReport(it.date, it.value.toFloat()) })
                val failedDates = failed.map { it.date }.toSet()
                val succeeded = bikeItems.filter { it.date !in failedDates }
                queueDao.delete(succeeded)
                drained += succeeded.size
            } catch (e: Exception) {
                Log.e(TAG, "Retry failed for queued bike data, will retry next run", e)
            }
        }

        val swimItems = pending.filter { it.activityType == TYPE_SWIMMING }
        if (swimItems.isNotEmpty()) {
            try {
                val (_, failed) = supabaseUserManager.syncSwimDataSuspendDetailed(uid, swimItems.map { SwimDataReport(it.date, it.value.toFloat()) })
                val failedDates = failed.map { it.date }.toSet()
                val succeeded = swimItems.filter { it.date !in failedDates }
                queueDao.delete(succeeded)
                drained += succeeded.size
            } catch (e: Exception) {
                Log.e(TAG, "Retry failed for queued swim data, will retry next run", e)
            }
        }

        if (drained > 0) Log.i(TAG, "Drained $drained previously-queued item(s) for uid=$uid")
        return drained
    }

    suspend fun pendingCount(context: Context, uid: String): Int = dao(context).countForUser(uid)
}
