package silverbackgarden.example.luga

import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import io.github.jan.supabase.gotrue.user.UserInfo
import silverbackgarden.example.luga.ui.components.AuthPrimaryButton
import silverbackgarden.example.luga.ui.components.AuthTextInputView

/**
 * Dedicated Forgot Password screen (Phase 7), replacing LoginActivity's old
 * AlertDialog flow. Uses the "always show success" pattern to avoid leaking
 * whether an email address has an account (mirrors iOS ForgotPasswordView).
 */
class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var authManager: AuthManager
    private lateinit var emailInput: AuthTextInputView
    private lateinit var submitButton: AuthPrimaryButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        authManager = AuthManager(this)
        emailInput = findViewById(R.id.forgotPasswordEmailInput)
        submitButton = findViewById(R.id.forgotPasswordSubmitButton)

        submitButton.setOnClick { submit() }
        findViewById<android.widget.TextView>(R.id.backToSignInLink).setOnClickListener { finish() }
    }

    private fun submit() {
        val email = emailInput.text.trim()
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Enter a valid email address.")
            return
        }
        emailInput.setError(null)
        submitButton.setLoading(true)

        authManager.resetPassword(email, object : AuthManager.AuthCallback {
            override fun onSuccess(user: UserInfo?) = showSuccess()
            // Always show success — don't reveal whether the email has an account.
            override fun onError(error: String) = showSuccess()
        })
    }

    private fun showSuccess() {
        submitButton.setLoading(false)
        findViewById<android.view.View>(R.id.forgotPasswordFormGroup).visibility = android.view.View.GONE
        findViewById<android.view.View>(R.id.forgotPasswordSuccessGroup).visibility = android.view.View.VISIBLE
    }
}
