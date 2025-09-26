package silverbackgarden.example.luga

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.content.SharedPreferences
import io.github.jan.supabase.gotrue.user.UserInfo
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import kotlin.Result
import silverbackgarden.example.luga.UserData
import silverbackgarden.example.luga.UserExistsResponse

/**
 * Registration activity that handles new user account creation for the Acteamity app.
 * 
 * This activity collects user information including personal details, credentials,
 * and optional employer codes. It validates input data and stores user information
 * both locally in shared preferences and in a remote MySQL database.
 * 
 * The registration process includes email validation, password requirements,
 * and optional employer code verification for corporate users.
 */
class RegisterActivity : AppCompatActivity() {

    // UI Elements for user input
    private lateinit var emailEditText: EditText        // Email address input
    private lateinit var passwordEditText: EditText     // Password input
    private lateinit var codeSwitch: Switch             // Toggle for employer code requirement
    private lateinit var employerCodeEditText: EditText // Employer code input (conditional)
    private lateinit var registerButton: Button         // Registration submission button
    private lateinit var nameEditText: EditText         // First name input
    private lateinit var surnameEditText: EditText      // Last name input

         // Data storage and API communication
     private lateinit var sharedPreferences: SharedPreferences  // Local data storage
     private lateinit var authManager: AuthManager  // Supabase Auth manager
     private lateinit var supabaseUserManager: SupabaseUserManager  // Supabase database manager

    /**
     * Called when the activity is first created.
     * Initializes the UI, sets up event listeners, and establishes database connection.
     * 
     * @param savedInstanceState Bundle containing the activity's previously saved state
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Initialize AuthManager
        authManager = AuthManager(this)
        supabaseUserManager = SupabaseUserManager()

        bindViews()        // Initialize UI element references
        setupListeners()   // Set up button and switch event handlers
    }

    /**
     * Binds all UI elements to their corresponding views in the layout.
     * Also initializes shared preferences for local data storage.
     */
    private fun bindViews() {
        emailEditText = findViewById(R.id.register_email_edittext)
        passwordEditText = findViewById(R.id.register_password_edittext)
        codeSwitch = findViewById(R.id.code_switch)
        employerCodeEditText = findViewById(R.id.employer_code_edittext)
        registerButton = findViewById(R.id.register_button)
        nameEditText = findViewById(R.id.register_name_edittext)
        surnameEditText = findViewById(R.id.register_surname_edittext)
        sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
    }

    /**
     * Sets up event listeners for interactive UI elements.
     * Handles switch state changes and button clicks.
     */
    private fun setupListeners() {
        // Toggle employer code input field visibility based on switch state
        codeSwitch.setOnCheckedChangeListener { _, isChecked ->
            employerCodeEditText.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Set up registration button click handler
        registerButton.setOnClickListener {
            register()
        }
    }

    /**
     * Main registration method that validates input and processes user registration.
     * Collects all form data, validates it, and saves both locally and to Supabase.
     */
    private fun register() {
        // Extract all input values from UI elements
        val email = emailEditText.text.toString()
        val password = passwordEditText.text.toString()
        val hasEmployerCode = codeSwitch.isChecked
        val employerCode = employerCodeEditText.text.toString()
        val name = nameEditText.text.toString()
        val surname = surnameEditText.text.toString()

        // Validate email and password before proceeding
        if (!isEmailValid(email)) {
            showPasswordValidationDialog("Please enter a valid email address")
            return
        }
        
        if (!isPasswordValid(password)) {
            showPasswordValidationDialog("Password must be at least 6 characters long")
            return
        }
        
        if (isEmailValid(email) && isPasswordValid(password)) {
            // Check if employer code is required but not provided
            if (hasEmployerCode && employerCode.isBlank()) {
                Toast.makeText(this, "Please enter the Employer code", Toast.LENGTH_SHORT).show()
                return
            }

            // Validate name fields
            if (name.isBlank() || surname.isBlank()) {
                Toast.makeText(this, "Please enter your name and surname", Toast.LENGTH_SHORT).show()
                return
            }

            // Show registration progress
            Toast.makeText(this, "Registering user...", Toast.LENGTH_SHORT).show()
            
            // Disable register button to prevent multiple submissions
            registerButton.isEnabled = false
            registerButton.text = "Registering..."

            // Use employer code as connection code, or generate one if no employer code
            val connectionCode = if (hasEmployerCode && employerCode.isNotEmpty()) {
                employerCode.toLongOrNull() ?: supabaseUserManager.generateConnectionCode()
            } else {
                supabaseUserManager.generateConnectionCode()
            }
            
            Log.d("RegisterActivity", "Using connection code: $connectionCode (hasEmployerCode: $hasEmployerCode, employerCode: $employerCode)")
            
            // Debug: Check what's actually in the database
            supabaseUserManager.debugGetAllUsers(object : SupabaseUserManager.DatabaseCallback<List<UserData>> {
                override fun onSuccess(users: List<UserData>) {
                    Log.d("RegisterActivity", "DEBUG: Database contains ${users.size} users")
                }
                override fun onError(error: String) {
                    Log.e("RegisterActivity", "DEBUG: Error getting users: $error")
                }
            })
            
            // First check if user already exists in Supabase database
            supabaseUserManager.checkUserExists(email, connectionCode, object : SupabaseUserManager.DatabaseCallback<UserExistsResponse> {
                override fun onSuccess(result: UserExistsResponse) {
                    if (result.exists) {
                        // User already exists
                        registerButton.isEnabled = true
                        registerButton.text = "Register"
                        Toast.makeText(this@RegisterActivity, "User with this email already exists! Please try logging in instead.", Toast.LENGTH_LONG).show()
                        return
                    }
                    
                    // User doesn't exist, proceed with registration
                    registerWithSupabase(email, password, name, surname, hasEmployerCode, employerCode, connectionCode)
                }

                override fun onError(error: String) {
                    registerButton.isEnabled = true
                    registerButton.text = "Register"
                    Toast.makeText(this@RegisterActivity, "Error checking user existence: $error", Toast.LENGTH_LONG).show()
                }
            })
        } else {
            Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Handles the complete registration process with Supabase Auth and database.
     * 
     * @param email User's email address
     * @param password User's password
     * @param name User's first name
     * @param surname User's last name
     * @param hasEmployerCode Whether user has employer code
     * @param employerCode Employer code if applicable
     * @param connectionCode Generated connection code for the user
     */
    private fun registerWithSupabase(
        email: String, 
        password: String, 
        name: String, 
        surname: String, 
        hasEmployerCode: Boolean, 
        employerCode: String, 
        connectionCode: Long
    ) {
        // Register with Supabase Auth first
        Log.d("RegisterActivity", "Starting Supabase Auth registration for: $email")
        authManager.register(email, password, object : AuthManager.AuthCallback {
            override fun onSuccess(user: UserInfo?) {
                Log.d("RegisterActivity", "Supabase Auth registration successful. User: $user")
                
                // Save additional user data locally
                sharedPreferences.edit().apply {
                    if (hasEmployerCode) {
                        putString("employer_name", "LUGA")  // Set employer name for corporate users
                        putString("employer_code", employerCode)
                    }
                    putString("name", name)
                    putString("surname", surname)
                    putBoolean("has_employer_code", hasEmployerCode)
                    putLong("connection_code", connectionCode)  // Store connection code locally
                    apply()
                }

                if (user != null) {
                    Log.d("RegisterActivity", "User is fully registered. Auth ID: ${user.id}")
                    // User is fully registered and logged in, now save to Supabase database
                    saveUserToSupabaseDatabase(email, connectionCode, user.id.toString(), name, surname, hasEmployerCode, employerCode)
                } else {
                    Log.d("RegisterActivity", "Registration successful but email confirmation required")
                    // Registration successful but email confirmation required
                    showEmailVerificationDialog()
                }
            }

            override fun onError(error: String) {
                Log.e("RegisterActivity", "Supabase Auth registration failed: $error")
                // Reset button state
                registerButton.isEnabled = true
                registerButton.text = "Register"
                
                Toast.makeText(this@RegisterActivity, "Registration failed: $error", Toast.LENGTH_LONG).show()
            }
        })
    }
    
    /**
     * Saves user data to Supabase user_registry table after successful auth registration.
     * 
     * @param email User's email address
     * @param connectionCode User's connection code
     * @param authUserId The user ID from Supabase Auth
     * @param name User's first name
     * @param surname User's last name
     * @param hasEmployerCode Whether user has employer code
     * @param employerCode Employer code if applicable
     */
    private fun saveUserToSupabaseDatabase(
        email: String, 
        connectionCode: Long, 
        authUserId: String,
        _name: String, 
        _surname: String, 
        _hasEmployerCode: Boolean, 
        _employerCode: String
    ) {
        Log.d("RegisterActivity", "Saving user to database: email=$email, connectionCode=$connectionCode, authUserId=$authUserId")
        supabaseUserManager.registerUser(email, connectionCode, authUserId, object : SupabaseUserManager.DatabaseCallback<UserData> {
            override fun onSuccess(result: UserData) {
                Log.d("RegisterActivity", "User saved to database successfully: $result")
                // Reset button state
                registerButton.isEnabled = true
                registerButton.text = "Register"
                
                Toast.makeText(this@RegisterActivity, "Registration successful! User ID: ${result.uid}", Toast.LENGTH_LONG).show()
                
                // Navigate directly to main app since user is already authenticated
                val intent = Intent(this@RegisterActivity, CentralActivity::class.java)
                startActivity(intent)
                finish()
            }

            override fun onError(error: String) {
                Log.e("RegisterActivity", "Failed to save user to database: $error")
                
                // Clean up the auth user since database insertion failed
                Log.d("RegisterActivity", "Cleaning up auth user due to database failure")
                authManager.deleteCurrentUser(object : AuthManager.AuthCallback {
                    override fun onSuccess(user: UserInfo?) {
                        Log.d("RegisterActivity", "Auth user cleanup completed")
                        // Reset button state
                        registerButton.isEnabled = true
                        registerButton.text = "Register"
                        
                        Toast.makeText(this@RegisterActivity, "Registration failed: $error", Toast.LENGTH_LONG).show()
                    }
                    
                    override fun onError(cleanupError: String) {
                        Log.e("RegisterActivity", "Failed to cleanup auth user: $cleanupError")
                        // Reset button state even if cleanup fails
                        registerButton.isEnabled = true
                        registerButton.text = "Register"
                        
                        Toast.makeText(this@RegisterActivity, "Registration failed: $error (Cleanup also failed: $cleanupError)", Toast.LENGTH_LONG).show()
                    }
                })
            }
        })
    }

    /**
     * Validates email format using Android's built-in email pattern matcher.
     * 
     * @param email The email string to validate
     * @return true if email is valid, false otherwise
     */
    private fun isEmailValid(email: String) = email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()

    /**
     * Validates password by checking if it's not empty.
     * 
     * @param password The password string to validate
     * @return true if password is not empty, false otherwise
     */
    private fun isPasswordValid(password: String) = password.isNotEmpty()

    /**
     * Registers user via REST API to store additional user data in your database.
     * This is called after successful Supabase Auth registration.
     * 
     * @param email The user's email address
     * @param password The user's password
     * @param name The user's first name
     * @param surname The user's last name
     */
    private fun registerUserViaAPI(email: String, password: String, name: String, surname: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = registerUserViaAPICall(email, password, name, surname)
                if (result.isSuccess) {
                    Toast.makeText(this@RegisterActivity, "User registered successfully!", Toast.LENGTH_LONG).show()
                    // Navigate directly to main app since user is already authenticated
                    val intent = Intent(this@RegisterActivity, CentralActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@RegisterActivity, "Registration failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@RegisterActivity, "Registration error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Makes the actual API call to register the user in your database.
     * Uses the same Retrofit pattern as StepCountWorker for consistency.
     * 
     * @param email The user's email address
     * @param password The user's password
     * @param name The user's first name
     * @param surname The user's last name
     * @return Result<RegistrationResponse> indicating success or failure
     */
    private suspend fun registerUserViaAPICall(email: String, password: String, name: String, surname: String): Result<RegistrationResponse> {
        return withContext(Dispatchers.IO) {
            try {
                // Build Retrofit instance (same pattern as StepCountWorker)
                val retrofit = Retrofit.Builder()
                    .baseUrl("http://20.0.164.108:3000/")  // Same server as step data
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                
                val api = retrofit.create(AuthApi::class.java)
                
                // Create user registration data
                val userData = UserRegistrationData(
                    email = email,
                    password = password,  // TODO: Hash this on server side
                    name = name,
                    surname = surname,
                    employerCode = if (codeSwitch.isChecked) employerCodeEditText.text.toString() else null,
                    hasEmployerCode = codeSwitch.isChecked
                )
                
                // Make API call
                val response = api.registerUser(userData)
                
                if (response.isSuccessful) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Registration failed: ${response.code()} - ${response.errorBody()?.string()}"))
                }
                
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Retrofit interface for user authentication API.
     * Defines the HTTP endpoint and request structure for user registration.
     */
    interface AuthApi {
        @POST("/api/auth/register")
        suspend fun registerUser(@Body userData: UserRegistrationData): retrofit2.Response<RegistrationResponse>
    }
    
    /**
     * Data class representing user registration data for API communication.
     * Contains all necessary user information for registration.
     * 
     * @property email User's email address
     * @property password User's password (should be hashed on server side)
     * @property name User's first name
     * @property surname User's last name
     * @property employerCode Optional employer code for corporate users
     * @property hasEmployerCode Whether the user has an employer code
     */
    data class UserRegistrationData(
        val email: String,
        val password: String,
        val name: String,
        val surname: String,
        val employerCode: String?,
        val hasEmployerCode: Boolean
    )
    
    /**
     * Data class representing the API response for user registration.
     * Contains the result of the registration attempt.
     * 
     * @property success Whether the registration was successful
     * @property message Human-readable message about the result
     * @property userId Generated user ID if registration successful
     */
    data class RegistrationResponse(
        val success: Boolean,
        val message: String,
        val userId: String?
    )
    
    /**
     * Shows a password validation dialog with specific error message.
     * 
     * @param message The validation error message to display
     */
    private fun showPasswordValidationDialog(message: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Validation Error")
        builder.setMessage(message)
        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
        }
        val dialog: AlertDialog = builder.create()
        dialog.show()
    }
    
    /**
     * Shows an email verification waiting dialog.
     * Informs the user that they need to check their email for verification.
     */
    private fun showEmailVerificationDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Email Verification Required")
        builder.setMessage("Registration successful! Please check your email and click the verification link to activate your account. You can then log in with your credentials.")
        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
            // Navigate directly to main app since user is already authenticated
            val intent = Intent(this, CentralActivity::class.java)
            startActivity(intent)
            finish()
        }
        builder.setCancelable(false) // Prevent dismissing by clicking outside
        val dialog: AlertDialog = builder.create()
        dialog.show()
    }
}