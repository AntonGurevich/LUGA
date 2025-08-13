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
    private lateinit var deleteButton: Button         // Account deletion button

    // Text views for displaying saved user data (currently unused)
    private lateinit var textView: TextView
    private lateinit var textView2: TextView

    // Google Sign-In client for authentication
    private lateinit var googleSignInClient: GoogleSignInClient

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

        // Initialize all UI elements from the layout
        emailEditText = findViewById(R.id.login_email_edittext)
        passwordEditText = findViewById(R.id.login_password_edittext)
        loginButton = findViewById(R.id.login_button)
        createAccountButton = findViewById(R.id.register_button)
        deleteButton = findViewById(R.id.delete_button)
        
        // Initially hide the create account button
        createAccountButton.visibility = View.GONE

        // Check if user data exists to determine UI state
        if (!isUserDataSaved()) {
            // No user data - show registration option, hide login elements
            loginButton.visibility = View.GONE
            emailEditText.visibility = View.GONE
            passwordEditText.visibility = View.GONE
            createAccountButton.visibility = View.VISIBLE
            deleteButton.visibility = View.GONE
        }

        // Retrieve saved user credentials for validation
        val savedEmail = sharedPref?.getString("email", "no value")
        val savedPassword = sharedPref?.getString("password", "no value")

        // Set up login button click listener
        loginButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()

            // Validate input and check credentials
            if (isEmailValid(email) && isPasswordValid(password)) {
                if (isUserRegistered(email, password)) {
                    // User authenticated successfully - set up Google Sign-In for fitness data
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

                    // Navigate to main app functionality
                    val intent = Intent(this, CentralActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show()
            }
        }

        // Set up create account button to navigate to registration
        createAccountButton.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        // Set up delete account button with confirmation dialog
        deleteButton.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Delete Account")
            builder.setMessage("Are you sure you want to delete your account?")

            builder.setPositiveButton("Yes") { dialog, _ ->
                // Remove saved user credentials
                val editor = sharedPref?.edit()
                editor?.remove("email")?.apply()
                editor?.remove("password")?.apply()
                
                // Return to main activity
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                dialog.dismiss()
            }

            builder.setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }

            val dialog: AlertDialog = builder.create()
            dialog.show()
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

    companion object {
        const val RC_SIGN_IN = 9001                                    // Request code for Google Sign-In
        private const val TAG = "LoginActivity"                         // Log tag for debugging
        private const val REQUEST_ACTIVITY_RECOGNITION_PERMISSION = 1002 // Permission request code
    }
}

