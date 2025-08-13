package silverbackgarden.example.luga

/**
 * Data class representing the calculation results for step-based token rewards.
 * 
 * This class encapsulates all the information needed to display token balances
 * and step progress in the UI. It follows the app's token economy where:
 * - Every 10,000 steps = 1 token
 * - First 30 tokens per month are exchangeable
 * - Remaining tokens (up to 60 total) are non-exchangeable
 * 
 * @property steps The current step count for the day (0-9,999)
 * @property exchangeableTokens Number of tokens that can be exchanged this month (0-30)
 * @property nonExchangeableTokens Number of non-exchangeable tokens earned (0-30)
 * @property monthlyExchangeLimit Maximum exchangeable tokens per month (default: 30)
 * @property dailyStepGoal Daily step target to earn tokens (default: 10,000)
 */
data class TokenCalculation(
    val steps: Int,
    val exchangeableTokens: Int,
    val nonExchangeableTokens: Int,
    val monthlyExchangeLimit: Int = 30,
    val dailyStepGoal: Int = 10000
)
