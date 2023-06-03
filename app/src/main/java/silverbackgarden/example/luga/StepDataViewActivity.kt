package silverbackgarden.example.luga
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.mikhaellopez.circularprogressbar.CircularProgressBar

class StepDataViewActivity : AppCompatActivity() {

    private lateinit var stepsProgBar: CircularProgressBar
    private lateinit var barChart: BarChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_step_data)

        stepsProgBar = findViewById(R.id.circularProgressBarSteps)
        barChart = findViewById(R.id.barChart)

        stepsProgBar.setOnClickListener {
            // Handle progress bar click if needed
            Toast.makeText(this, "Progress bar clicked", Toast.LENGTH_SHORT).show()
        }

        // Populate the bar chart with mock data for the last 12 months
        populateBarChart()

        // Set a listener for bar chart value selection
        barChart.getAxisLeft().setAxisMinValue(0f)
        barChart.getAxisRight().setAxisMinValue(0f)
        barChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
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

    private fun populateBarChart() {
        val entries = mutableListOf<BarEntry>()

        // Generate mock data for the last 12 months
        for (month in 0 until 12) {
            val steps = (200000..450000).random().toFloat() // Replace with actual steps data
            //TODO: Get real Data
            entries.add(BarEntry(month.toFloat(), steps))
        }

        // Create a bar dataset with the entries
        val dataSet = BarDataSet(entries, "Steps")
        dataSet.color = resources.getColor(R.color.luga_blue)

        // Create a bar data object with the dataset
        val barData = BarData(dataSet)

        // Customize the appearance of the bar chart
        barChart.data = barData
        barChart.description.isEnabled = false
        barChart.legend.isEnabled = false
        barChart.xAxis.setDrawGridLines(false)
        barChart.axisLeft.setDrawGridLines(false)
        barChart.axisRight.setDrawGridLines(false)
        barChart.animateY(1000)

        // Refresh the chart
        barChart.invalidate()
    }

    //private fun launchDetailedView(month: Int) {
        // Launch the detailed view activity for the selected month
      //  val intent = Intent(this, StepDataByDayActivity::class.java)
      //  intent.putExtra("selectedMonth", month)
      //  startActivity(intent)
    //}
}
