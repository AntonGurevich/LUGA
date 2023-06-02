package silverbackgarden.example.luga
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.TextView
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.HistoryClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.result.DataReadResponse
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.mikhaellopez.circularprogressbar.CircularProgressBar
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin. math. roundToInt

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_central)

        stepsProgBar = findViewById<CircularProgressBar>(R.id.circularProgressBarSteps)
        eTokenProgBar = findViewById<CircularProgressBar>(R.id.circularProgressBarExTokens)
        neTokenProgBar = findViewById<CircularProgressBar>(R.id.circularProgressBarNonExTokens)
        tSteps = findViewById<TextView>(R.id.tvStepsBal)
        tEToken = findViewById<TextView>(R.id.tvExTokensBal)
        tNEToken = findViewById<TextView>(R.id.tvNonExTokensBal)

        stepsProgBar.setOnClickListener{
            //TODO: Detailed Steps View
            Toast.makeText(this, "Detailed Step Data capability is not supported in MVP yet", Toast.LENGTH_SHORT).show()
        }

        eTokenProgBar.setOnClickListener{
            //TODO: Detailed Tokens View
            Toast.makeText(this, "Detailed Exchangeable Tokens Data capability is not supported in MVP yet", Toast.LENGTH_SHORT).show()
        }

        neTokenProgBar.setOnClickListener{
            //TODO: Detailed Tokens View 2
            Toast.makeText(this, "Detailed non-Exchangeable Tokens Data capability is not supported in MVP yet", Toast.LENGTH_SHORT).show()
        }

        var todaySteps: Int = 0
        var todayStepTokens: Int = 0
        var todayTokensExchengeble: Int = 0
        var todayTokensNotExchangeble: Int = 0
        val daylyTokenExchengeLimit: Int = 30
        var daylyTokenNonExchengeLimit = 0
        if (daylyTokenExchengeLimit < 60){
            daylyTokenNonExchengeLimit = 60 - daylyTokenExchengeLimit
        } else {
            daylyTokenNonExchengeLimit = 0
        }



        // Get the system's sensor service
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager



        // Check if the step counter sensor is available
        stepCountSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        if (stepCountSensor == null) {
            // Step counter sensor is not available
            //Toast.makeText(this, "Step counter sensor is not available", Toast.LENGTH_SHORT).show()
        } else {
            //Toast.makeText(this, "ALL GOOD", Toast.LENGTH_SHORT).show()
        }

        fitnessOptions = FitnessOptions.builder()
            .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .build()
        val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(this)
        if (lastSignedInAccount != null && GoogleSignIn.hasPermissions(lastSignedInAccount, fitnessOptions)) {
            // User is signed in and has granted fitness permissions



            readStepCount()
        } else {
            // User is not signed in or has not granted fitness permissions, request sign-in and permissions
            GoogleSignIn.requestPermissions(
                this,
                REQUEST_PERMISSIONS_REQUEST_CODE,
                lastSignedInAccount,
                fitnessOptions
            )
            Toast.makeText(this, "Permission requested", Toast.LENGTH_SHORT).show()
        }

        println("-------------------------------------------------------------------------------------------------------------------------------")
        println(GoogleSignIn.getLastSignedInAccount(this).toString())







        val buttonColor = ContextCompat.getColor(this, R.color.luga_blue)

        profileButton = findViewById(R.id.profileButton)
        screenshotButton = findViewById(R.id.screenshotButton)
        videoButton = findViewById(R.id.videoRecognitionButton)

        profileButton.setBackgroundColor(buttonColor)
        screenshotButton.setBackgroundColor(buttonColor)
        videoButton.setBackgroundColor(buttonColor)

        profileButton.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }
        screenshotButton.setOnClickListener {
            Toast.makeText(this, "Screenshot recognition capability is not yet available", Toast.LENGTH_SHORT).show()
        }
        videoButton.setOnClickListener {
            Toast.makeText(this, "Video capture capability is not yet available", Toast.LENGTH_SHORT).show()
        }







    }

    override fun onResume() {
        super.onResume()

        // Register the sensor listener when the activity is resumed
        stepCountSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()

        // Unregister the sensor listener when the activity is paused
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            // Get the step count value from the sensor event
        readStepCount()

            // Do something with the step count value
            //updateStepCount(stepCount)
        }
    }

    private fun readStepCount() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        val startTime = cal.timeInMillis
        val endTime = System.currentTimeMillis()
        println("==================================================================================================================================================================================")
        println("==================================================================================================================================================================================")
        println("==================================================================================================================================================================================")
        println("==================================================================================================================================================================================")
        println(startTime.toString())
        println(endTime.toString())
        val readRequest = DataReadRequest.Builder()
            .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
            .read(DataType.TYPE_STEP_COUNT_DELTA)
            .build()

        println(readRequest.toString())

        val account = GoogleSignIn.getAccountForExtension(this, fitnessOptions)

        Fitness.getHistoryClient(this, account)
            .readData(readRequest)
            .addOnSuccessListener(object : OnSuccessListener<DataReadResponse> {
                override fun onSuccess(response: DataReadResponse) {

                    println("==============================================================================================================================================================================================================================================")
                    println("==============================================================================================================================================================================================================================================")
                    println("==============================================================================================================================================================================================================================================")
                    println("==============================================================================================================================================================================================================================================")
                    println(response.toString())
                    println(response.status)
                    println(response.dataSets)
                    val dataSet = response.getDataSet(DataType.TYPE_STEP_COUNT_DELTA)
                    val totalSteps: Int = if (dataSet.isEmpty) {
                        println("EMPTY")
                        0
                    } else {
                        var total = 0
                        for (dataPoint in dataSet.dataPoints) {
                            val steps = dataPoint.getValue(Field.FIELD_STEPS).asInt()
                            //val steps = dataPoint.getValue(Field.FIELD_CALORIES).asFloat()
                            println(steps.toString())
                            total += steps
                        }
                        total
                    }
                    // Use the totalSteps value for further processing
                    updateUIWithStepCount(totalSteps)
                }
            })
            .addOnFailureListener(object : OnFailureListener {
                override fun onFailure(e: Exception) {
                    // Handle the error
                    Toast.makeText(this@CentralActivity, "Failed to read step count", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun updateUIWithStepCount(stepCount: Int) {
        // Update your UI with the step count value
        Toast.makeText(this, "Step count: $stepCount", Toast.LENGTH_SHORT).show()

        // PROGRESS BAR UPDATE


        val daylyTokenExchengeLimit: Int = 30
        var daylyTokenNonExchengeLimit = 0
        if (daylyTokenExchengeLimit < 60){
            daylyTokenNonExchengeLimit = 60 - daylyTokenExchengeLimit
        } else {
            daylyTokenNonExchengeLimit = 0
        }
        val todaySteps = stepCount.toInt() % 1000
        var todayStepTokens = stepCount.toInt() / 1000
        val todayTokensExchengeble = minOf(todayStepTokens, daylyTokenExchengeLimit)
        todayStepTokens = maxOf(0, todayStepTokens - todayTokensExchengeble)
        val todayTokensNotExchangeble = minOf(todayStepTokens, daylyTokenNonExchengeLimit)

        Toast.makeText(this, todaySteps.toString(), Toast.LENGTH_SHORT).show()
        stepsProgBar.setProgressWithAnimation(todaySteps.toFloat(), 1000)
        eTokenProgBar.setProgressWithAnimation(todayTokensExchengeble.toFloat(), 1000)
        neTokenProgBar.setProgressWithAnimation(todayTokensNotExchangeble.toFloat(), 1000)
        tSteps.setText("$todaySteps/1000")
        tEToken.setText("$todayTokensExchengeble/$daylyTokenExchengeLimit")
        tNEToken.setText("$todayTokensNotExchangeble/$daylyTokenNonExchengeLimit")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // User granted the permission
                println("Access Permited")
                readStepCount()
            } else {
                // User denied the permission
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

}
