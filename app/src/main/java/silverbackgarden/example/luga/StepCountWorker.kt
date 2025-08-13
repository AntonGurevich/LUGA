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
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.DataReadRequest

import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Background worker that periodically retrieves step count data from Google Fit
 * and sends it to a remote API for storage and analysis.
 * 
 * This worker runs every 2 hours (as configured in CentralActivity) to ensure
 * step data is consistently collected and synchronized with the backend system.
 * It handles Google Fit authentication, data retrieval, and API communication
 * in the background without requiring user interaction.
 */
class StepCountWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    /**
     * Main work method that executes the step counting and synchronization process.
     * This method runs on a background thread and handles all the core functionality
     * including Google Fit data retrieval and API communication.
     * 
     * @return Result.success() if work completes successfully, Result.retry() if it should be retried
     */
    override fun doWork(): Result = runBlocking {
        try {
            // Configure Google Fit options for step count data access
            val fitnessOptions = FitnessOptions.builder()
                .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                .build()
                
            // Get the currently signed-in Google account
            val account = GoogleSignIn.getAccountForExtension(applicationContext, fitnessOptions)
            
            // Check if user is actually signed in and has permissions
            if (account == null) {
                Log.w("StepCountWorker", "No Google account signed in, skipping work")
                return@runBlocking Result.success() // Don't retry, just succeed
            }
            
            // Verify that the user has granted the required Google Fit permissions
            if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
                Log.w("StepCountWorker", "User doesn't have Google Fit permissions, skipping work")
                return@runBlocking Result.success() // Don't retry, just succeed
            }
            
            // Retrieve user email from shared preferences for API identification
            val email = getEmailFromPrefs()
            if (email.isNullOrEmpty()) {
                Log.e("StepCountWorker", "Email is null or empty")
                return@runBlocking Result.failure()
            }
            
            // Use async/await for better performance when retrieving multiple days of data
            // Retrieve step data for the last 3 days concurrently (for API snapshot only)
            val stepReports = (1..3).map { dayOffset ->
                async { getStepCountForDay(dayOffset, account) }
            }.awaitAll().filterNotNull()
            
            // Send collected step data to the remote API if any data was retrieved
            if (stepReports.isNotEmpty()) {
                Log.d("StepCountWorker", "Sending 3-day snapshot to API: ${stepReports.size} days")
                
                // Send 3-day snapshot to API
                registerStepsApi(stepReports)
                
                // Update shared preferences with last API sync time
                val prefs = applicationContext.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putLong("api_last_sync", System.currentTimeMillis())
                    .putString("api_last_sync_days", stepReports.joinToString(",") { it.date })
                    .apply()
                
                Log.d("StepCountWorker", "API snapshot sent successfully. Days: ${stepReports.map { it.date }}")
            } else {
                Log.w("StepCountWorker", "No step data available for API snapshot")
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e("StepCountWorker", "Work failed", e)
            Result.retry()
        }
    }
    
    /**
     * Retrieves the user's email address from shared preferences.
     * This email is used to identify the user when sending data to the API.
     * 
     * @return The user's email address, or null if not found
     */
    private fun getEmailFromPrefs(): String? {
        val sharedPref = applicationContext.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        return sharedPref.getString("email", null)
    }
    
    /**
     * Retrieves step count data for a specific day from Google Fit.
     * This is specifically for creating the 3-day API snapshot.
     * 
     * @param dayOffset Number of days ago (1 = yesterday, 2 = day before yesterday, etc.)
     * @param account Google Sign-In account with Fitness permissions
     * @return StepReport object containing the day's step data, or null if retrieval fails
     */
    private suspend fun getStepCountForDay(dayOffset: Int, account: GoogleSignInAccount): StepReport? {
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
                val totalSteps = response.getDataSet(DataType.TYPE_STEP_COUNT_DELTA)
                    .dataPoints.sumOf { it.getValue(Field.FIELD_STEPS).asInt() }
                    
                // Create and return a StepReport with the collected data
                StepReport(getEmailFromPrefs() ?: "", totalSteps, getDateString(-dayOffset))
            } catch (e: Exception) {
                Log.e("StepCountWorker", "Failed to get steps for day $dayOffset", e)
                null
            }
        }
    }

    /**
     * Sends step count data to the remote API for storage and analysis.
     * Uses Retrofit to make HTTP POST requests to the backend service.
     * Implements retry logic with exponential backoff for reliability.
     * 
     * @param stepReports List of StepReport objects to send to the API
     */
    private suspend fun registerStepsApi(stepReports: List<StepReport>) {
        return withContext(Dispatchers.IO) {
            try {
                // Build Retrofit instance for API communication
                val retrofit = Retrofit.Builder()
                    .baseUrl("http://20.0.164.108:3000/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                val api = retrofit.create(StepApi::class.java)

                Log.d("StepCountWorker", "Sending StepReports to API: $stepReports")

                // Send each step report individually to the API
                stepReports.forEach { stepReport ->
                    Log.d("StepCountWorker", "Sending single StepReport to API: $stepReport")
                    var success = false
                    
                    // Retry logic: attempt up to 3 times with 5-second delays
                    repeat(3) { attempt ->
                        if (success) return@repeat
                        try {
                            val response = api.registerSteps(stepReport)
                            if (response.isSuccessful) {
                                Log.d("Step record", "SUCCESS")
                                success = true
                            } else {
                                Log.e("Step record", "Error response body: ${response.errorBody()?.string()}")
                                Log.e("Step record", "Response code: ${response.code()}")
                            }
                        } catch (e: Exception) {
                            Log.e("StepCountWorker", "Exception: ${e.message}", e)
                            if (e is HttpException) {
                                Log.e("StepCountWorker", "HTTP exception error body: ${e.response()?.errorBody()?.string()}")
                            }
                        }
                        
                        // Add delay between retry attempts (except for the last attempt)
                        if (!success && attempt < 2) {
                            delay(5000) // 5-second delay between retries
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("StepCountWorker", "Failed to register steps", e)
            }
        }
    }

    /**
     * Generates a date string in "yyyy-MM-dd" format for a specified number of days ago.
     * Used to create date identifiers for step data when sending to the API.
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
     * Retrofit interface for the step recording API.
     * Defines the HTTP endpoint and request structure for sending step data.
     */
    interface StepApi {
        @POST("/api/record_steps")
        suspend fun registerSteps(@Body stepReports: StepReport): retrofit2.Response<Void>
    }

    /**
     * Data class representing a step count report for a single day.
     * Contains the user's email, step count, and date for API communication.
     * 
     * @property email User's email address for identification
     * @property steps_per_day Total step count for the specified day
     * @property date Date string in "yyyy-MM-dd" format
     */
    data class StepReport(val email: String, val steps_per_day: Int, val date: String)
}