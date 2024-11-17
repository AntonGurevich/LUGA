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
import silverbackgarden.example.luga.R

class LoginActivity : AppCompatActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var createAccountButton: Button

    private lateinit var textView: TextView

    private lateinit var textView2: TextView


    private lateinit var deleteButton: Button

    private val sharedPref by lazy {
        (applicationContext as? Acteamity)?.sharedPref
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        emailEditText = findViewById(R.id.login_email_edittext)
        passwordEditText = findViewById(R.id.login_password_edittext)
        loginButton = findViewById(R.id.login_button)
        createAccountButton = findViewById(R.id.register_button)
        deleteButton = findViewById(R.id.delete_button)
        createAccountButton.visibility = View.GONE

        if (!isUserDataSaved()) {
            loginButton.visibility = View.GONE
            emailEditText.visibility = View.GONE
            passwordEditText.visibility = View.GONE
            createAccountButton.visibility = View.VISIBLE
            deleteButton.visibility = View.GONE
        }

        //textView = findViewById(R.id.textView)
        //textView2 = findViewById(R.id.textView2)


        val savedEmail = sharedPref?.getString("email", "no value")
        val savedPassword = sharedPref?.getString("password", "no value")

        //textView.setText(savedEmail)
        //textView2.setText(savedPassword)

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

        deleteButton.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Delete Account")
            builder.setMessage("Are you sure you want to delete your account?")

            builder.setPositiveButton("Yes") { dialog, _ ->
                val editor = sharedPref?.edit()
                editor?.remove("email")?.apply()
                editor?.remove("password")?.apply()
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
        val savedEmail = sharedPref?.getString("email", "no value")
        val savedPassword = sharedPref?.getString("password", "no value")
        return email == savedEmail && password == savedPassword
    }

    private fun isUserDataSaved(): Boolean {
        val savedEmail = sharedPref?.getString("email", null)
        val savedPassword = sharedPref?.getString("password", null)
        return !savedEmail.isNullOrEmpty() && !savedPassword.isNullOrEmpty()
    }
}

