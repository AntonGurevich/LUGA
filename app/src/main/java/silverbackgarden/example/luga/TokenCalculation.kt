package silverbackgarden.example.luga

data class TokenCalculation(
    val steps: Int,
    val exchangeableTokens: Int,
    val nonExchangeableTokens: Int,
    val monthlyExchangeLimit: Int = 30,
    val dailyStepGoal: Int = 10000
)
