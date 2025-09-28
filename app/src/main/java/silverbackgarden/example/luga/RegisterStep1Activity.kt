package silverbackgarden.example.luga

import android.content.Intent
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_step1)

        authManager = AuthManager(this)
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
            proceedToStep2()
        }
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

    private fun proceedToStep2() {
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

        // Show progress
        continueButton.isEnabled = false
        continueButton.text = "Creating Account..."

        // Create Supabase Auth record
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = authManager.registerUser(email, password)
                
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        Toast.makeText(this@RegisterStep1Activity, "Account created successfully!", Toast.LENGTH_SHORT).show()
                        
                        // Pass email to step 2
                        val intent = Intent(this@RegisterStep1Activity, RegisterStep2Activity::class.java)
                        intent.putExtra("email", email)
                        startActivity(intent)
                        finish()
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Registration failed"
                        Toast.makeText(this@RegisterStep1Activity, "Registration failed: $error", Toast.LENGTH_LONG).show()
                        
                        // Re-enable button
                        continueButton.isEnabled = true
                        continueButton.text = "Continue"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RegisterStep1Activity, "Registration error: ${e.message}", Toast.LENGTH_LONG).show()
                    
                    // Re-enable button
                    continueButton.isEnabled = true
                    continueButton.text = "Continue"
                }
            }
        }
    }

    private fun isEmailValid(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun isPasswordValid(password: String): Boolean {
        return password.length >= 6
    }
}

