package silverbackgarden.example.luga

import android.content.Context
import android.icu.text.SimpleDateFormat
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.DataReadRequest
import silverbackgarden.example.luga.SupabaseClient
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Background worker that periodically retrieves activity data from Google Fit
 * and synchronizes it with Supabase database.
 * 
 * This worker runs every 6 hours (as configured in CentralActivity) to ensure
 * activity data (steps, cycling, swimming) for the last 30 days is consistently 
 * collected and synchronized with the Supabase backend. It handles Google Fit 
 * authentication, data retrieval, duplicate checking, and database insertion 
 * in the background without requiring user interaction.
 */
class StepCountWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    private val supabaseUserManager = SupabaseUserManager()

    /**
     * Main work method that executes the activity data synchronization process.
     * This method runs on a background thread and handles all the core functionality
     * including Google Fit data retrieval and Supabase synchronization for steps, cycling, 
     * and swimming data over the last 30 days.
     * 
     * @return Result.success() if work completes successfully, Result.retry() if it should be retried
     */
    override fun doWork(): Result = runBlocking {
        try {
            Log.i("StepCountWorker", "🚀 WORKER STARTED: Activity Data Sync (Steps, Bike, Swim) - Last 30 Days")
            Log.d("StepCountWorker", "Worker ID: ${id}, Run attempt: ${runAttemptCount}")
            
            // Configure Google Fit options for activity data access
            val fitnessOptions = FitnessOptions.builder()
                .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_READ)
                .build()
                
            // Get the currently signed-in Google account
            val account = GoogleSignIn.getAccountForExtension(applicationContext, fitnessOptions)
            
            // Check if user is actually signed in and has permissions
            if (account == null) {
                Log.w("StepCountWorker", "❌ No Google account signed in, skipping work")
                return@runBlocking Result.success() // Don't retry, just succeed
            }
            
            // Verify that the user has granted the required Google Fit permissions
            if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
                Log.w("StepCountWorker", "❌ User doesn't have Google Fit permissions, skipping work")
                return@runBlocking Result.success() // Don't retry, just succeed
            }
            
            Log.d("StepCountWorker", "✅ Google account and permissions verified")
            
            // Get current Supabase user ID via manager
            val userUid = supabaseUserManager.getCurrentUserUid()
            if (userUid == null) {
                Log.w("StepCountWorker", "❌ No Supabase user signed in, skipping work")
                return@runBlocking Result.success()
            }
            
            Log.i("StepCountWorker", "📊 Processing activity data for user: $userUid")
            
            // Retrieve activity data for the last 30 days concurrently
            val stepDataReports = (1..30).map { dayOffset ->
                async { getStepDataForDay(dayOffset, account) }
            }.awaitAll().filterNotNull()
            
            val bikeDataReports = (1..30).map { dayOffset ->
                async { getBikeDataForDay(dayOffset, account) }
            }.awaitAll().filterNotNull()
            
            val swimDataReports = (1..30).map { dayOffset ->
                async { getSwimDataForDay(dayOffset, account) }
            }.awaitAll().filterNotNull()
            
            // Sync collected activity data with Supabase
            var totalSynced = 0
            
            if (stepDataReports.isNotEmpty()) {
                Log.d("StepCountWorker", "Syncing ${stepDataReports.size} days of step data to Supabase (last 30 days)")
                syncStepsToSupabase(userUid, stepDataReports)
                totalSynced += stepDataReports.size
            }
            
            if (bikeDataReports.isNotEmpty()) {
                Log.d("StepCountWorker", "Syncing ${bikeDataReports.size} days of bike data to Supabase (last 30 days)")
                syncBikeToSupabase(userUid, bikeDataReports)
                totalSynced += bikeDataReports.size
            }
            
            if (swimDataReports.isNotEmpty()) {
                Log.d("StepCountWorker", "Syncing ${swimDataReports.size} days of swim data to Supabase (last 30 days)")
                syncSwimToSupabase(userUid, swimDataReports)
                totalSynced += swimDataReports.size
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
                Log.d("StepCountWorker", "Token data refresh flag set - CentralActivity will refresh token data on next resume")
            } else {
                Log.w("StepCountWorker", "No activity data available for 30-day Supabase sync")
            }
            
            Log.i("StepCountWorker", "✅ 30-Day Activity Data Sync Completed Successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e("StepCountWorker", "❌ Worker failed with exception", e)
            Result.retry()
        }
    }
    
    /**
     * Retrieves step count data for a specific day from Google Fit.
     * This method specifically targets individual days for Supabase synchronization.
     * 
     * @param dayOffset Number of days ago (1 = yesterday, 2 = day before yesterday, etc.)
     * @param account Google Sign-In account with Fitness permissions
     * @return StepDataReport object containing the day's step data, or null if retrieval fails
     */
    private suspend fun getStepDataForDay(dayOffset: Int, account: GoogleSignInAccount): StepDataReport? {
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
                
                // Build the data read request for step count data
                val readRequest = DataReadRequest.Builder()
                    .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                    .read(DataType.TYPE_STEP_COUNT_DELTA)
                    .build()
                
                // Execute the read request and wait for the response
                val response = Fitness.getHistoryClient(applicationContext, account)
                    .readData(readRequest)
                    .await()
                
                // Calculate total steps for the day by summing all data points
                val dataSet = response.getDataSet(DataType.TYPE_STEP_COUNT_DELTA)
                val totalSteps = if (dataSet.isEmpty) {
                    Log.d("StepCountWorker", "No step data points found for day $dayOffset")
                    0
                } else {
                    val steps = dataSet.dataPoints.sumOf { it.getValue(Field.FIELD_STEPS).asInt() }
                    Log.d("StepCountWorker", "Found ${dataSet.dataPoints.size} data points for day $dayOffset, total: $steps steps")
                    steps
                }
                
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
     * Retrieves bike distance data for a specific day from Google Fit.
     * Uses activity segments to identify cycling periods and correlates with distance data.
     * 
     * @param dayOffset Number of days ago (1 = yesterday, 2 = day before yesterday, etc.)
     * @param account Google Sign-In account with Fitness permissions
     * @return BikeDataReport object containing the day's cycling distance data, or null if retrieval fails
     */
    private suspend fun getBikeDataForDay(dayOffset: Int, account: GoogleSignInAccount): BikeDataReport? {
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
                
                // First, get activity segments to find cycling periods
                val activityRequest = DataReadRequest.Builder()
                    .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                    .read(DataType.TYPE_ACTIVITY_SEGMENT)
                    .build()
                
                val activityResponse = Fitness.getHistoryClient(applicationContext, account)
                    .readData(activityRequest)
                    .await()
                
                // Find cycling activity segments (activity type 1 = cycling)
                val cyclingSegments = activityResponse.getDataSet(DataType.TYPE_ACTIVITY_SEGMENT)
                    .dataPoints
                    .filter { dataPoint ->
                        val activityField = dataPoint.getValue(Field.FIELD_ACTIVITY)
                        activityField != null && activityField.asInt() == 1 // 1 = cycling
                    }
                
                val dateString = getDateString(-dayOffset)
                
                if (cyclingSegments.isEmpty()) {
                    Log.d("StepCountWorker", "No cycling activities found for $dateString - returning 0.0m")
                    return@withContext BikeDataReport(dateString, 0.0f)
                }
                
                Log.d("StepCountWorker", "Found ${cyclingSegments.size} cycling segments for $dateString")
                
                // For each cycling segment, get distance data during that time period
                var totalCyclingDistance = 0.0f
                
                for (segment in cyclingSegments) {
                    val segmentStart = segment.getStartTime(TimeUnit.MILLISECONDS)
                    val segmentEnd = segment.getEndTime(TimeUnit.MILLISECONDS)
                    
                    val distanceRequest = DataReadRequest.Builder()
                        .setTimeRange(segmentStart, segmentEnd, TimeUnit.MILLISECONDS)
                        .read(DataType.TYPE_DISTANCE_DELTA)
                        .build()
                    
                    val distanceResponse = Fitness.getHistoryClient(applicationContext, account)
                        .readData(distanceRequest)
                        .await()
                    
                    val segmentDistance = distanceResponse.getDataSet(DataType.TYPE_DISTANCE_DELTA)
                        .dataPoints
                        .sumOf { it.getValue(Field.FIELD_DISTANCE).asFloat().toDouble() }
                        .toFloat()
                    
                    totalCyclingDistance += segmentDistance
                    Log.d("StepCountWorker", "Cycling segment: ${segmentDistance}m")
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
     * Retrieves swimming distance data for a specific day from Google Fit.
     * Uses activity segments to identify swimming periods and correlates with distance data.
     * 
     * @param dayOffset Number of days ago (1 = yesterday, 2 = day before yesterday, etc.)
     * @param account Google Sign-In account with Fitness permissions
     * @return SwimDataReport object containing the day's swimming distance data, or null if retrieval fails
     */
    private suspend fun getSwimDataForDay(dayOffset: Int, account: GoogleSignInAccount): SwimDataReport? {
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
                
                // First, get activity segments to find swimming periods
                val activityRequest = DataReadRequest.Builder()
                    .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                    .read(DataType.TYPE_ACTIVITY_SEGMENT)
                    .build()
                
                val activityResponse = Fitness.getHistoryClient(applicationContext, account)
                    .readData(activityRequest)
                    .await()
                
                // Find swimming activity segments (activity type 9 = swimming)
                val swimmingSegments = activityResponse.getDataSet(DataType.TYPE_ACTIVITY_SEGMENT)
                    .dataPoints
                    .filter { dataPoint ->
                        val activityField = dataPoint.getValue(Field.FIELD_ACTIVITY)
                        activityField != null && activityField.asInt() == 9 // 9 = swimming
                    }
                
                val dateString = getDateString(-dayOffset)
                
                if (swimmingSegments.isEmpty()) {
                    Log.d("StepCountWorker", "No swimming activities found for $dateString - returning 0.0m")
                    return@withContext SwimDataReport(dateString, 0.0f)
                }
                
                Log.d("StepCountWorker", "Found ${swimmingSegments.size} swimming segments for $dateString")
                
                // For each swimming segment, get distance data during that time period
                var totalSwimmingDistance = 0.0f
                
                for (segment in swimmingSegments) {
                    val segmentStart = segment.getStartTime(TimeUnit.MILLISECONDS)
                    val segmentEnd = segment.getEndTime(TimeUnit.MILLISECONDS)
                    
                    val distanceRequest = DataReadRequest.Builder()
                        .setTimeRange(segmentStart, segmentEnd, TimeUnit.MILLISECONDS)
                        .read(DataType.TYPE_DISTANCE_DELTA)
                        .build()
                    
                    val distanceResponse = Fitness.getHistoryClient(applicationContext, account)
                        .readData(distanceRequest)
                        .await()
                    
                    val segmentDistance = distanceResponse.getDataSet(DataType.TYPE_DISTANCE_DELTA)
                        .dataPoints
                        .sumOf { it.getValue(Field.FIELD_DISTANCE).asFloat().toDouble() }
                        .toFloat()
                    
                    totalSwimmingDistance += segmentDistance
                    Log.d("StepCountWorker", "Swimming segment: ${segmentDistance}m")
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
     * Synchronizes step data to Supabase using the SupabaseUserManager.
     * This method handles the duplicate checking and insertion logic.
     * 
     * @param userUid User's UID from Supabase Auth
     * @param stepDataReports List of StepDataReport objects to sync
     */
    private suspend fun syncStepsToSupabase(userUid: String, stepDataReports: List<StepDataReport>) {
        return withContext(Dispatchers.Main) {
            try {
                Log.d("StepCountWorker", "Starting Supabase step sync for ${stepDataReports.size} days")
                
                supabaseUserManager.syncStepData(userUid, stepDataReports, object : SupabaseUserManager.DatabaseCallback<Int> {
                    override fun onSuccess(result: Int) {
                        Log.d("StepCountWorker", "Successfully synced $result new step records to Supabase")
                        // Set flag to refresh token data after sync
                        val prefs = applicationContext.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                        prefs.edit().putBoolean("token_data_needs_refresh", true).apply()
                        Log.d("StepCountWorker", "Token data refresh flag set after step sync")
                    }
                    
                    override fun onError(error: String) {
                        Log.e("StepCountWorker", "Failed to sync step data to Supabase: $error")
                    }
                })
                
            } catch (e: Exception) {
                Log.e("StepCountWorker", "Error during Supabase step sync: ${e.message}", e)
            }
        }
    }

    /**
     * Synchronizes bike data to Supabase using the SupabaseUserManager.
     * 
     * @param userUid User's UID from Supabase Auth
     * @param bikeDataReports List of BikeDataReport objects to sync
     */
    private suspend fun syncBikeToSupabase(userUid: String, bikeDataReports: List<BikeDataReport>) {
        return withContext(Dispatchers.Main) {
            try {
                Log.d("StepCountWorker", "Starting Supabase bike sync for ${bikeDataReports.size} days")
                
                supabaseUserManager.syncBikeData(userUid, bikeDataReports, object : SupabaseUserManager.DatabaseCallback<Int> {
                    override fun onSuccess(result: Int) {
                        Log.d("StepCountWorker", "Successfully synced $result new bike records to Supabase")
                        // Set flag to refresh token data after sync
                        val prefs = applicationContext.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                        prefs.edit().putBoolean("token_data_needs_refresh", true).apply()
                        Log.d("StepCountWorker", "Token data refresh flag set after bike sync")
                    }
                    
                    override fun onError(error: String) {
                        Log.e("StepCountWorker", "Failed to sync bike data to Supabase: $error")
                    }
                })
                
            } catch (e: Exception) {
                Log.e("StepCountWorker", "Error during Supabase bike sync: ${e.message}", e)
            }
        }
    }

    /**
     * Synchronizes swim data to Supabase using the SupabaseUserManager.
     * 
     * @param userUid User's UID from Supabase Auth
     * @param swimDataReports List of SwimDataReport objects to sync
     */
    private suspend fun syncSwimToSupabase(userUid: String, swimDataReports: List<SwimDataReport>) {
        return withContext(Dispatchers.Main) {
            try {
                Log.d("StepCountWorker", "Starting Supabase swim sync for ${swimDataReports.size} days")
                
                supabaseUserManager.syncSwimData(userUid, swimDataReports, object : SupabaseUserManager.DatabaseCallback<Int> {
                    override fun onSuccess(result: Int) {
                        Log.d("StepCountWorker", "Successfully synced $result new swim records to Supabase")
                        // Set flag to refresh token data after sync
                        val prefs = applicationContext.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                        prefs.edit().putBoolean("token_data_needs_refresh", true).apply()
                        Log.d("StepCountWorker", "Token data refresh flag set after swim sync")
                    }
                    
                    override fun onError(error: String) {
                        Log.e("StepCountWorker", "Failed to sync swim data to Supabase: $error")
                    }
                })
                
            } catch (e: Exception) {
                Log.e("StepCountWorker", "Error during Supabase swim sync: ${e.message}", e)
            }
        }
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
}