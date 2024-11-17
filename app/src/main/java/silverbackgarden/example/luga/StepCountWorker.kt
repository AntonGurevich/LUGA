package silverbackgarden.example.luga

import android.content.Context
import android.icu.text.SimpleDateFormat
import android.util.Log
import android.widget.Toast
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import silverbackgarden.example.luga.RegisterActivity.ErrorResponse
import silverbackgarden.example.luga.RegisterActivity.User
import silverbackgarden.example.luga.RegisterActivity.UserApi
import java.util.*
import java.util.concurrent.TimeUnit

class StepCountWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val fitnessOptions = FitnessOptions.builder()
            .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .build()
        val account = GoogleSignIn.getAccountForExtension(applicationContext, fitnessOptions)

        val sharedPref by lazy {
            (applicationContext as? Acteamity)?.sharedPref
        }
        val email = sharedPref?.getString("email", "no value")

        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1) // Set to yesterday
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
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
                val dataSet = response.getDataSet(DataType.TYPE_STEP_COUNT_DELTA)
                val totalSteps: Int = if (dataSet.isEmpty) {
                    0
                } else {
                    var total = 0
                    for (dataPoint in dataSet.dataPoints) {
                        val steps = dataPoint.getValue(Field.FIELD_STEPS).asInt()
                        total += steps
                    }
                    total
                }
                Log.d("StepCountWorker", "Total steps: $totalSteps")
                val reportDate = getYesterdayDate()
                if (email != null) {
                    registerStepsApi(email, totalSteps, reportDate)
                }
            }
            .addOnFailureListener { e ->
                Log.e("StepCountWorker", "Failed to read step count", e)
            }
        return Result.success()
    }


    private fun registerStepsApi(email: String, dailySteps: Int, reportDate: String) {


        val retrofit = Retrofit.Builder()
            .baseUrl("http://20.0.164.108:3000/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(StepApi::class.java)
        val stepReport = StepReport(email, dailySteps, reportDate)

        CoroutineScope(Dispatchers.IO).launch {
            var attempt = 0
            val maxAttempts = 10
            var success = false
            while (attempt < maxAttempts && !success) {
                try {
                    val response = api.registerSteps(stepReport)
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            success = true
                            Log.e("Step record", "SUCCESS")
                        } else {
                            val errorBody = response.errorBody()?.string()
                            Log.e("Step record", "Error response body: $errorBody")
                            val errorResponse = errorBody?.let {
                                Gson().fromJson(it, ErrorResponse::class.java)
                            }
                            val errorMessage = errorResponse?.error ?: "Step record failed"
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Log.e("RegisterActivity", "Exception: ${e.message}", e)
                        val errorMessage = when (e) {
                            is HttpException -> {
                                val errorBody = e.response()?.errorBody()?.string()
                                Log.e("RegisterActivity", "HTTP exception error body: $errorBody")
                                val errorResponse = errorBody?.let {
                                    Gson().fromJson(it, ErrorResponse::class.java)
                                }
                                errorResponse?.error ?: e.message()
                            }

                            else -> e.message ?: "Unknown error"
                        }
                    }
                }
                attempt++
                if (!success) {
                    delay(1000) // Wait for 1 second before retrying
                }
            }
        }
    }



    private fun getCurrentDate(): String {
        val calendar = android.icu.util.Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(calendar.time)
    }

    interface StepApi {
        @POST("/api/record_steps")
        suspend fun registerSteps(@Body user: StepReport): retrofit2.Response<Void>
    }
    data class StepReport(
        val email: String,
        val steps_per_day: Int,
        val date: String
    )


    fun getYesterdayDate(): String {
        val calendar = android.icu.util.Calendar.getInstance()
        calendar.add(android.icu.util.Calendar.DAY_OF_YEAR, -1)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(calendar.time)
    }
}

