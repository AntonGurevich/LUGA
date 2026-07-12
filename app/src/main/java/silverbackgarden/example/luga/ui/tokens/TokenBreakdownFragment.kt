package silverbackgarden.example.luga.ui.tokens

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.chip.ChipGroup
import silverbackgarden.example.luga.ActivityTokenAllocation
import silverbackgarden.example.luga.BikeData
import silverbackgarden.example.luga.CompanyRules
import silverbackgarden.example.luga.R
import silverbackgarden.example.luga.StepData
import silverbackgarden.example.luga.SupabaseUserManager
import silverbackgarden.example.luga.SwimData
import silverbackgarden.example.luga.TokenRecord
import silverbackgarden.example.luga.computeActivityTokenAllocation
import silverbackgarden.example.luga.ui.CompanyRulesCache
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Tokens tab: earnings card, monthly breakdown, and per-activity allocation.
 * Ported from iOS TokenBreakdownView, reusing Android's existing
 * fetchTokenRecordsLast12Months + raw_steps/raw_bike/raw_swim range queries.
 */
class TokenBreakdownFragment : Fragment() {

    private enum class TokenRange(val months: Int) { THIS_MONTH(1), THREE_MONTHS(3), YEAR(12) }

    private val supabaseUserManager = SupabaseUserManager()
    private var selectedRange = TokenRange.THIS_MONTH
    private var last12Months: List<TokenRecord> = emptyList()

    private lateinit var tvEarningsAmount: TextView
    private lateinit var tvEarningsReimbursable: TextView
    private lateinit var tvEarningsNonReimbursable: TextView
    private lateinit var tvEarningsRateInfo: TextView
    private lateinit var monthlyContainer: LinearLayout
    private lateinit var activityContainer: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_tokens, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvEarningsAmount = view.findViewById(R.id.tvEarningsAmount)
        tvEarningsReimbursable = view.findViewById(R.id.tvEarningsReimbursable)
        tvEarningsNonReimbursable = view.findViewById(R.id.tvEarningsNonReimbursable)
        tvEarningsRateInfo = view.findViewById(R.id.tvEarningsRateInfo)
        monthlyContainer = view.findViewById(R.id.monthlyBreakdownContainer)
        activityContainer = view.findViewById(R.id.activityBreakdownContainer)

        view.findViewById<ChipGroup>(R.id.tokenRangeChipGroup).setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            selectedRange = when (id) {
                R.id.chipThreeMonths -> TokenRange.THREE_MONTHS
                R.id.chipTokenYear -> TokenRange.YEAR
                else -> TokenRange.THIS_MONTH
            }
            renderAll()
        }

        loadAll()
    }

    private fun loadAll() {
        val userId = supabaseUserManager.getCurrentUserUid() ?: return

        supabaseUserManager.fetchTokenRecordsLast12Months(userId, object : SupabaseUserManager.DatabaseCallback<List<TokenRecord>> {
            override fun onSuccess(result: List<TokenRecord>) {
                if (!isAdded) return
                last12Months = result
                renderAll()
            }
            override fun onError(error: String) { }
        })
    }

    private fun renderAll() {
        if (last12Months.isEmpty()) return
        val slice = last12Months.takeLast(selectedRange.months)
        val companyRules = CompanyRulesCache.current

        renderEarningsCard(slice, companyRules)
        renderMonthlyBreakdown(slice, companyRules)
        loadAndRenderActivityBreakdown(slice, companyRules)
    }

    private fun renderEarningsCard(slice: List<TokenRecord>, companyRules: CompanyRules?) {
        val totalReimbursable = slice.sumOf { it.reimbursable_tokens }
        val totalNonReimbursable = slice.sumOf { it.nonreimbursable_tokens }

        tvEarningsAmount.text = companyRules?.earnings(totalReimbursable) ?: "${formatNumber(totalReimbursable)} tokens"
        tvEarningsReimbursable.text = formatNumber(totalReimbursable)
        tvEarningsNonReimbursable.text = formatNumber(totalNonReimbursable)

        val rate = companyRules?.conversionRate
        if (rate != null && rate > 0) {
            val currency = companyRules.currency?.uppercase(Locale.getDefault()) ?: "USD"
            tvEarningsRateInfo.text = "1 token = ${String.format(Locale.getDefault(), "%.2f", rate)} $currency"
            tvEarningsRateInfo.visibility = View.VISIBLE
        } else {
            tvEarningsRateInfo.visibility = View.GONE
        }
    }

    private fun renderMonthlyBreakdown(slice: List<TokenRecord>, companyRules: CompanyRules?) {
        monthlyContainer.removeAllViews()
        val maxReimbursable = slice.maxOfOrNull { it.reimbursable_tokens } ?: 0.0

        // Most recent month first.
        for (record in slice.asReversed()) {
            val monthDate = runCatching { LocalDate.parse(record.month.take(10)) }.getOrNull()
            val monthLabel = monthDate?.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())) ?: record.month
            val earningsText = companyRules?.earnings(record.reimbursable_tokens) ?: "${formatNumber(record.reimbursable_tokens)} tok"
            val frac = if (maxReimbursable > 0) (record.reimbursable_tokens / maxReimbursable).coerceIn(0.0, 1.0) else 0.0
            addMonthRow(monthLabel, formatNumber(record.reimbursable_tokens), earningsText, frac)
        }
    }

    private fun addMonthRow(monthLabel: String, tokenCount: String, earningsText: String, frac: Double) {
        val context = requireContext()

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        headerRow.addView(TextView(context).apply {
            text = monthLabel
            setTextColor(ContextCompat.getColor(context, R.color.act_ink_900))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        headerRow.addView(TextView(context).apply {
            text = "$tokenCount  ·  $earningsText"
            setTextColor(ContextCompat.getColor(context, R.color.act_ink_500))
            textSize = 12.5f
        })
        row.addView(headerRow)

        val track = FrameLayout(context).apply {
            id = View.generateViewId()
            setBackgroundResource(R.drawable.bg_gauge_track_light)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8)).apply {
                topMargin = dp(6)
            }
        }
        track.addView(View(context).apply {
            setBackgroundResource(R.drawable.bg_gauge_fill)
            layoutParams = FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT)
            post {
                val totalWidth = track.width
                if (totalWidth > 0) layoutParams = (layoutParams as FrameLayout.LayoutParams).apply { width = (totalWidth * frac).toInt() }
            }
        })
        row.addView(track)

        monthlyContainer.addView(row)
    }

    private fun loadAndRenderActivityBreakdown(slice: List<TokenRecord>, companyRules: CompanyRules?) {
        val userId = supabaseUserManager.getCurrentUserUid() ?: return
        val start = rangeStartDate(slice)
        val end = LocalDate.now().toString()
        val startStr = start.toString()

        supabaseUserManager.getStepDataRange(userId, startStr, end, object : SupabaseUserManager.DatabaseCallback<List<StepData>> {
            override fun onSuccess(stepRows: List<StepData>) {
                if (!isAdded) return
                supabaseUserManager.getBikeDataRange(userId, startStr, end, object : SupabaseUserManager.DatabaseCallback<List<BikeData>> {
                    override fun onSuccess(bikeRows: List<BikeData>) {
                        if (!isAdded) return
                        supabaseUserManager.getSwimDataRange(userId, startStr, end, object : SupabaseUserManager.DatabaseCallback<List<SwimData>> {
                            override fun onSuccess(swimRows: List<SwimData>) {
                                if (!isAdded) return
                                val stepsUnits = stepRows.sumOf { it.steps }.toDouble()
                                val bikeUnits = bikeRows.sumOf { it.m_per_day }.toDouble()
                                val swimUnits = swimRows.sumOf { it.m_per_day }.toDouble()
                                val totalReimbursable = slice.sumOf { it.reimbursable_tokens }
                                val totalNonReimbursable = slice.sumOf { it.nonreimbursable_tokens }

                                val allocations = computeActivityTokenAllocation(
                                    stepsUnits, bikeUnits, swimUnits,
                                    totalReimbursable, totalNonReimbursable
                                ) { activityName -> companyRules?.isActivityAllowed(activityName) ?: true }

                                renderActivityBreakdown(allocations, companyRules)
                            }
                            override fun onError(error: String) { }
                        })
                    }
                    override fun onError(error: String) { }
                })
            }
            override fun onError(error: String) { }
        })
    }

    private fun rangeStartDate(slice: List<TokenRecord>): LocalDate {
        val earliest = slice.firstOrNull()?.month?.take(10)
        return earliest?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now().withDayOfMonth(1)
    }

    private fun renderActivityBreakdown(allocations: List<ActivityTokenAllocation>, companyRules: CompanyRules?) {
        activityContainer.removeAllViews()
        val currentMonth = last12Months.lastOrNull()
        val visible = allocations.filter { it.reimbursableTokens > 0 || it.nonReimbursableTokens > 0 }

        if (visible.isEmpty()) {
            activityContainer.addView(TextView(requireContext()).apply {
                text = "No activity tokens yet this period."
                setTextColor(ContextCompat.getColor(requireContext(), R.color.act_ink_500))
                textSize = 13f
            })
            return
        }

        for (allocation in visible) {
            val remainderText = if (selectedRange == TokenRange.THIS_MONTH) remainderLabel(allocation.activity, currentMonth) else null
            addActivityRow(allocation, remainderText, companyRules?.isActivityAllowed(allocation.activity) ?: true)
        }
    }

    private fun remainderLabel(activity: String, currentMonth: TokenRecord?): String? {
        currentMonth ?: return null
        return when (activity) {
            "steps" -> currentMonth.steps_to_token?.let { "${(10000 - it).roundToInt().coerceAtLeast(0)} steps to next token" }
            "cycling" -> currentMonth.bike_to_token?.let { "${(15000 - it).roundToInt().coerceAtLeast(0)}m to next token" }
            "swimming" -> currentMonth.swim_to_token?.let { "${(2000 - it).roundToInt().coerceAtLeast(0)}m to next token" }
            else -> null
        }
    }

    private fun addActivityRow(allocation: ActivityTokenAllocation, remainderText: String?, isAllowed: Boolean) {
        val context = requireContext()
        val (iconRes, label) = when (allocation.activity) {
            "cycling" -> R.drawable.bike to "Cycling"
            "swimming" -> R.drawable.swim to "Swimming"
            else -> R.drawable.run to "Steps"
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14)
            }
        }

        row.addView(ImageView(context).apply {
            setImageResource(iconRes)
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            alpha = if (isAllowed) 1f else 0.4f
        })

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
            }
        }
        textCol.addView(TextView(context).apply {
            text = if (isAllowed) label else "$label (paused)"
            setTextColor(ContextCompat.getColor(context, R.color.act_ink_900))
            textSize = 14.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        if (remainderText != null && isAllowed) {
            textCol.addView(TextView(context).apply {
                text = remainderText
                setTextColor(ContextCompat.getColor(context, R.color.act_ink_500))
                textSize = 12f
            })
        }
        row.addView(textCol)

        val totalTokens = (allocation.reimbursableTokens + allocation.nonReimbursableTokens).roundToInt()
        row.addView(TextView(context).apply {
            text = totalTokens.toString()
            setTextColor(ContextCompat.getColor(context, R.color.act_ink_900))
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        activityContainer.addView(row)
    }

    private fun formatNumber(value: Double): String = String.format(Locale.getDefault(), "%,d", value.roundToInt())

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
