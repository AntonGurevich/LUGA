package silverbackgarden.example.luga

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager

import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.DataReadRequest
import com.mikhaellopez.circularprogressbar.CircularProgressBar
import java.util.*
import java.util.concurrent.TimeUnit

import silverbackgarden.example.luga.TokenCalculation

class CentralActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var profileButton: Button

    private lateinit var activateStepCountWorkerButton: Button

    private lateinit var stepsProgBar: CircularProgressBar
    private lateinit var eTokenProgBar: CircularProgressBar
    private lateinit var neTokenProgBar: CircularProgressBar
    private lateinit var tSteps: TextView
    private lateinit var tEToken: TextView
    private lateinit var tNEToken: TextView

    private lateinit var sensorManager: SensorManager
    private var stepCountSensor: Sensor? = null
    private var stepDetectorSensor: Sensor? = null
    private var initialStepCount: Int = -1
    private var currentStepCount: Int = 0

    private lateinit var fitnessOptions: FitnessOptions

    companion object {
        private const val REQUEST_PERMISSIONS_REQUEST_CODE = 1001
        private const val REQUEST_ACTIVITY_RECOGNITION_PERMISSION = 1002
        private const val MONTHLY_TOKEN_EXCHANGE_LIMIT = 30
        private const val DAILY_STEP_GOAL = 10000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_central_test)
    // Initialize the button
        activateStepCountWorkerButton = findViewById(R.id.activateStepCountWorkerButton)

        // Set OnClickListener to the button
        activateStepCountWorkerButton.setOnClickListener {
            val stepCountWorkRequest = PeriodicWorkRequestBuilder<StepCountWorker>(2, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(this).enqueue(stepCountWorkRequest)
            Toast.makeText(this, "Step Count Worker activated", Toast.LENGTH_SHORT).show()
        }
        stepsProgBar = findViewById(R.id.circularProgressBarSteps)
        eTokenProgBar = findViewById(R.id.circularProgressBarExTokens)
        neTokenProgBar = findViewById(R.id.circularProgressBarNonExTokens)
        tSteps = findViewById(R.id.tvStepsBal)
        tEToken = findViewById(R.id.tvExTokensBal)
        tNEToken = findViewById(R.id.tvNonExTokensBal)

        stepsProgBar.setOnClickListener {
            val intent = Intent(this, StepDataViewActivity::class.java)
            startActivity(intent)
        }

        eTokenProgBar.setOnClickListener {
            Toast.makeText(this, "Detailed Exchangeable Tokens Data capability is not supported in MVP yet", Toast.LENGTH_SHORT).show()
        }

        neTokenProgBar.setOnClickListener {
            Toast.makeText(this, "Detailed non-Exchangeable Tokens Data capability is not supported in MVP yet", Toast.LENGTH_SHORT).show()
        }
        // Get the system's sensor service
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Check if the step counter sensor is available
        stepCountSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        if (stepCountSensor == null && stepDetectorSensor == null) {
            Toast.makeText(this, "No step sensors available on this device", Toast.LENGTH_SHORT).show()
        } else {
            if (stepCountSensor != null) {
                Toast.makeText(this, "Step counter sensor available", Toast.LENGTH_SHORT).show()
            }
            if (stepDetectorSensor != null) {
                Toast.makeText(this, "Step detector sensor available", Toast.LENGTH_SHORT).show()
            }
        }

        fitnessOptions = FitnessOptions.builder()
            .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .build()
        val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(this)
        if (lastSignedInAccount != null && GoogleSignIn.hasPermissions(lastSignedInAccount, fitnessOptions)) {
            readStepCount()
        } else {
            GoogleSignIn.requestPermissions(
                this,
                REQUEST_PERMISSIONS_REQUEST_CODE,
                lastSignedInAccount,
                fitnessOptions
            )
            Toast.makeText(this, "Permission requested", Toast.LENGTH_SHORT).show()
        }

        // Check and request ACTIVITY_RECOGNITION permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                REQUEST_ACTIVITY_RECOGNITION_PERMISSION
            )
        } else {
            // Permission already granted, proceed with reading step count
            readStepCount()
        }

        val buttonColor = ContextCompat.getColor(this, R.color.luga_blue)

        profileButton = findViewById(R.id.profileButton)
        profileButton.setBackgroundColor(buttonColor)

        profileButton.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }
        
        // Add a button to reset step counter (for testing)
        activateStepCountWorkerButton.setOnClickListener {
            // Reset step counter for new day
            resetStepCounter()
            val stepCountWorkRequest = PeriodicWorkRequestBuilder<StepCountWorker>(2, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(this).enqueue(stepCountWorkRequest)
            Toast.makeText(this, "Step Count Worker activated & counter reset", Toast.LENGTH_SHORT).show()
        }

        val stepCountWorkRequest = PeriodicWorkRequestBuilder<StepCountWorker>(2, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueue(stepCountWorkRequest)

    }

    override fun onResume() {
        super.onResume()
        stepCountSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        stepDetectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                handleStepCounterEvent(event)
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                handleStepDetectorEvent(event)
            }
        }
    }
    
    private fun handleStepCounterEvent(event: SensorEvent) {
        val totalSteps = event.values[0].toInt()
        
        if (initialStepCount == -1) {
            initialStepCount = totalSteps
        }
        
        currentStepCount = totalSteps - initialStepCount
        updateUIWithStepCount(currentStepCount)
    }
    
    private fun handleStepDetectorEvent(event: SensorEvent) {
        // Step detector gives 1.0 for each step
        if (event.values[0] == 1.0f) {
            currentStepCount++
            updateUIWithStepCount(currentStepCount)
        }
    }

    private fun readStepCount() {
        // Try device sensor first, fallback to Google Fit
        if (currentStepCount > 0) {
            updateUIWithStepCount(currentStepCount)
            return
        }
        
        // Fallback to Google Fit if device sensor not available
        readStepCountFromGoogleFit()
    }
    
    private fun readStepCountFromGoogleFit() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        val startTime = cal.timeInMillis
        val endTime = System.currentTimeMillis()

        val readRequest = DataReadRequest.Builder()
            .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
            .read(DataType.TYPE_STEP_COUNT_DELTA)
            .build()

        val account = GoogleSignIn.getAccountForExtension(this, fitnessOptions)

        Fitness.getHistoryClient(this, account)
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
                updateUIWithStepCount(totalSteps)
            }
            .addOnFailureListener { e ->
                Log.e("CentralActivity", "Failed to read step count from Google Fit", e)
                Toast.makeText(this@CentralActivity, "Failed to read step count: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun resetStepCounter() {
        initialStepCount = -1
        currentStepCount = 0
        updateUIWithStepCount(0)
        Toast.makeText(this, "Step counter reset", Toast.LENGTH_SHORT).show()
    }
    
    private fun getStepDataSource(): String {
        return when {
            stepCountSensor != null -> "Device Step Counter"
            stepDetectorSensor != null -> "Device Step Detector"
            else -> "Google Fit (Fallback)"
        }
    }

    private fun calculateTokens(stepCount: Int): TokenCalculation {
        val todaySteps = stepCount % DAILY_STEP_GOAL
        val todayStepTokens = stepCount / DAILY_STEP_GOAL
        val exchangeableTokens = minOf(todayStepTokens, MONTHLY_TOKEN_EXCHANGE_LIMIT)
        val remainingTokens = maxOf(0, todayStepTokens - exchangeableTokens)
        val nonExchangeableTokens = minOf(remainingTokens, 60 - MONTHLY_TOKEN_EXCHANGE_LIMIT)
        
        return TokenCalculation(
            steps = todaySteps,
            exchangeableTokens = exchangeableTokens,
            nonExchangeableTokens = nonExchangeableTokens,
            monthlyExchangeLimit = MONTHLY_TOKEN_EXCHANGE_LIMIT,
            dailyStepGoal = DAILY_STEP_GOAL
        )
    }

    private fun updateUIWithStepCount(stepCount: Int) {
        val dataSource = getStepDataSource()
        Toast.makeText(this, "Steps: $stepCount (via $dataSource)", Toast.LENGTH_SHORT).show()

        val tokenCalculation = calculateTokens(stepCount)

        stepsProgBar.progressMax = DAILY_STEP_GOAL.toFloat()
        eTokenProgBar.progressMax = MONTHLY_TOKEN_EXCHANGE_LIMIT.toFloat()
        neTokenProgBar.progressMax = (60 - MONTHLY_TOKEN_EXCHANGE_LIMIT).toFloat()
        
        stepsProgBar.setProgressWithAnimation(tokenCalculation.steps.toFloat(), 1000)
        eTokenProgBar.setProgressWithAnimation(tokenCalculation.exchangeableTokens.toFloat(), 1000)
        neTokenProgBar.setProgressWithAnimation(tokenCalculation.nonExchangeableTokens.toFloat(), 1000)
        
        tSteps.text = "${tokenCalculation.steps}/$DAILY_STEP_GOAL"
        tEToken.text = "${tokenCalculation.exchangeableTokens}/$MONTHLY_TOKEN_EXCHANGE_LIMIT"
        tNEToken.text = "${tokenCalculation.nonExchangeableTokens}/${60 - MONTHLY_TOKEN_EXCHANGE_LIMIT}"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_ACTIVITY_RECOGNITION_PERMISSION) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // Permission granted, proceed with reading step count
                readStepCount()
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                readStepCount()
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

