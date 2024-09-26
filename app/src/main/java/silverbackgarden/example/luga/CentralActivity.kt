package silverbackgarden.example.luga

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.widget.TextView
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.mikhaellopez.circularprogressbar.CircularProgressBar
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager

class CentralActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var profileButton: Button
    private lateinit var screenshotButton: Button
    private lateinit var videoButton: Button

    private lateinit var stepsProgBar: CircularProgressBar
    private lateinit var eTokenProgBar: CircularProgressBar
    private lateinit var neTokenProgBar: CircularProgressBar
    private lateinit var tSteps: TextView
    private lateinit var tEToken: TextView
    private lateinit var tNEToken: TextView

    private lateinit var sensorManager: SensorManager
    private var stepCountSensor: Sensor? = null

    private lateinit var fitnessOptions: FitnessOptions

    val REQUEST_PERMISSIONS_REQUEST_CODE = 1001
    private val REQUEST_ACTIVITY_RECOGNITION_PERMISSION = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_central_test)

        stepsProgBar = findViewById(R.id.circularProgressBarSteps)
        eTokenProgBar = findViewById(R.id.circularProgressBarExTokens)
        neTokenProgBar = findViewById(R.id.circularProgressBarNonExTokens)
        tSteps = findViewById(R.id.tvStepsBal)
        tEToken = findViewById(R.id.tvExTokensBal)
        tNEToken = findViewById(R.id.tvNonExTokensBal)

        stepsProgBar.setOnClickListener{
            val intent = Intent(this, StepDataViewActivity::class.java)
            startActivity(intent)
        }

        eTokenProgBar.setOnClickListener{
            Toast.makeText(this, "Detailed Exchangeable Tokens Data capability is not supported in MVP yet", Toast.LENGTH_SHORT).show()
        }

        neTokenProgBar.setOnClickListener{
            Toast.makeText(this, "Detailed non-Exchangeable Tokens Data capability is not supported in MVP yet", Toast.LENGTH_SHORT).show()
        }

        // Get the system's sensor service
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Check if the step counter sensor is available
        stepCountSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        if (stepCountSensor == null) {
            Toast.makeText(this, "Step counter sensor is not available", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "ALL GOOD", Toast.LENGTH_SHORT).show()
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
        //screenshotButton = findViewById(R.id.screenshotButton)
        //videoButton = findViewById(R.id.videoRecognitionButton)

        profileButton.setBackgroundColor(buttonColor)
//        screenshotButton.setBackgroundColor(buttonColor)
//        videoButton.setBackgroundColor(buttonColor)

        profileButton.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }

        val stepCountWorkRequest = PeriodicWorkRequestBuilder<StepCountWorker>(2, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueue(stepCountWorkRequest)
//        screenshotButton.setOnClickListener {
//            Toast.makeText(
//                this,
//                "Screenshot recognition capability is not yet available",
//                Toast.LENGTH_SHORT
//            ).show()
//        }
//        videoButton.setOnClickListener {
//            Toast.makeText(
//                this,
//                "Video capture capability is not yet available",
//                Toast.LENGTH_SHORT
//            ).show()
//        }
    }

    override fun onResume() {
        super.onResume()
        stepCountSensor?.let {
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
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            readStepCount()
        }
    }

    private fun readStepCount() {
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
                Log.e("CentralActivity", "Failed to read step count", e)
                Toast.makeText(this@CentralActivity, "Failed to read step count: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateUIWithStepCount(stepCount: Int) {
        Toast.makeText(this, "Step count: $stepCount", Toast.LENGTH_SHORT).show()

        val daylyTokenExchengeLimit: Int = 30
        var daylyTokenNonExchengeLimit = 0
        if (daylyTokenExchengeLimit < 60){
            daylyTokenNonExchengeLimit = 60 - daylyTokenExchengeLimit
        } else {
            daylyTokenNonExchengeLimit = 0
        }
        val todaySteps = stepCount % 1000
        var todayStepTokens = stepCount / 1000
        val todayTokensExchengeble = minOf(todayStepTokens, daylyTokenExchengeLimit)
        todayStepTokens = maxOf(0, todayStepTokens - todayTokensExchengeble)
        val todayTokensNotExchangeble = minOf(todayStepTokens, daylyTokenNonExchengeLimit)

        stepsProgBar.setProgressWithAnimation(todaySteps.toFloat(), 1000)
        eTokenProgBar.setProgressWithAnimation(todayTokensExchengeble.toFloat(), 1000)
        neTokenProgBar.setProgressWithAnimation(todayTokensNotExchangeble.toFloat(), 1000)
        tSteps.text = "$todaySteps/1000"
        tEToken.text = "$todayTokensExchengeble/$daylyTokenExchengeLimit"
        tNEToken.text = "$todayTokensNotExchangeble/$daylyTokenNonExchengeLimit"
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