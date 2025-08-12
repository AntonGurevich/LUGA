package silverbackgarden.example.luga

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis.XAxisPosition
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.DataReadRequest
import com.mikhaellopez.circularprogressbar.CircularProgressBar
import java.util.*
import java.util.concurrent.TimeUnit

class StepDataViewActivity : AppCompatActivity() {

    private lateinit var stepsProgBar: CircularProgressBar
    private lateinit var barChartY: BarChart
    private lateinit var barChartM: BarChart

    private lateinit var fitnessOptions: FitnessOptions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_step_data)

        stepsProgBar = findViewById(R.id.circularProgressBarSteps)
        barChartY = findViewById(R.id.barChartYear)
        barChartM = findViewById(R.id.barChartMonth)

        fitnessOptions = FitnessOptions.builder()
            .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .build()
        val account = GoogleSignIn.getAccountForExtension(this, fitnessOptions)

        if (GoogleSignIn.getLastSignedInAccount(this) != null && GoogleSignIn.hasPermissions(account, fitnessOptions)) {
            fetchAndDisplayStepData()
        } else {
            Toast.makeText(this, "Google Fit permissions are required", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchAndDisplayStepData() {
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
                val stepsByMonth = IntArray(12)
                val stepsByDay = IntArray(30)

                for (dataPoint in dataSet.dataPoints) {
                    val steps = dataPoint.getValue(Field.FIELD_STEPS).asInt()
                    val timestamp = dataPoint.getStartTime(TimeUnit.MILLISECONDS)
                    val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
                    val month = calendar.get(Calendar.MONTH)
                    val day = calendar.get(Calendar.DAY_OF_MONTH) - 1

                    if (month in 0..11) {
                        stepsByMonth[month] += steps
                    }
                    if (day in 0..29) {
                        stepsByDay[day] += steps
                    }
                }

                updateBarChart(barChartY, stepsByMonth)
                updateBarChart(barChartM, stepsByDay)
            }
            .addOnFailureListener { e ->
                Log.e("StepDataViewActivity", "Failed to read step count", e)
                Toast.makeText(this, "Failed to read step count: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateBarChart(barChart: BarChart, stepsData: IntArray) {
        val entries = stepsData.mapIndexed { index, steps -> BarEntry((index + 1).toFloat(), steps.toFloat()) }
        val dataSet = BarDataSet(entries, "Steps")
        dataSet.color = resources.getColor(R.color.luga_blue)
        dataSet.setDrawValues(false)

        val barData = BarData(dataSet)
        barChart.data = barData
        barChart.description.isEnabled = false
        barChart.legend.isEnabled = false
        barChart.xAxis.setDrawGridLines(false)
        barChart.xAxis.position = XAxisPosition.BOTTOM
        barChart.axisLeft.setDrawGridLines(false)
        barChart.axisRight.setDrawGridLines(false)
        barChart.axisLeft.setDrawLabels(false)
        barChart.axisLeft.axisMinimum = 0f
        barChart.axisRight.axisMinimum = 0f
        barChart.animateY(1000)
        barChart.invalidate()
    }
}