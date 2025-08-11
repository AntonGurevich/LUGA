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
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Properties
import android.content.SharedPreferences

class RegisterActivity : AppCompatActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var codeSwitch: Switch
    private lateinit var employerCodeEditText: EditText
    private lateinit var registerButton: Button
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var nameEditText: EditText
    private lateinit var surnameEditText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        bindViews()
        setupListeners()
        getConnection()
    }

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

    private fun setupListeners() {
        codeSwitch.setOnCheckedChangeListener { _, isChecked ->
            employerCodeEditText.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        registerButton.setOnClickListener {
            register()
        }
    }

    private fun register() {
        val email = emailEditText.text.toString()
        val password = passwordEditText.text.toString()
        val hasEmployerCode = codeSwitch.isChecked
        val employerCode = employerCodeEditText.text.toString()
        val name = nameEditText.text.toString()
        val surname = surnameEditText.text.toString()

        if (isEmailValid(email) && isPasswordValid(password)) {
            if (hasEmployerCode && employerCode.isBlank()) {
                Toast.makeText(this, "Please enter the Employer code", Toast.LENGTH_SHORT).show()
                return
            }

            // Save preferences in a single commit
            sharedPreferences.edit().apply {
                if (hasEmployerCode) {
                    putString("employer_name", "LUGA")
                }
                putString("email", email)
                putString("password", password)
                putString("name", name)
                putString("surname", surname)
                apply()
            }

            //registerUser(email, password)

            Toast.makeText(this, "Pre-insert", Toast.LENGTH_SHORT).show()
            insertPerson(email, password, name, surname)
        } else {
            Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isEmailValid(email: String) = email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()

    private fun isPasswordValid(password: String) = password.isNotEmpty()

    private fun registerUser(email: String, password: String) {
        // Implement user registration logic here
    }

    private fun insertPerson(email: String, password: String, name: String, surname: String) = CoroutineScope(Dispatchers.IO).launch {
        runOnUiThread{
            Toast.makeText(this@RegisterActivity, "In-Insert", Toast.LENGTH_SHORT).show()
        }
        try {
            getConnection()?.use { connection ->
                val statement = connection.prepareStatement(
                    "INSERT INTO Persons2 " +
                            "(user_id, employer_id, effective_from, effective_to, email, password, connection_code) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)").apply {
                    setString(1, "1")
                    setString(2, "1")
                    setString(3, "1")
                    setString(4, "1")
                    setString(5, "1")
                    setString(6, "1")
                    setString(7, "1")
                    executeUpdate()
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RegisterActivity, "User registered successfully", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: SQLException) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@RegisterActivity, "Database error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getConnection(): Connection? {
        runOnUiThread{
            Toast.makeText(this@RegisterActivity, "In-CONNECTION", Toast.LENGTH_SHORT).show()
        }
        val connectionProps = Properties().apply {
            put("user", "acuser02")
            put("password", "!suka-password-19-48")
        }
        return try {
            DriverManager.getConnection("jdbc:mysql://20.0.164.108:3306/acdbdev", connectionProps)
        } catch (e: SQLException) {
            null
        }
    }
}