package silverbackgarden.example.luga

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import kotlin.Result

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

    /**
     * Called when the activity is first created.
     * Initializes the UI, sets up event listeners, and establishes database connection.
     * 
     * @param savedInstanceState Bundle containing the activity's previously saved state
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

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
     * Collects all form data, validates it, and saves both locally and to database.
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
        if (isEmailValid(email) && isPasswordValid(password)) {
            // Check if employer code is required but not provided
            if (hasEmployerCode && employerCode.isBlank()) {
                Toast.makeText(this, "Please enter the Employer code", Toast.LENGTH_SHORT).show()
                return
            }

            // Save user preferences locally in a single commit
            sharedPreferences.edit().apply {
                if (hasEmployerCode) {
                    putString("employer_name", "LUGA")  // Set employer name for corporate users
                }
                putString("email", email)
                putString("password", password)
                putString("name", name)
                putString("surname", surname)
                apply()
            }

                         // Show registration progress and proceed with API registration
             Toast.makeText(this, "Registering user...", Toast.LENGTH_SHORT).show()
             registerUserViaAPI(email, password, name, surname)
        } else {
            Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show()
        }
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
     * Placeholder method for user registration logic.
     * Currently not implemented but intended for future authentication system integration.
     * 
     * @param email The user's email address
     * @param password The user's password
     */
    private fun registerUser(email: String, password: String) {
        // TODO: Implement user registration logic here
        // This could include API calls to an authentication service
    }

         /**
      * Registers user via REST API instead of direct database insertion.
      * Uses the same pattern as StepCountWorker for consistency.
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
                     // Navigate to next screen or close activity
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
      * Makes the actual API call to register the user.
      * Uses the same Retrofit pattern as StepCountWorker.
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
}