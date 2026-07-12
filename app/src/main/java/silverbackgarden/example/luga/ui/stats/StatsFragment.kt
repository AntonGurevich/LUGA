package silverbackgarden.example.luga.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis.XAxisPosition
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import silverbackgarden.example.luga.BikeData
import silverbackgarden.example.luga.R
import silverbackgarden.example.luga.StepData
import silverbackgarden.example.luga.SupabaseUserManager
import silverbackgarden.example.luga.SwimData
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Stats tab: activity selector + Week/Month/Year range, a bar chart, and a
 * Total/Avg/Best summary card. Ported from iOS StatsView, but reuses
 * Android's existing MPAndroidChart dependency and the raw_steps/raw_bike/
 * raw_swim range queries that already power StepDataViewActivity's charts.
 */
class StatsFragment : Fragment() {

    private enum class ActivitySelection { STEPS, CYCLING, SWIMMING }
    private enum class RangeSelection { WEEK, MONTH, YEAR }

    private data class PeriodBar(val label: String, val value: Float, val isFuture: Boolean)

    private val supabaseUserManager = SupabaseUserManager()

    private var selectedActivity = ActivitySelection.STEPS
    private var selectedRange = RangeSelection.MONTH
    private var referenceDate: LocalDate = LocalDate.now()

    private lateinit var barChart: BarChart
    private lateinit var tvPeriodLabel: TextView
    private lateinit var tvStatTotal: TextView
    private lateinit var tvStatAvg: TextView
    private lateinit var tvStatAvgLabel: TextView
    private lateinit var tvStatBest: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_stats, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        barChart = view.findViewById(R.id.statsBarChart)
        tvPeriodLabel = view.findViewById(R.id.tvPeriodLabel)
        tvStatTotal = view.findViewById(R.id.tvStatTotal)
        tvStatAvg = view.findViewById(R.id.tvStatAvg)
        tvStatAvgLabel = view.findViewById(R.id.tvStatAvgLabel)
        tvStatBest = view.findViewById(R.id.tvStatBest)

        val activityChips = view.findViewById<ChipGroup>(R.id.activityChipGroup)
        activityChips.setOnCheckedStateChangeListener { group, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            selectedActivity = when (id) {
                R.id.chipCycling -> ActivitySelection.CYCLING
                R.id.chipSwimming -> ActivitySelection.SWIMMING
                else -> ActivitySelection.STEPS
            }
            loadAndRender()
        }

        val rangeChips = view.findViewById<ChipGroup>(R.id.rangeChipGroup)
        rangeChips.setOnCheckedStateChangeListener { group, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            selectedRange = when (id) {
                R.id.chipWeek -> RangeSelection.WEEK
                R.id.chipYear -> RangeSelection.YEAR
                else -> RangeSelection.MONTH
            }
            referenceDate = LocalDate.now()
            loadAndRender()
        }

        view.findViewById<View>(R.id.btnPrevPeriod).setOnClickListener {
            referenceDate = when (selectedRange) {
                RangeSelection.WEEK -> referenceDate.minusWeeks(1)
                RangeSelection.MONTH -> referenceDate.minusMonths(1)
                RangeSelection.YEAR -> referenceDate.minusYears(1)
            }
            loadAndRender()
        }
        view.findViewById<View>(R.id.btnNextPeriod).setOnClickListener {
            referenceDate = when (selectedRange) {
                RangeSelection.WEEK -> referenceDate.plusWeeks(1)
                RangeSelection.MONTH -> referenceDate.plusMonths(1)
                RangeSelection.YEAR -> referenceDate.plusYears(1)
            }
            loadAndRender()
        }

        setupChartAppearance()
        loadAndRender()
    }

    private fun setupChartAppearance() {
        barChart.description.isEnabled = false
        barChart.legend.isEnabled = false
        barChart.setDrawGridBackground(false)
        barChart.setFitBars(true)
        barChart.axisRight.isEnabled = false
        barChart.axisLeft.setDrawGridLines(false)
        barChart.axisLeft.axisMinimum = 0f
        barChart.xAxis.setDrawGridLines(false)
        barChart.xAxis.position = XAxisPosition.BOTTOM
        barChart.xAxis.granularity = 1f
    }

    private fun loadAndRender() {
        updatePeriodLabel()

        val userId = supabaseUserManager.getCurrentUserUid()
        if (userId.isNullOrEmpty()) return

        val (start, end) = rangeBounds()
        val startStr = start.toString()
        val endStr = end.toString()

        when (selectedActivity) {
            ActivitySelection.STEPS -> supabaseUserManager.getStepDataRange(userId, startStr, endStr, object : SupabaseUserManager.DatabaseCallback<List<StepData>> {
                override fun onSuccess(result: List<StepData>) {
                    if (!isAdded) return
                    render(result.associate { it.date to it.steps.toFloat() })
                }
                override fun onError(error: String) { }
            })
            ActivitySelection.CYCLING -> supabaseUserManager.getBikeDataRange(userId, startStr, endStr, object : SupabaseUserManager.DatabaseCallback<List<BikeData>> {
                override fun onSuccess(result: List<BikeData>) {
                    if (!isAdded) return
                    render(result.associate { it.date to it.m_per_day.toFloat() })
                }
                override fun onError(error: String) { }
            })
            ActivitySelection.SWIMMING -> supabaseUserManager.getSwimDataRange(userId, startStr, endStr, object : SupabaseUserManager.DatabaseCallback<List<SwimData>> {
                override fun onSuccess(result: List<SwimData>) {
                    if (!isAdded) return
                    render(result.associate { it.date to it.m_per_day.toFloat() })
                }
                override fun onError(error: String) { }
            })
        }
    }

    /** Start/end (inclusive) of the currently selected period. */
    private fun rangeBounds(): Pair<LocalDate, LocalDate> = when (selectedRange) {
        RangeSelection.WEEK -> {
            val weekFields = WeekFields.of(Locale.getDefault())
            val start = referenceDate.with(weekFields.dayOfWeek(), 1L)
            start to start.plusDays(6)
        }
        RangeSelection.MONTH -> {
            val start = referenceDate.withDayOfMonth(1)
            start to start.withDayOfMonth(start.lengthOfMonth())
        }
        RangeSelection.YEAR -> {
            val start = referenceDate.withDayOfYear(1)
            start to LocalDate.of(referenceDate.year, 12, 31)
        }
    }

    private fun updatePeriodLabel() {
        tvPeriodLabel.text = when (selectedRange) {
            RangeSelection.WEEK -> {
                val (start, end) = rangeBounds()
                val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
                "${start.format(fmt)} - ${end.format(fmt)}, ${end.year}"
            }
            RangeSelection.MONTH -> referenceDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
            RangeSelection.YEAR -> referenceDate.year.toString()
        }
        tvStatAvgLabel.text = if (selectedRange == RangeSelection.YEAR) "AVG/MO" else "AVG/DAY"
    }

    private fun render(valuesByDate: Map<String, Float>) {
        val today = LocalDate.now()
        val bars: List<PeriodBar> = when (selectedRange) {
            RangeSelection.WEEK, RangeSelection.MONTH -> {
                val (start, end) = rangeBounds()
                val dayCount = ChronoUnit.DAYS.between(start, end).toInt() + 1
                (0 until dayCount).map { offset ->
                    val date = start.plusDays(offset.toLong())
                    val label = when (selectedRange) {
                        RangeSelection.WEEK -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        else -> date.dayOfMonth.toString()
                    }
                    PeriodBar(label, valuesByDate[date.toString()] ?: 0f, date.isAfter(today))
                }
            }
            RangeSelection.YEAR -> {
                val sums = FloatArray(12)
                for ((dateStr, value) in valuesByDate) {
                    val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: continue
                    if (date.year == referenceDate.year) sums[date.monthValue - 1] += value
                }
                (0 until 12).map { monthIndex ->
                    val monthDate = LocalDate.of(referenceDate.year, monthIndex + 1, 1)
                    val label = monthDate.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    val isFuture = monthDate.year > today.year || (monthDate.year == today.year && monthDate.monthValue > today.monthValue)
                    PeriodBar(label, sums[monthIndex], isFuture)
                }
            }
        }

        renderChart(bars)
        renderStatsCard(bars)
    }

    private fun renderChart(bars: List<PeriodBar>) {
        if (!isAdded) return
        val activityColor = ContextCompat.getColor(requireContext(), colorForActivity())
        val futureColor = ContextCompat.getColor(requireContext(), R.color.act_ink_300)

        val entries = bars.mapIndexed { index, bar -> BarEntry(index.toFloat(), bar.value) }
        val colors = bars.map { if (it.isFuture) futureColor else activityColor }

        val dataSet = BarDataSet(entries, "")
        dataSet.colors = colors
        dataSet.setDrawValues(false)

        barChart.data = BarData(dataSet)
        barChart.xAxis.valueFormatter = IndexAxisValueFormatter(bars.map { it.label })

        // Thin out X-axis labels for the month view (31 bars is too crowded).
        barChart.xAxis.labelCount = if (bars.size > 12) 6 else bars.size

        barChart.animateY(400)
        barChart.invalidate()
    }

    private fun renderStatsCard(bars: List<PeriodBar>) {
        val pastBars = bars.filter { !it.isFuture }
        val total = pastBars.sumOf { it.value.toDouble() }
        val avg = if (pastBars.isNotEmpty()) total / pastBars.size else 0.0
        val best = pastBars.maxOfOrNull { it.value } ?: 0f

        tvStatTotal.text = formatNumber(total)
        tvStatAvg.text = formatNumber(avg)
        tvStatBest.text = formatNumber(best.toDouble())
    }

    private fun formatNumber(value: Double): String {
        val rounded = value.roundToInt()
        return String.format(Locale.getDefault(), "%,d", rounded)
    }

    private fun colorForActivity(): Int = when (selectedActivity) {
        ActivitySelection.STEPS -> R.color.act_blue_500
        ActivitySelection.CYCLING -> R.color.act_mint_500
        ActivitySelection.SWIMMING -> R.color.act_sky
    }
}
