package silverbackgarden.example.luga
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import silverbackgarden.example.luga.R

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

        emailEditText = findViewById(R.id.register_email_edittext)
        passwordEditText = findViewById(R.id.register_password_edittext)
        codeSwitch = findViewById(R.id.code_switch)
        employerCodeEditText = findViewById(R.id.employer_code_edittext)
        registerButton = findViewById(R.id.register_button)
        nameEditText = findViewById(R.id.register_name_edittext)
        surnameEditText = findViewById(R.id.register_surname_edittext)

        sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)

        codeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                employerCodeEditText.visibility = View.VISIBLE
            } else {
                employerCodeEditText.visibility = View.GONE
            }
        }

        registerButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()
            val hasEmployerCode = codeSwitch.isChecked
            val employerCode = employerCodeEditText.text.toString()
            val name = nameEditText.text.toString()
            val surname = surnameEditText.text.toString()

            if (isEmailValid(email) && isPasswordValid(password)) {
                if (hasEmployerCode && employerCode.isBlank()) {
                    Toast.makeText(this, "Please enter the Employer code", Toast.LENGTH_SHORT).show()
                } else {
                    if (hasEmployerCode && employerCode.isNotEmpty()) {
                        // Save employer name in SharedPreferences
                        val editor = sharedPreferences.edit()
                        editor.putString("employer_name", "LUGA")
                        editor.apply()
                    }

                    // Save email and password in SharedPreferences
                    val editor = sharedPreferences.edit()
                    editor.putString("email", email)
                    editor.putString("password", password)
                    editor.putString("name", name)
                    editor.putString("surname", surname)
                    editor.apply()

                    // Proceed with registration
                    registerUser(email, password)
                }
            } else {
                Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show()
            }
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

    private fun registerUser(email: String, password: String) {
        // TODO: Implement user registration logic
        // You can add your logic here to register the user with the provided email and password

        // If registration is successful, navigate to the profile screen
        val intent = Intent(this, CentralActivity::class.java)
        startActivity(intent)
        finish()
    }
}

