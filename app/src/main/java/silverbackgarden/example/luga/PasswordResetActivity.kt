package silverbackgarden.example.luga

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.github.jan.supabase.gotrue.user.UserInfo
import silverbackgarden.example.luga.ui.components.AuthPasswordInputView
import silverbackgarden.example.luga.ui.components.AuthPrimaryButton

/**
 * Handles the acteamity://reset deep link (public/password_recovery.html forwards here
 * with access_token/refresh_token/type=recovery attached). Establishes the recovery
 * session, then lets the user set a new password. Mirrors iOS PasswordResetView.
 */
class PasswordResetActivity : AppCompatActivity() {

    private lateinit var authManager: AuthManager
    private lateinit var newPasswordInput: AuthPasswordInputView
    private lateinit var confirmPasswordInput: AuthPasswordInputView
    private lateinit var submitButton: AuthPrimaryButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password_reset)

        authManager = AuthManager(this)
        newPasswordInput = findViewById(R.id.newPasswordInput)
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput)
        submitButton = findViewById(R.id.passwordResetSubmitButton)
        submitButton.setOnClick { submit() }
        findViewById<TextView>(R.id.passwordResetBackToSignInLink).setOnClickListener { goToLogin() }

        val uri = intent?.data
        val accessToken = uri?.getQueryParameter("access_token")
        val refreshToken = uri?.getQueryParameter("refresh_token")

        if (accessToken.isNullOrEmpty() || refreshToken.isNullOrEmpty()) {
            showInvalidLink()
            return
        }

        authManager.importRecoverySession(accessToken, refreshToken, object : AuthManager.AuthCallback {
            override fun onSuccess(user: UserInfo?) { /* form is already visible by default */ }
            override fun onError(error: String) = showInvalidLink()
        })
    }

    private fun submit() {
        val newPassword = newPasswordInput.text
        val confirmPassword = confirmPasswordInput.text

        if (newPassword.length < 6) {
            newPasswordInput.setError("Password must be at least 6 characters.")
            return
        }
        newPasswordInput.setError(null)

        if (newPassword != confirmPassword) {
            confirmPasswordInput.setError("Passwords don't match.")
            return
        }
        confirmPasswordInput.setError(null)

        submitButton.setLoading(true)
        authManager.updatePassword(newPassword, object : AuthManager.AuthCallback {
            override fun onSuccess(user: UserInfo?) {
                submitButton.setLoading(false)
                showSuccess()
            }
            override fun onError(error: String) {
                submitButton.setLoading(false)
                confirmPasswordInput.setError(error)
            }
        })
    }

    private fun showSuccess() {
        findViewById<View>(R.id.passwordResetFormGroup).visibility = View.GONE
        findViewById<View>(R.id.passwordResetSuccessGroup).visibility = View.VISIBLE
        Handler(Looper.getMainLooper()).postDelayed({ goToLogin() }, 2000)
    }

    private fun showInvalidLink() {
        findViewById<View>(R.id.passwordResetFormGroup).visibility = View.GONE
        findViewById<View>(R.id.passwordResetInvalidGroup).visibility = View.VISIBLE
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
