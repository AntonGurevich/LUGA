package silverbackgarden.example.luga

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Per-activity share of a total token balance, ported from iOS's
 * ActivityTokenAllocation / computeActivityTokenAllocation().
 */
data class ActivityTokenAllocation(
    val activity: String,
    val reimbursableTokens: Double,
    val nonReimbursableTokens: Double
)

private data class ActivityUnits(val activity: String, val totalUnits: Double, val tokenRate: Double)

/**
 * Splits [totalReimbursable]/[totalNonReimbursable] tokens across steps/cycling/swimming
 * proportionally to how many raw tokens each activity's raw unit total would earn on its
 * own (floor(units / rate)), honoring [isActivityAllowed] (company_rules.activity_list).
 * Matches Android's real token thresholds: 10,000 steps / 15,000m cycling / 2,000m swimming.
 */
fun computeActivityTokenAllocation(
    stepsUnits: Double,
    cyclingUnits: Double,
    swimmingUnits: Double,
    totalReimbursable: Double,
    totalNonReimbursable: Double,
    isActivityAllowed: (String) -> Boolean
): List<ActivityTokenAllocation> {
    val activities = listOf(
        ActivityUnits("steps", stepsUnits, 10000.0),
        ActivityUnits("cycling", cyclingUnits, 15000.0),
        ActivityUnits("swimming", swimmingUnits, 2000.0)
    )

    val rawTokens = activities.map { a -> if (isActivityAllowed(a.activity)) floor(a.totalUnits / a.tokenRate) else 0.0 }
    val totalRaw = rawTokens.sum()

    if (totalRaw <= 0.0) {
        return activities.map { ActivityTokenAllocation(it.activity, 0.0, 0.0) }
    }

    val reimbursableShare = rawTokens.map { (it / totalRaw) * totalReimbursable }
    val nonReimbursableShare = rawTokens.map { (it / totalRaw) * totalNonReimbursable }

    val reimbursableRounded = largestRemainderRound(reimbursableShare, totalReimbursable.roundToInt())
    val nonReimbursableRounded = largestRemainderRound(nonReimbursableShare, totalNonReimbursable.roundToInt())

    return activities.mapIndexed { i, a ->
        ActivityTokenAllocation(a.activity, reimbursableRounded[i].toDouble(), nonReimbursableRounded[i].toDouble())
    }
}

/**
 * Largest-remainder rounding: floors every value, then distributes the remaining whole
 * units (so the results sum exactly to [total]) to the entries with the largest
 * fractional remainder. Used so displayed integer token counts always add up correctly.
 */
fun largestRemainderRound(values: List<Double>, total: Int = values.sum().roundToInt()): List<Int> {
    if (values.isEmpty()) return emptyList()
    val floors = values.map { floor(it).toInt() }
    var remaining = total - floors.sum()
    val byRemainderDesc = values.indices.sortedByDescending { values[it] - floors[it] }
    val result = floors.toIntArray()
    for (index in byRemainderDesc) {
        if (remaining <= 0) break
        result[index] += 1
        remaining--
    }
    return result.toList()
}
