package silverbackgarden.example.luga

import android.content.Context
import android.icu.text.SimpleDateFormat
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.DataReadRequest
import kotlinx.coroutines.*
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.*
import java.util.concurrent.TimeUnit

class StepCountWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val fitnessOptions = FitnessOptions.builder()
            .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .build()
        val account = GoogleSignIn.getAccountForExtension(applicationContext, fitnessOptions)
        val sharedPref = applicationContext.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val email = sharedPref.getString("email", null)

        if (email.isNullOrEmpty()) {
            Log.e("StepCountWorker", "Email is null or empty")
            return Result.failure()
        }

        val stepReports = mutableListOf<StepReport>()
        for (i in 1..3) {
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -i)
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

            Fitness.getHistoryClient(applicationContext, account)
                .readData(readRequest)
                .addOnSuccessListener { response ->
                    val totalSteps = response.getDataSet(DataType.TYPE_STEP_COUNT_DELTA)
                        .dataPoints.sumOf { it.getValue(Field.FIELD_STEPS).asInt() }
                    Log.d("StepCountWorker", "Total steps for day $i: $totalSteps")
                    val stepReport = StepReport(email, totalSteps, getDateString(-i))
                    stepReports.add(stepReport)
                    Log.d("StepCountWorker", "Created StepReport: $stepReport")
                    if (stepReports.size == 3) {
                        registerStepsApi(stepReports)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("StepCountWorker", "Failed to read step count for day $i", e)
                }
        }
        return Result.success()
    }

    private fun registerStepsApi(stepReports: List<StepReport>) {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://20.0.164.108:3000/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val api = retrofit.create(StepApi::class.java)

        Log.d("StepCountWorker", "Sending StepReports to API: $stepReports")

        CoroutineScope(Dispatchers.IO).launch {
            stepReports.forEach { stepReport ->
                Log.d("StepCountWorker", "Sending single StepReport to API: $stepReport")
                repeat(10) { attempt ->
                    try {
                        val response = api.registerSteps(stepReport)
                        withContext(Dispatchers.Main) {
                            if (response.isSuccessful) {
                                Log.e("Step record", "SUCCESS")
                                return@withContext
                            } else {
                                Log.e("Step record", "Error response body: ${response.errorBody()?.string()}")
                                Log.e("Step record", "Response code: ${response.code()}")
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Log.e("RegisterActivity", "Exception: ${e.message}", e)
                            if (e is HttpException) {
                                Log.e("RegisterActivity", "HTTP exception error body: ${e.response()?.errorBody()?.string()}")
                            }
                        }
                    }
                    delay(60000)
                }
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