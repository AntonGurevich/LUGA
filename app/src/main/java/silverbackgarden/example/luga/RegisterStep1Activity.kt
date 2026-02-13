package silverbackgarden.example.luga

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * First step of the registration process.
 * Handles email and password collection with confirmation validation.
 * Creates the Supabase Auth record upon successful validation.
 */
class RegisterStep1Activity : AppCompatActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var passwordMatchTextView: TextView
    private lateinit var continueButton: Button

    private lateinit var authManager: AuthManager
    private lateinit var sharedPreferences: SharedPreferences
    
    // Track registration state
    private var accountCreated = false
    private var userEmail: String = ""
    private var userPassword: String = ""
    private var userId: String? = null
    private var verificationEmailSent = false

    companion object {
        private const val PREFS_NAME = "registration_prefs"
        private const val KEY_PENDING_EMAIL = "pending_registration_email"
        private const val KEY_PENDING_PASSWORD = "pending_registration_password"
        private const val KEY_PENDING_USER_ID = "pending_registration_user_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_step1)

        authManager = AuthManager(this)
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        bindViews()
        setupListeners()
    }

    private fun bindViews() {
        emailEditText = findViewById(R.id.register_email_edittext)
        passwordEditText = findViewById(R.id.register_password_edittext)
        confirmPasswordEditText = findViewById(R.id.register_confirm_password_edittext)
        passwordMatchTextView = findViewById(R.id.password_match_textview)
        continueButton = findViewById(R.id.continue_button)
    }

    private fun setupListeners() {
        // Add text watchers for real-time password confirmation validation
        val passwordWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validatePasswordMatch()
            }
        }

        passwordEditText.addTextChangedListener(passwordWatcher)
        confirmPasswordEditText.addTextChangedListener(passwordWatcher)

        continueButton.setOnClickListener {
            if (!accountCreated) {
                // First click: Create account and send verification email
                verifyEmail()
            } else {
                // Second click: Check verification and proceed
                checkVerificationAndProceed()
            }
        }
        
        // Set initial button text
        continueButton.text = "Verify Email"
    }

    private fun validatePasswordMatch() {
        val password = passwordEditText.text.toString()
        val confirmPassword = confirmPasswordEditText.text.toString()

        if (confirmPassword.isNotEmpty()) {
            if (password == confirmPassword) {
                passwordMatchTextView.text = "✓ Passwords match"
                passwordMatchTextView.setTextColor(getColor(android.R.color.holo_green_dark))
                passwordMatchTextView.visibility = View.VISIBLE
            } else {
                passwordMatchTextView.text = "✗ Passwords do not match"
                passwordMatchTextView.setTextColor(getColor(android.R.color.holo_red_dark))
                passwordMatchTextView.visibility = View.VISIBLE
            }
        } else {
            passwordMatchTextView.visibility = View.GONE
        }
    }

    /**
     * Step 1: Create account and send verification email
     */
    private fun verifyEmail() {
        val email = emailEditText.text.toString()
        val password = passwordEditText.text.toString()
        val confirmPassword = confirmPasswordEditText.text.toString()

        // Validate inputs
        if (!isEmailValid(email)) {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isPasswordValid(password)) {
            Toast.makeText(this, "Password must be at least 6 characters long", Toast.LENGTH_SHORT).show()
            return
        }

        if (password != confirmPassword) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return
        }

        // Store email and password
        userEmail = email
        userPassword = password

        // Show progress
        continueButton.isEnabled = false
        continueButton.text = "Creating Account..."

        // Create Supabase Auth record
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = authManager.registerUser(email, password)
                
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        val user = result.getOrNull()
                        
                        if (user != null) {
                            userId = user.id.toString()
                            accountCreated = true
                            
                            // Supabase Auth creates the user record immediately, but email is not verified yet
                            // Always show verification dialog with instructions to click the link in email
                            Log.d("RegisterStep1", "Account created for: $email, user ID: $userId (email verification pending)")
                            Log.d("RegisterStep1", "User ID from signUpWith: ${user.id}")
                            
                            // Check if there's a current authenticated session
                            val currentAuthUser = authManager.getCurrentUser()
                            if (currentAuthUser != null) {
                                Log.d("RegisterStep1", "Current authenticated user UID: ${currentAuthUser.id}")
                                Log.d("RegisterStep1", "UIDs match: ${user.id.toString() == currentAuthUser.id.toString()}")
                            } else {
                                Log.d("RegisterStep1", "No authenticated session (email verification required)")
                            }
                            
                            verificationEmailSent = true
                            
                            // Always show verification dialog - user must click link in email before proceeding
                            Log.d("RegisterStep1", "Showing email verification dialog")
                            showEmailVerificationDialog(email)
                            
                            // Change button to "Continue" - user must click to proceed after verifying email
                            continueButton.isEnabled = true
                            continueButton.text = "Continue"
                        } else {
                            Log.e("RegisterStep1", "Registration succeeded but no user object returned")
                            Toast.makeText(this@RegisterStep1Activity, "Registration error: No user data returned", Toast.LENGTH_LONG).show()
                            continueButton.isEnabled = true
                            continueButton.text = "Verify Email"
                        }
                    } else {
                        val exception = result.exceptionOrNull()
                        val error = exception?.message ?: "Registration failed"
                        
                        // Log detailed error information
                        Log.e("RegisterStep1", "Registration failed with error: $error")
                        exception?.let {
                            Log.e("RegisterStep1", "Exception type: ${it.javaClass.name}")
                            Log.e("RegisterStep1", "Exception cause: ${it.cause?.message ?: "No cause"}")
                            Log.e("RegisterStep1", "Full stack trace:", it)
                        }
                        
                        // Show user-friendly error message
                        val userMessage = when {
                            error.contains("email", ignoreCase = true) && 
                            error.contains("send", ignoreCase = true) -> {
                                "Email service error. Please check Supabase email configuration or try again later."
                            }
                            error.contains("network", ignoreCase = true) -> {
                                "Network error. Please check your internet connection."
                            }
                            error.contains("rate limit", ignoreCase = true) -> {
                                "Too many requests. Please wait a moment and try again."
                            }
                            else -> "Registration failed: $error"
                        }
                        
                        Toast.makeText(this@RegisterStep1Activity, userMessage, Toast.LENGTH_LONG).show()
                        
                        // Re-enable button
                        continueButton.isEnabled = true
                        continueButton.text = "Verify Email"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RegisterStep1Activity, "Registration error: ${e.message}", Toast.LENGTH_LONG).show()
                    
                    // Re-enable button
                    continueButton.isEnabled = true
                    continueButton.text = "Verify Email"
                }
            }
        }
    }
    
    /**
     * Step 2: Check if email is verified and proceed to next step
     */
    private fun checkVerificationAndProceed() {
        if (!accountCreated || userId == null) {
            Toast.makeText(this, "Please verify your email first", Toast.LENGTH_SHORT).show()
            return
        }

        // First check if user is already authenticated (maybe they verified in another tab)
        if (authManager.isLoggedIn()) {
            Log.d("RegisterStep1", "User already authenticated - proceeding to step 2")
            proceedToStep2()
            return
        }

        continueButton.isEnabled = false
        continueButton.text = "Checking..."

        // Try to sign in to check if email is verified
        authManager.signIn(userEmail, userPassword, object : AuthManager.AuthCallback {
            override fun onSuccess(user: io.github.jan.supabase.gotrue.user.UserInfo?) {
                // Sign in successful means email is verified
                Log.d("RegisterStep1", "Email verified - sign in successful")
                if (user != null) {
                    Log.d("RegisterStep1", "Signed in user UID: ${user.id}")
                    Log.d("RegisterStep1", "Original user ID from signUpWith: $userId")
                    Log.d("RegisterStep1", "UIDs match after sign-in: ${user.id.toString() == userId}")
                }
                continueButton.isEnabled = true
                continueButton.text = "Continue"
                proceedToStep2()
            }

            override fun onError(error: String) {
                // Check if error is due to unverified email
                if (error == "EMAIL_NOT_VERIFIED") {
                    // Email not verified yet
                    Log.d("RegisterStep1", "Email not verified yet")
                    continueButton.isEnabled = true
                    continueButton.text = "Continue"
                    
                    // Show popup again
                    showEmailVerificationReminderDialog(userEmail)
                } else {
                    // Other error
                    Log.e("RegisterStep1", "Error checking verification: $error")
                    continueButton.isEnabled = true
                    continueButton.text = "Continue"
                    Toast.makeText(this@RegisterStep1Activity, "Error: $error", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
    
    /**
     * Proceed to Step 2 (employer code)
     * Note: We don't pass user_id - RegisterStep2Activity will always use authenticated user's UID from auth
     */
    private fun proceedToStep2() {
        val intent = Intent(this, RegisterStep2Activity::class.java)
        intent.putExtra("email", userEmail)
        // Don't pass user_id - RegisterStep2Activity will always use getCurrentUserUid() from auth
        startActivity(intent)
        finish()
    }

    private fun isEmailValid(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun isPasswordValid(password: String): Boolean {
        return password.length >= 6
    }
    
    /**
     * Shows a dialog instructing the user to check their email for verification.
     * This is shown after the verification email is sent.
     * Supabase Auth creates the user record immediately, but email verification is required.
     */
    private fun showEmailVerificationDialog(email: String) {
        Log.d("RegisterStep1", "showEmailVerificationDialog called for: $email")
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Verification Email Sent")
        builder.setMessage("We've sent a verification email to $email.\n\nPlease:\n1. Check your inbox (and spam folder if needed)\n2. Click the verification link in the email\n3. Return to this app and click 'Continue' to proceed")
        
        builder.setNeutralButton("Resend Email") { dialog, _ ->
            resendVerificationEmail(email)
            dialog.dismiss()
        }
        
        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
        }
        
        builder.setCancelable(false)
        val dialog: AlertDialog = builder.create()
        dialog.show()
        Log.d("RegisterStep1", "Email verification dialog shown")
    }
    
    /**
     * Shows a reminder dialog when user tries to continue but email is not verified yet.
     */
    private fun showEmailVerificationReminderDialog(email: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Email Not Verified Yet")
        builder.setMessage("Please check your email ($email) and click the verification link before continuing.\n\nIf you haven't received the email, check your spam folder or click 'Resend Email' below.")
        
        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
        }
        
        builder.setNeutralButton("Resend Email") { dialog, _ ->
            resendVerificationEmail(email)
            dialog.dismiss()
        }
        
        builder.setCancelable(true)
        val dialog: AlertDialog = builder.create()
        dialog.show()
    }
    
    /**
     * Resends the verification email to the user.
     */
    private fun resendVerificationEmail(email: String) {
        authManager.resendEmailConfirmation(email, object : AuthManager.AuthCallback {
            override fun onSuccess(user: io.github.jan.supabase.gotrue.user.UserInfo?) {
                Toast.makeText(this@RegisterStep1Activity, "Verification email resent to $email", Toast.LENGTH_LONG).show()
            }
            
            override fun onError(error: String) {
                Toast.makeText(this@RegisterStep1Activity, "Failed to resend email: $error", Toast.LENGTH_LONG).show()
            }
        })
    }
}


