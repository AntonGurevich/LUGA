package silverbackgarden.example.luga

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Second step of the registration process.
 * Handles employer code collection and creates the database record.
 * Completes the registration process.
 */
class RegisterStep2Activity : AppCompatActivity() {

    private lateinit var employerCodeEditText: EditText
    private lateinit var completeRegistrationButton: Button
    private lateinit var supabaseUserManager: SupabaseUserManager
    private lateinit var authManager: AuthManager

    private var userEmail: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_step2)

        supabaseUserManager = SupabaseUserManager()
        authManager = AuthManager(this)
        
        // Get email from previous step (we always use authenticated user's UID from auth)
        userEmail = intent.getStringExtra("email") ?: ""
        
        if (userEmail.isEmpty()) {
            Toast.makeText(this, "Error: No email provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        bindViews()
        setupListeners()
    }
    

    private fun bindViews() {
        employerCodeEditText = findViewById(R.id.employer_code_edittext)
        completeRegistrationButton = findViewById(R.id.complete_registration_button)
    }

    private fun setupListeners() {
        completeRegistrationButton.setOnClickListener {
            completeRegistration()
        }
    }

    private fun completeRegistration() {
        val employerCode = employerCodeEditText.text.toString()

        if (employerCode.isBlank()) {
            Toast.makeText(this, "Please enter your employer code", Toast.LENGTH_SHORT).show()
            return
        }

        // Parse employer code as connection code
        val connectionCode = employerCode.toLongOrNull()
        if (connectionCode == null) {
            Toast.makeText(this, "Invalid employer code format. Please enter a valid number.", Toast.LENGTH_SHORT).show()
            return
        }

        // Show progress
        completeRegistrationButton.isEnabled = false
        completeRegistrationButton.text = "Validating Code..."

        // First validate the employer code
        supabaseUserManager.validateEmployerCode(connectionCode, object : SupabaseUserManager.DatabaseCallback<EmployerCodeValidationResult> {
            override fun onSuccess(result: EmployerCodeValidationResult) {
                if (!result.isValid) {
                    // Validation failed
                    completeRegistrationButton.isEnabled = true
                    completeRegistrationButton.text = "Complete Registration"
                    Toast.makeText(this@RegisterStep2Activity, result.errorMessage ?: "Invalid employer code", Toast.LENGTH_LONG).show()
                    return
                }
                
                // Validation passed, proceed with registration
                completeRegistrationButton.text = "Completing Registration..."
                proceedWithRegistration(connectionCode, result.companyInfo)
            }

            override fun onError(error: String) {
                completeRegistrationButton.isEnabled = true
                completeRegistrationButton.text = "Complete Registration"
                Toast.makeText(this@RegisterStep2Activity, "Error validating employer code: $error", Toast.LENGTH_LONG).show()
            }
        })
    }
    
    /**
     * Proceeds with user registration after employer code validation passes.
     * 
     * @param connectionCode The validated connection code
     * @param companyInfo The company information from the registry
     */
    private fun proceedWithRegistration(connectionCode: Long, companyInfo: CompanyUserRegistry?) {
        // Create database record
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Always use the authenticated user's UID from auth (required for RLS policies)
                // The RLS policy checks that uid = auth.uid(), so we must use the current authenticated user's ID
                val userUid = supabaseUserManager.getCurrentUserUid()
                
                // Get the full user object for additional logging
                val currentAuthUser = authManager.getCurrentUser()
                if (currentAuthUser != null) {
                    Log.d("RegisterStep2", "Current authenticated user - Email: ${currentAuthUser.email}, UID: ${currentAuthUser.id}")
                }
                
                if (userUid == null) {
                    Log.e("RegisterStep2", "ERROR: getCurrentUserUid() returned null - user not authenticated")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@RegisterStep2Activity, "Error: User not authenticated. Please verify your email and log in.", Toast.LENGTH_LONG).show()
                        completeRegistrationButton.isEnabled = true
                        completeRegistrationButton.text = "Complete Registration"
                        
                        // Navigate to login
                        val intent = Intent(this@RegisterStep2Activity, LoginActivity::class.java)
                        intent.putExtra("email", userEmail)
                        startActivity(intent)
                    }
                    return@launch
                }
                
                Log.d("RegisterStep2", "Creating database record with authenticated user ID: $userUid")
                Log.d("RegisterStep2", "User authenticated: ${authManager.isLoggedIn()}")
                Log.d("RegisterStep2", "Email: $userEmail, UID: $userUid")

                // Create user record in database
                supabaseUserManager.registerUser(
                    email = userEmail,
                    connectionCode = connectionCode,
                    authUserId = userUid,
                    callback = object : SupabaseUserManager.DatabaseCallback<UserData> {
                        override fun onSuccess(result: UserData) {
                            runOnUiThread {
                                val companyName = companyInfo?.company_name ?: "Unknown Company"
                                val isAuthenticated = authManager.isLoggedIn()
                                
                                if (isAuthenticated) {
                                    Toast.makeText(this@RegisterStep2Activity, "Registration completed successfully! Welcome to $companyName", Toast.LENGTH_LONG).show()
                                } else {
                                    // User hasn't verified email yet
                                    Toast.makeText(this@RegisterStep2Activity, "Registration completed! Please verify your email to access all features. Welcome to $companyName", Toast.LENGTH_LONG).show()
                                }
                                
                                // Navigate to main app (user can verify email later)
                                val intent = Intent(this@RegisterStep2Activity, CentralActivity::class.java)
                                startActivity(intent)
                                finish()
                            }
                        }

                        override fun onError(error: String) {
                            runOnUiThread {
                                Toast.makeText(this@RegisterStep2Activity, "Registration error: $error", Toast.LENGTH_LONG).show()
                                completeRegistrationButton.isEnabled = true
                                completeRegistrationButton.text = "Complete Registration"
                            }
                        }
                    }
                )

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("RegisterStep2", "Registration error", e)
                    Toast.makeText(this@RegisterStep2Activity, "Registration error: ${e.message}", Toast.LENGTH_LONG).show()
                    completeRegistrationButton.isEnabled = true
                    completeRegistrationButton.text = "Complete Registration"
                }
            }
        }
    }
}
