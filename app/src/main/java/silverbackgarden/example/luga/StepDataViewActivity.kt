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

/**
 * Activity for displaying detailed step count data visualization.
 * 
 * This activity provides users with comprehensive views of their step data
 * including monthly and daily breakdowns. It uses bar charts to visualize
 * step patterns over time and integrates with Google Fit to retrieve
 * historical fitness data.
 * 
 * The activity displays:
 * - A circular progress bar showing current daily progress
 * - A yearly bar chart showing monthly step totals
 * - A monthly bar chart showing daily step counts
 */
class StepDataViewActivity : AppCompatActivity() {

    // UI Elements for data visualization
    private lateinit var stepsProgBar: CircularProgressBar  // Daily step progress indicator
    private lateinit var barChartY: BarChart               // Yearly step data chart
    private lateinit var barChartM: BarChart               // Monthly step data chart

    // Google Fit configuration for data access
    private lateinit var fitnessOptions: FitnessOptions

    /**
     * Called when the activity is first created.
     * Initializes the UI elements, sets up Google Fit integration,
     * and fetches step data if permissions are available.
     * 
     * @param savedInstanceState Bundle containing the activity's previously saved state
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_step_data)

        // Initialize UI elements from the layout
        stepsProgBar = findViewById(R.id.circularProgressBarSteps)
        barChartY = findViewById(R.id.barChartYear)
        barChartM = findViewById(R.id.barChartMonth)

        // Configure Google Fit options for step count data access
        fitnessOptions = FitnessOptions.builder()
            .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .build()
        
        // Get the currently signed-in Google account
        val account = GoogleSignIn.getAccountForExtension(this, fitnessOptions)

        // Check if user has Google Fit permissions before proceeding
        if (GoogleSignIn.getLastSignedInAccount(this) != null && GoogleSignIn.hasPermissions(account, fitnessOptions)) {
            fetchAndDisplayStepData()
        } else {
            Toast.makeText(this, "Google Fit permissions are required", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Fetches step count data from Google Fit and displays it in charts.
     * 
     * This method retrieves step data for the current month and processes it
     * to create both monthly and daily breakdowns. It then updates the UI
     * with the processed data using bar charts.
     */
    private fun fetchAndDisplayStepData() {
        // Calculate time range for data retrieval (current month)
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)      // Start from first day of month
        cal.set(Calendar.HOUR_OF_DAY, 0)       // Start at beginning of day
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        val startTime = cal.timeInMillis
        val endTime = System.currentTimeMillis() // End at current time

        // Build the data read request for step count data
        val readRequest = DataReadRequest.Builder()
            .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
            .read(DataType.TYPE_STEP_COUNT_DELTA)
            .build()

        // Get the Google account for API calls
        val account = GoogleSignIn.getAccountForExtension(this, fitnessOptions)

        // Execute the read request and process the response
        Fitness.getHistoryClient(this, account)
            .readData(readRequest)
            .addOnSuccessListener { response ->
                val dataSet = response.getDataSet(DataType.TYPE_STEP_COUNT_DELTA)
                
                // Arrays to store aggregated step data
                val stepsByMonth = IntArray(12)  // Monthly totals (Jan-Dec)
                val stepsByDay = IntArray(30)    // Daily totals (1-30)

                // Process each data point from Google Fit
                for (dataPoint in dataSet.dataPoints) {
                    val steps = dataPoint.getValue(Field.FIELD_STEPS).asInt()
                    val timestamp = dataPoint.getStartTime(TimeUnit.MILLISECONDS)
                    
                    // Convert timestamp to calendar for date extraction
                    val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
                    val month = calendar.get(Calendar.MONTH)           // 0-11 (Jan-Dec)
                    val day = calendar.get(Calendar.DAY_OF_MONTH) - 1 // 0-29 (1-30)

                    // Aggregate steps by month (ensure valid month index)
                    if (month in 0..11) {
                        stepsByMonth[month] += steps
                    }
                    
                    // Aggregate steps by day (ensure valid day index)
                    if (day in 0..29) {
                        stepsByDay[day] += steps
                    }
                }

                // Update both charts with the processed data
                updateBarChart(barChartY, stepsByMonth)
                updateBarChart(barChartM, stepsByDay)
            }
            .addOnFailureListener { e ->
                // Handle errors gracefully with user feedback
                Log.e("StepDataViewActivity", "Failed to read step count", e)
                Toast.makeText(this, "Failed to read step count: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Updates a bar chart with step count data.
     * 
     * This method configures the chart appearance and populates it with
     * the provided step data. It sets up visual styling, animations,
     * and data representation for optimal user experience.
     * 
     * @param barChart The bar chart to update
     * @param stepsData Array of step counts to display in the chart
     */
    private fun updateBarChart(barChart: BarChart, stepsData: IntArray) {
        // Convert step data to chart entries with proper indexing
        val entries = stepsData.mapIndexed { index, steps -> 
            BarEntry((index + 1).toFloat(), steps.toFloat()) 
        }
        
        // Create dataset with custom styling
        val dataSet = BarDataSet(entries, "Steps")
        dataSet.color = resources.getColor(R.color.luga_blue)  // Use app theme color
        dataSet.setDrawValues(false)  // Hide value labels for cleaner appearance

        // Configure chart data and appearance
        val barData = BarData(dataSet)
        barChart.data = barData
        
        // Disable chart description and legend for cleaner UI
        barChart.description.isEnabled = false
        barChart.legend.isEnabled = false
        
        // Configure X-axis (horizontal)
        barChart.xAxis.setDrawGridLines(false)  // Remove vertical grid lines
        barChart.xAxis.position = XAxisPosition.BOTTOM  // Position labels at bottom
        
        // Configure Y-axis (vertical) - both left and right
        barChart.axisLeft.setDrawGridLines(false)   // Remove horizontal grid lines
        barChart.axisRight.setDrawGridLines(false)  // Remove horizontal grid lines
        barChart.axisLeft.setDrawLabels(false)      // Hide left axis labels
        barChart.axisLeft.axisMinimum = 0f          // Start Y-axis at 0
        barChart.axisRight.axisMinimum = 0f         // Start Y-axis at 0
        
        // Add smooth animation for better user experience
        barChart.animateY(1000)  // 1-second animation duration
        
        // Refresh the chart to display changes
        barChart.invalidate()
    }
}