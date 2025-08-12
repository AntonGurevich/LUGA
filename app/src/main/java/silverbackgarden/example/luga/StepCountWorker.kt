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

class StepCountWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork(): Result = runBlocking {
        try {
            val fitnessOptions = FitnessOptions.builder()
                .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                .build()
                
            val account = GoogleSignIn.getAccountForExtension(applicationContext, fitnessOptions)
            val email = getEmailFromPrefs()
            if (email.isNullOrEmpty()) {
                Log.e("StepCountWorker", "Email is null or empty")
                return@runBlocking Result.failure()
            }
            
            // Use async/await for better performance
            val stepReports = (1..3).map { dayOffset ->
                async { getStepCountForDay(dayOffset, account) }
            }.awaitAll().filterNotNull()
            
            if (stepReports.isNotEmpty()) {
                registerStepsApi(stepReports)
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e("StepCountWorker", "Work failed", e)
            Result.retry()
        }
    }
    
    private fun getEmailFromPrefs(): String? {
        val sharedPref = applicationContext.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        return sharedPref.getString("email", null)
    }
    
    private suspend fun getStepCountForDay(dayOffset: Int, account: GoogleSignInAccount): StepReport? {
        return withContext(Dispatchers.IO) {
            try {
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
                
                val readRequest = DataReadRequest.Builder()
                    .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                    .read(DataType.TYPE_STEP_COUNT_DELTA)
                    .build()
                
                val response = Fitness.getHistoryClient(applicationContext, account)
                    .readData(readRequest)
                    .await()
                
                val totalSteps = response.getDataSet(DataType.TYPE_STEP_COUNT_DELTA)
                    .dataPoints.sumOf { it.getValue(Field.FIELD_STEPS).asInt() }
                    
                StepReport(getEmailFromPrefs() ?: "", totalSteps, getDateString(-dayOffset))
            } catch (e: Exception) {
                Log.e("StepCountWorker", "Failed to get steps for day $dayOffset", e)
                null
            }
        }
    }

    private suspend fun registerStepsApi(stepReports: List<StepReport>) {
        return withContext(Dispatchers.IO) {
            try {
                val retrofit = Retrofit.Builder()
                    .baseUrl("http://20.0.164.108:3000/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                val api = retrofit.create(StepApi::class.java)

                Log.d("StepCountWorker", "Sending StepReports to API: $stepReports")

                stepReports.forEach { stepReport ->
                    Log.d("StepCountWorker", "Sending single StepReport to API: $stepReport")
                    var success = false
                    repeat(3) { attempt -> // Reduced from 10 to 3 attempts
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
                        if (!success && attempt < 2) {
                            delay(5000) // Reduced delay from 60s to 5s
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("StepCountWorker", "Failed to register steps", e)
            }
        }
    }

    private fun getDateString(daysAgo: Int): String {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, daysAgo)
        }
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
    }

    interface StepApi {
        @POST("/api/record_steps")
        suspend fun registerSteps(@Body stepReports: StepReport): retrofit2.Response<Void>
    }

    data class StepReport(val email: String, val steps_per_day: Int, val date: String)
}