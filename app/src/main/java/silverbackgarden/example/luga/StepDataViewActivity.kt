package silverbackgarden.example.luga

import androidx.core.content.ContextCompat
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis.XAxisPosition
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Activity for displaying activity data: steps, cycle, swim, or tokens.
 * Data is from Supabase (raw_steps, raw_bike, raw_swim, token_record2).
 * - Steps/Cycle/Swim: last 12 months (rolling) + last 30 days (rolling).
 * - Tokens: last 12 months only (from token_record2).
 */
class StepDataViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DATA_TYPE = "data_type"
        const val TYPE_STEPS = "steps"
        const val TYPE_CYCLE = "cycle"
        const val TYPE_SWIM = "swim"
        const val TYPE_TOKENS = "tokens"
    }

    private lateinit var barChartY: BarChart
    private lateinit var barChartM: BarChart
    private lateinit var titleLast12Months: TextView
    private lateinit var sectionLast30Days: View
    private lateinit var titleLast30Days: TextView

    private val supabaseUserManager = SupabaseUserManager()
    private var dataType: String = TYPE_STEPS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_step_data)

        dataType = intent.getStringExtra(EXTRA_DATA_TYPE) ?: TYPE_STEPS
        title = when (dataType) {
            TYPE_CYCLE -> "Cycle Data View"
            TYPE_SWIM -> "Swim Data View"
            TYPE_TOKENS -> "Token Data View"
            else -> "Step Data View"
        }

        barChartY = findViewById(R.id.barChartYear)
        barChartM = findViewById(R.id.barChartMonth)
        titleLast12Months = findViewById(R.id.titleLast12Months)
        sectionLast30Days = findViewById(R.id.sectionLast30Days)
        titleLast30Days = findViewById(R.id.titleLast30Days)

        when (dataType) {
            TYPE_TOKENS -> {
                sectionLast30Days.visibility = View.GONE
                titleLast12Months.text = "Your tokens in last 12 months"
            }
            TYPE_CYCLE -> {
                titleLast12Months.text = "Your cycle (m) in last 12 months"
                titleLast30Days.text = "Your cycle (m) in last 30 days"
            }
            TYPE_SWIM -> {
                titleLast12Months.text = "Your swim (m) in last 12 months"
                titleLast30Days.text = "Your swim (m) in last 30 days"
            }
            else -> {
                titleLast12Months.text = "Your steps in last 12 months"
                titleLast30Days.text = "Your steps in last 30 days"
            }
        }

        val uid = supabaseUserManager.getCurrentUserUid()
        if (uid == null) {
            Toast.makeText(this, "Please sign in to view data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        when (dataType) {
            TYPE_TOKENS -> loadTokenData(uid)
            else -> loadActivityData(uid)
        }
    }

    private fun loadActivityData(uid: String) {
        val now = LocalDate.now()
        val start12 = now.minusMonths(11).withDayOfMonth(1)
        val end12 = now
        val start30 = now.minusDays(29)
        val end30 = now
        val start12Str = start12.toString()
        val end12Str = end12.toString()
        val start30Str = start30.toString()
        val end30Str = end30.toString()

        when (dataType) {
            TYPE_STEPS -> {
                supabaseUserManager.getStepDataRange(uid, start12Str, end12Str, object : SupabaseUserManager.DatabaseCallback<List<StepData>> {
                    override fun onSuccess(result: List<StepData>) {
                        val byMonth = aggregateStepsByLast12Months(result, start12)
                        supabaseUserManager.getStepDataRange(uid, start30Str, end30Str, object : SupabaseUserManager.DatabaseCallback<List<StepData>> {
                            override fun onSuccess(result30: List<StepData>) {
                                val byDay = aggregateStepsByLast30Days(result30, start30)
                                runOnUiThread {
                                    updateBarChart(barChartY, byMonth.map { it.toFloat() }.toFloatArray(), "Steps")
                                    updateBarChart(barChartM, byDay.map { it.toFloat() }.toFloatArray(), "Steps")
                                }
                            }
                            override fun onError(error: String) {
                                runOnUiThread { Toast.makeText(this@StepDataViewActivity, error, Toast.LENGTH_SHORT).show() }
                            }
                        })
                    }
                    override fun onError(error: String) {
                        runOnUiThread { Toast.makeText(this@StepDataViewActivity, error, Toast.LENGTH_SHORT).show() }
                    }
                })
            }
            TYPE_CYCLE -> {
                supabaseUserManager.getBikeDataRange(uid, start12Str, end12Str, object : SupabaseUserManager.DatabaseCallback<List<BikeData>> {
                    override fun onSuccess(result: List<BikeData>) {
                        val byMonth = aggregateBikeByLast12Months(result, start12)
                        supabaseUserManager.getBikeDataRange(uid, start30Str, end30Str, object : SupabaseUserManager.DatabaseCallback<List<BikeData>> {
                            override fun onSuccess(result30: List<BikeData>) {
                                val byDay = aggregateBikeByLast30Days(result30, start30)
                                runOnUiThread {
                                    updateBarChart(barChartY, byMonth.map { it.toFloat() }.toFloatArray(), "m")
                                    updateBarChart(barChartM, byDay.map { it.toFloat() }.toFloatArray(), "m")
                                }
                            }
                            override fun onError(error: String) {
                                runOnUiThread { Toast.makeText(this@StepDataViewActivity, error, Toast.LENGTH_SHORT).show() }
                            }
                        })
                    }
                    override fun onError(error: String) {
                        runOnUiThread { Toast.makeText(this@StepDataViewActivity, error, Toast.LENGTH_SHORT).show() }
                    }
                })
            }
            TYPE_SWIM -> {
                supabaseUserManager.getSwimDataRange(uid, start12Str, end12Str, object : SupabaseUserManager.DatabaseCallback<List<SwimData>> {
                    override fun onSuccess(result: List<SwimData>) {
                        val byMonth = aggregateSwimByLast12Months(result, start12)
                        supabaseUserManager.getSwimDataRange(uid, start30Str, end30Str, object : SupabaseUserManager.DatabaseCallback<List<SwimData>> {
                            override fun onSuccess(result30: List<SwimData>) {
                                val byDay = aggregateSwimByLast30Days(result30, start30)
                                runOnUiThread {
                                    updateBarChart(barChartY, byMonth.map { it.toFloat() }.toFloatArray(), "m")
                                    updateBarChart(barChartM, byDay.map { it.toFloat() }.toFloatArray(), "m")
                                }
                            }
                            override fun onError(error: String) {
                                runOnUiThread { Toast.makeText(this@StepDataViewActivity, error, Toast.LENGTH_SHORT).show() }
                            }
                        })
                    }
                    override fun onError(error: String) {
                        runOnUiThread { Toast.makeText(this@StepDataViewActivity, error, Toast.LENGTH_SHORT).show() }
                    }
                })
            }
            else -> { }
        }
    }

    private fun loadTokenData(uid: String) {
        supabaseUserManager.fetchTokenRecordsLast12Months(uid, object : SupabaseUserManager.DatabaseCallback<List<TokenRecord>> {
            override fun onSuccess(result: List<TokenRecord>) {
                val tokensPerMonth = result.map { (it.reimbursable_tokens + it.nonreimbursable_tokens).toFloat() }.toFloatArray()
                runOnUiThread {
                    updateBarChart(barChartY, tokensPerMonth, "Tokens")
                }
            }
            override fun onError(error: String) {
                runOnUiThread { Toast.makeText(this@StepDataViewActivity, error, Toast.LENGTH_SHORT).show() }
            }
        })
    }

    private fun aggregateStepsByLast12Months(records: List<StepData>, startMonth: LocalDate): IntArray {
        val out = IntArray(12)
        for (r in records) {
            val d = LocalDate.parse(r.date)
            val months = ChronoUnit.MONTHS.between(startMonth, d).toInt()
            if (months in 0..11) out[months] += r.steps
        }
        return out
    }

    private fun aggregateStepsByLast30Days(records: List<StepData>, startDay: LocalDate): IntArray {
        val out = IntArray(30)
        for (r in records) {
            val d = LocalDate.parse(r.date)
            val days = ChronoUnit.DAYS.between(startDay, d).toInt()
            if (days in 0..29) out[days] += r.steps
        }
        return out
    }

    private fun aggregateBikeByLast12Months(records: List<BikeData>, startMonth: LocalDate): IntArray {
        val out = IntArray(12)
        for (r in records) {
            val d = LocalDate.parse(r.date)
            val months = ChronoUnit.MONTHS.between(startMonth, d).toInt()
            if (months in 0..11) out[months] += r.m_per_day
        }
        return out
    }

    private fun aggregateBikeByLast30Days(records: List<BikeData>, startDay: LocalDate): IntArray {
        val out = IntArray(30)
        for (r in records) {
            val d = LocalDate.parse(r.date)
            val days = ChronoUnit.DAYS.between(startDay, d).toInt()
            if (days in 0..29) out[days] += r.m_per_day
        }
        return out
    }

    private fun aggregateSwimByLast12Months(records: List<SwimData>, startMonth: LocalDate): IntArray {
        val out = IntArray(12)
        for (r in records) {
            val d = LocalDate.parse(r.date)
            val months = ChronoUnit.MONTHS.between(startMonth, d).toInt()
            if (months in 0..11) out[months] += r.m_per_day
        }
        return out
    }

    private fun aggregateSwimByLast30Days(records: List<SwimData>, startDay: LocalDate): IntArray {
        val out = IntArray(30)
        for (r in records) {
            val d = LocalDate.parse(r.date)
            val days = ChronoUnit.DAYS.between(startDay, d).toInt()
            if (days in 0..29) out[days] += r.m_per_day
        }
        return out
    }

    private fun updateBarChart(barChart: BarChart, values: FloatArray, label: String) {
        val entries = values.mapIndexed { index, v -> BarEntry((index + 1).toFloat(), v) }
        val dataSet = BarDataSet(entries, label)
        dataSet.color = ContextCompat.getColor(this, R.color.luga_blue)
        dataSet.setDrawValues(false)
        barChart.data = BarData(dataSet)
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
