package silverbackgarden.example.luga

import java.util.Locale

/**
 * Domain wrapper around a company_rules row, ported from iOS's CompanyRules model.
 * Wraps the raw Supabase record with the two behaviors the UI needs: whether an
 * activity type counts toward tokens for this employer, and converting a token
 * count into a displayable earnings string.
 */
data class CompanyRules(
    val corpuid: String,
    val tokenLimit: Double?,
    val activityList: List<String>?,
    val conversionRate: Double?,
    val currency: String?
) {
    /**
     * Whether [activityName] (e.g. "steps", "cycling", "swimming") counts toward
     * tokens for this employer. A null/empty activity_list means no restriction —
     * all activities are allowed (matches how Android currently has no concept of
     * disabling activities, so absence of rules preserves existing behavior).
     */
    fun isActivityAllowed(activityName: String): Boolean {
        val list = activityList
        if (list.isNullOrEmpty()) return true
        return list.any { it.equals(activityName, ignoreCase = true) }
    }

    /**
     * Formats [tokens] as a currency string using this company's conversion_rate
     * and currency code, or falls back to the raw token count if no rate is set.
     */
    fun earnings(tokens: Double): String {
        val rate = conversionRate
        if (rate == null || rate <= 0.0) {
            return "$tokens tokens"
        }
        val amount = tokens * rate
        val currencyCode = currency?.uppercase(Locale.getDefault()) ?: "USD"
        val symbol = when (currencyCode) {
            "USD" -> "$"
            "GBP" -> "£"
            "EUR" -> "€"
            else -> "$currencyCode "
        }
        return String.format(Locale.getDefault(), "%s%.2f", symbol, amount)
    }

    companion object {
        fun from(record: CompanyRulesRecord): CompanyRules = CompanyRules(
            corpuid = record.corpuid,
            tokenLimit = record.token_limit?.toDouble(),
            // activity_list is stored as a comma-separated string (e.g. "steps,swimming,cycling").
            activityList = record.activity_list
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() },
            conversionRate = record.conversion_rate,
            currency = record.currency
        )
    }
}
