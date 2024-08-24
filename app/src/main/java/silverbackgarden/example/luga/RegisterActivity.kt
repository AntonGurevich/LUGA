package silverbackgarden.example.luga

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.HttpException
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import android.util.Log


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
        val name = nameEditText.text.toString()
        val surname = surnameEditText.text.toString()
        val hasEmployerCode = codeSwitch.isChecked
        val employerCode = employerCodeEditText.text.toString()

        if (isEmailValid(email) && isPasswordValid(password)) {
            if (hasEmployerCode && employerCode.isBlank()) {
                Toast.makeText(this, "Please enter the Employer code", Toast.LENGTH_SHORT).show()
                return
            }

            // Register user via API
            registerUserApi(email, password, name, surname)
        } else {
            Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isEmailValid(email: String) = email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()

    private fun isPasswordValid(password: String) = password.isNotEmpty()


    // Define the error response data class
    data class ErrorResponse(
        @SerializedName("error") val error: String
    )

    private fun registerUserApi(email: String, password: String, name: String, surname: String) {

        val user_id = 1000.toString()
        val employer_id = "AC"
        val email = "AG@AC.com"
        val password = 12345.toString()
        val connection_code = 1
        val retrofit = Retrofit.Builder()
            .baseUrl("http://20.0.164.108:3000/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(UserApi::class.java)
        val user = User(user_id, employer_id,  email, password, connection_code)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = api.registerUser(user)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@RegisterActivity, "User registered successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        // Log error response body
                        val errorBody = response.errorBody()?.string()
                        Log.e("RegisterActivity", "Error response body: $errorBody")

                        // Parse the error response
                        val errorResponse = errorBody?.let {
                            Gson().fromJson(it, ErrorResponse::class.java)
                        }
                        val errorMessage = errorResponse?.error ?: "Registration failed"
                        Toast.makeText(this@RegisterActivity, errorMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // Log the exception
                    Log.e("RegisterActivity", "Exception: ${e.message}", e)

                    val errorMessage = when (e) {
                        is HttpException -> {
                            val errorBody = e.response()?.errorBody()?.string()
                            Log.e("RegisterActivity", "HTTP exception error body: $errorBody")

                            val errorResponse = errorBody?.let {
                                Gson().fromJson(it, ErrorResponse::class.java)
                            }
                            errorResponse?.error ?: e.message()
                        }
                        else -> e.message ?: "Unknown error"
                    }
                    Toast.makeText(this@RegisterActivity, "Error: $errorMessage", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    interface UserApi {
        @POST("/api/register")
        suspend fun registerUser(@Body user: User): retrofit2.Response<Void>
    }

    data class User(val user_id: String, val employer_id: String, val email: String, val password: String, val connection_code: Int)
}

