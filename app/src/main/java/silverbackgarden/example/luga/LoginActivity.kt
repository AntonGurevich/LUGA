package silverbackgarden.example.luga

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import silverbackgarden.example.luga.R

class LoginActivity : AppCompatActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var createAccountButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        emailEditText = findViewById(R.id.login_email_edittext)
        passwordEditText = findViewById(R.id.login_password_edittext)
        loginButton = findViewById(R.id.login_button)
        createAccountButton = findViewById(R.id.register_button)

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()

            if (isEmailValid(email) && isPasswordValid(password)) {
                if (isUserRegistered(email, password)) {
                    // Perform login validation and authentication
                    // You can use Firebase Authentication or your preferred authentication method

                    // If login is successful, navigate to the profile screen
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

        createAccountButton.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    private fun isEmailValid(email: String): Boolean {
        return if (email.isEmpty()) {
            false
        } else {
            Patterns.EMAIL_ADDRESS.matcher(email).matches()
        }
    }

    private fun isPasswordValid(password: String): Boolean {
        return password.isNotEmpty()
    }

    private fun isUserRegistered(email: String, password: String): Boolean {
        val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val savedEmail = sharedPreferences.getString("email", null)
        val savedPassword = sharedPreferences.getString("password", null)

        return email == savedEmail && password == savedPassword
    }
}

