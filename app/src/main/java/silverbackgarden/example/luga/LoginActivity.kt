package silverbackgarden.example.luga

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import silverbackgarden.example.luga.R
import android.util.Log
import io.github.jan.supabase.gotrue.user.UserInfo

/**
 * Login activity that handles user authentication for the Acteamity app.
 * 
 * This activity provides both traditional email/password authentication and
 * Google Sign-In integration. It manages user login state, validates credentials,
 * and handles the transition to the main app functionality.
 * 
 * The activity also includes account deletion functionality and automatically
 * shows registration options for new users who haven't created accounts yet.
 */
class LoginActivity : AppCompatActivity() {

    // UI Elements for user input and interaction
    private lateinit var emailEditText: EditText      // Email input field
    private lateinit var passwordEditText: EditText   // Password input field
    private lateinit var loginButton: Button          // Login submission button
    private lateinit var createAccountButton: Button  // Registration navigation button
    private lateinit var forgotPasswordText: TextView // Forgot password link

    // Google Sign-In client for authentication
    private lateinit var googleSignInClient: GoogleSignInClient
    
    // Supabase Auth manager for authentication
    private lateinit var authManager: AuthManager

    /**
     * Shared preferences instance for storing user credentials.
     * Uses lazy initialization to access the application's shared preferences.
     */
    private val sharedPref by lazy {
        (applicationContext as? Acteamity)?.sharedPref
    }

    /**
     * Called when the activity is first created.
     * Initializes the UI, sets up authentication logic, and configures
     * the appropriate view states based on whether user data exists.
     * 
     * @param savedInstanceState Bundle containing the activity's previously saved state
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize AuthManager
        authManager = AuthManager(this)

        // Initialize all UI elements from the layout
        emailEditText = findViewById(R.id.login_email_edittext)
        passwordEditText = findViewById(R.id.login_password_edittext)
        loginButton = findViewById(R.id.login_button)
        createAccountButton = findViewById(R.id.register_button)
        forgotPasswordText = findViewById(R.id.forgot_password_text)
        
        // Check if user is already logged in with Supabase
        if (authManager.isLoggedIn()) {
            // User is already authenticated - navigate to main app
            navigateToMainApp()
            return
        }

        // Always show login interface - users can choose to login or register
        loginButton.visibility = View.VISIBLE
        emailEditText.visibility = View.VISIBLE
        passwordEditText.visibility = View.VISIBLE
        createAccountButton.visibility = View.VISIBLE

        // Pre-fill email if saved credentials exist (for migration)
        if (isUserDataSaved()) {
            val savedEmail = sharedPref?.getString("email", null)
            if (!savedEmail.isNullOrEmpty()) {
                emailEditText.setText(savedEmail)
            }
        }

        // Set up login button click listener
        loginButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()

            // Validate input
            if (isEmailValid(email) && isPasswordValid(password)) {
                // Show loading state
                loginButton.isEnabled = false
                loginButton.text = "Signing in..."
                
                // Use Supabase Auth for authentication
                authManager.signIn(email, password, object : AuthManager.AuthCallback {
                    override fun onSuccess(user: UserInfo?) {
                        // Reset button state
                        loginButton.isEnabled = true
                        loginButton.text = "Login"
                        
                        // User authenticated successfully
                        Toast.makeText(this@LoginActivity, "Login successful!", Toast.LENGTH_SHORT).show()
                        
                        // Set up Google Sign-In for fitness data
                        setupGoogleSignIn()
                        
                        // Navigate to main app functionality
                        navigateToMainApp()
                    }

                    override fun onError(error: String) {
                        // Reset button state
                        loginButton.isEnabled = true
                        loginButton.text = "Login"
                        
                        Toast.makeText(this@LoginActivity, "Login failed: $error", Toast.LENGTH_LONG).show()
                    }
                })
            } else {
                Toast.makeText(this, "Please enter valid email and password", Toast.LENGTH_SHORT).show()
            }
        }

        // Set up create account button to navigate to registration
        createAccountButton.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        // Set up forgot password functionality
        forgotPasswordText.setOnClickListener {
            showForgotPasswordDialog()
        }

    }

    /**
     * Validates email format using Android's built-in email pattern matcher.
     * 
     * @param email The email string to validate
     * @return true if email is valid, false otherwise
     */
    private fun isEmailValid(email: String): Boolean {
        return if (email.isEmpty()) {
            false
        } else {
            Patterns.EMAIL_ADDRESS.matcher(email).matches()
        }
    }

    /**
     * Validates password by checking if it's not empty.
     * 
     * @param password The password string to validate
     * @return true if password is not empty, false otherwise
     */
    private fun isPasswordValid(password: String): Boolean {
        return password.isNotEmpty()
    }

    /**
     * Checks if the provided credentials match the saved user data.
     * 
     * @param email The email to check
     * @param password The password to check
     * @return true if credentials match saved data, false otherwise
     */
    private fun isUserRegistered(email: String, password: String): Boolean {
        val savedEmail = sharedPref?.getString("email", "no value")
        val savedPassword = sharedPref?.getString("password", "no value")
        return email == savedEmail && password == savedPassword
    }

    /**
     * Checks if user data (email and password) has been saved in shared preferences.
     * 
     * @return true if user data exists, false otherwise
     */
    private fun isUserDataSaved(): Boolean {
        val savedEmail = sharedPref?.getString("email", null)
        val savedPassword = sharedPref?.getString("password", null)
        return !savedEmail.isNullOrEmpty() && !savedPassword.isNullOrEmpty()
    }

    /**
     * Sets up Google Sign-In client for fitness data access.
     */
    private fun setupGoogleSignIn() {
        googleSignInClient = GoogleSignIn.getClient(this, GoogleSignInOptions.Builder(
            GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestId()
            .requestProfile()
            .requestScopes(Scope("https://www.googleapis.com/auth/fitness.activity.read"))
            .requestIdToken("465622083556-75gj6fqpims30lr2q1iqo5rd9dpkrc4f.apps.googleusercontent.com") // Web client ID
            .build())
        
        // Check if already signed in to Google, otherwise initiate sign-in
        GoogleSignIn.getLastSignedInAccount(this)?.let { readStepCount(it) } ?: signIn()
    }

    /**
     * Navigates to the main app activity.
     */
    private fun navigateToMainApp() {
        val intent = Intent(this, CentralActivity::class.java)
        startActivity(intent)
        finish()
    }

    /**
     * Placeholder method for reading step count from Google Fit API.
     * Currently not implemented but intended for future fitness data integration.
     * 
     * @param account The Google Sign-In account to use for API calls
     */
    private fun readStepCount(account: GoogleSignInAccount) {
        // Make the API call to read the step count
        // TODO: Implement Google Fit API integration
    }

    /**
     * Initiates the Google Sign-In process by starting the sign-in activity.
     */
    private fun signIn() {
        startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
    }

    /**
     * Handles the result of Google Sign-In activity.
     * Processes the authentication result and logs the outcome.
     * 
     * @param requestCode The request code passed to startActivityForResult()
     * @param resultCode The result code returned by the child activity
     * @param data Intent containing result data
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                Log.d(TAG, "Google Sign-In successful in LoginActivity: ${account.email}")
                // Handle successful sign-in
            } catch (e: com.google.android.gms.common.api.ApiException) {
                Log.e(TAG, "Google Sign-In failed in LoginActivity: ${e.statusCode}")
                // Handle failed sign-in
            }
        }
    }

    /**
     * Shows a dialog for password reset functionality.
     * Allows users to enter their email to receive a password reset link.
     */
    private fun showForgotPasswordDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Reset Password")
        builder.setMessage("Enter your email address to receive a password reset link.")

        // Create input field for email
        val input = EditText(this)
        input.hint = "Enter your email"
        input.inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        builder.setView(input)

        builder.setPositiveButton("Send Reset Link") { dialog, _ ->
            val email = input.text.toString().trim()
            if (email.isNotEmpty() && isEmailValid(email)) {
                // Send password reset email using Supabase Auth
                authManager.resetPassword(email, object : AuthManager.AuthCallback {
                    override fun onSuccess(user: UserInfo?) {
                        Toast.makeText(this@LoginActivity, "Password reset email sent to $email", Toast.LENGTH_LONG).show()
                    }
                    override fun onError(error: String) {
                        Toast.makeText(this@LoginActivity, "Failed to send reset email: $error", Toast.LENGTH_LONG).show()
                    }
                })
            } else {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog: AlertDialog = builder.create()
        dialog.show()
    }

    companion object {
        const val RC_SIGN_IN = 9001                                    // Request code for Google Sign-In
        private const val TAG = "LoginActivity"                         // Log tag for debugging
        private const val REQUEST_ACTIVITY_RECOGNITION_PERMISSION = 1002 // Permission request code
    }
}

