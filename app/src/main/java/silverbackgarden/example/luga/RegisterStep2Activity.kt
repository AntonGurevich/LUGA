package silverbackgarden.example.luga

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

    private var userEmail: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_step2)

        supabaseUserManager = SupabaseUserManager()
        
        // Get email from previous step
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
                // Get current user UID from Supabase Auth
                val userUid = supabaseUserManager.getCurrentUserUid()
                if (userUid == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@RegisterStep2Activity, "Error: User not authenticated", Toast.LENGTH_SHORT).show()
                        completeRegistrationButton.isEnabled = true
                        completeRegistrationButton.text = "Complete Registration"
                    }
                    return@launch
                }

                // Create user record in database
                supabaseUserManager.registerUser(
                    email = userEmail,
                    connectionCode = connectionCode,
                    authUserId = userUid,
                    callback = object : SupabaseUserManager.DatabaseCallback<UserData> {
                        override fun onSuccess(result: UserData) {
                            runOnUiThread {
                                val companyName = companyInfo?.company_name ?: "Unknown Company"
                                Toast.makeText(this@RegisterStep2Activity, "Registration completed successfully! Welcome to $companyName", Toast.LENGTH_LONG).show()
                                
                                // Navigate to main app
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
