package silverbackgarden.example.luga
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis.XAxisPosition
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.mikhaellopez.circularprogressbar.CircularProgressBar

class StepDataViewActivity : AppCompatActivity() {

    private lateinit var stepsProgBar: CircularProgressBar
    private lateinit var barChartY: BarChart
    private lateinit var barChartM: BarChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_step_data)

        stepsProgBar = findViewById(R.id.circularProgressBarSteps)
        barChartY = findViewById(R.id.barChartYear)
        barChartM = findViewById(R.id.barChartMonth)

        stepsProgBar.setOnClickListener {
            // Handle progress bar click if needed
            Toast.makeText(this, "Progress bar clicked", Toast.LENGTH_SHORT).show()
        }

        // Populate the bar chart with mock data for the last 12 months
        populateBarChartY()
        populateBarChartM()

        // Set a listener for bar chart value selection
        barChartY.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                // Handle bar chart value selection, launch detailed view for the selected month
                val selectedMonth = e?.x?.toInt() ?: 0
                //launchDetailedView(selectedMonth)
                //TODO: Detailed steps View
            }

            override fun onNothingSelected() {
                // Handle when no value is selected
            }
        })
    }

    private fun populateBarChartY() {
        val entries = mutableListOf<BarEntry>()

        // Generate mock data for the last 12 months
        for (month in 0 until 12) {
            val steps = (200000..450000).random().toFloat() // Replace with actual steps data
            //TODO: Get real Data
            entries.add(BarEntry((month+1).toFloat(), steps))
        }

        // Create a bar dataset with the entries
        val dataSet = BarDataSet(entries, "Steps")
        dataSet.color = resources.getColor(R.color.luga_blue)

        // Create a bar data object with the dataset
        val barData = BarData(dataSet)

        // Customize the appearance of the bar chart
        barChartY.data = barData
        barChartY.description.isEnabled = false
        barChartY.legend.isEnabled = false
        barChartY.xAxis.setDrawGridLines(false)
        barChartY.xAxis.position = XAxisPosition.BOTTOM
        barChartY.axisLeft.setDrawGridLines(false)
        barChartY.axisRight.setDrawGridLines(false)
        barChartY.axisLeft.setDrawGridLines(false)
        barChartY.axisLeft.setDrawLabels(false)
        barChartY.axisLeft.axisMinimum = 0f
        barChartY.axisRight.axisMinimum = 0f
        barChartY.animateY(1000)
        barChartY.xAxis.setLabelCount(entries.size, false)

        // Refresh the chart
        barChartY.invalidate()
    }private fun populateBarChartM() {
        val entries = mutableListOf<BarEntry>()

        // Generate mock data for the last 12 months
        for (month in 0 until 30) {
            val steps = (5000..20000).random().toFloat() // Replace with actual steps data
            //TODO: Get real Data
            entries.add(BarEntry((month+1).toFloat(), steps))
        }

        // Create a bar dataset with the entries
        val dataSet = BarDataSet(entries, "Steps")
        dataSet.color = resources.getColor(R.color.luga_blue)
        dataSet.setDrawValues(false)

        // Create a bar data object with the dataset
        val barData = BarData(dataSet)

        // Customize the appearance of the bar chart
        barChartM.data = barData
        barChartM.description.isEnabled = false
        barChartM.legend.isEnabled = false
        barChartM.xAxis.setDrawGridLines(false)
        barChartM.xAxis.position = XAxisPosition.BOTTOM
        barChartM.axisLeft.setDrawGridLines(false)
        barChartM.axisRight.setDrawGridLines(false)
        barChartM.axisLeft.setDrawGridLines(false)
        barChartM.axisLeft.setDrawLabels(false)
        barChartM.axisLeft.axisMinimum = 0f
        barChartM.axisRight.axisMinimum = 0f
        barChartM.animateY(1000)

        // Refresh the chart
        barChartY.invalidate()
    }

    //private fun launchDetailedView(month: Int) {
        // Launch the detailed view activity for the selected month
      //  val intent = Intent(this, StepDataByDayActivity::class.java)
      //  intent.putExtra("selectedMonth", month)
      //  startActivity(intent)
    //}
}
