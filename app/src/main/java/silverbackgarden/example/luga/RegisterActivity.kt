package silverbackgarden.example.luga

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.*
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.util.*
import android.content.pm.PackageManager
import android.util.Log
import retrofit2.http.POST
import retrofit2.http.Body

class RegisterActivity : AppCompatActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var codeSwitch: Switch
    private lateinit var employerCodeEditText: EditText
    private lateinit var registerButton: Button
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var nameEditText: EditText
    private lateinit var surnameEditText: EditText
    private lateinit var googleSignInClient: GoogleSignInClient

    private val sharedPref by lazy { (applicationContext as? Acteamity)?.sharedPref }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        googleSignInClient = GoogleSignIn.getClient(this, GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().build())
        GoogleSignIn.getLastSignedInAccount(this)?.let { readStepCount(it) } ?: signIn()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), REQUEST_ACTIVITY_RECOGNITION_PERMISSION)
        }

        bindViews()
        setupListeners()
    }

    private fun signIn() {
        startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
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

        registerButton.setOnClickListener { register() }
    }

    private fun register() {
        val email = emailEditText.text.toString()
        val password = passwordEditText.text.toString()
        val name = nameEditText.text.toString()
        val surname = surnameEditText.text.toString()
        val hasEmployerCode = codeSwitch.isChecked
        val employerCode = employerCodeEditText.text.toString()
        val registrationDate = getCurrentDate()

        if (isEmailValid(email) && isPasswordValid(password)) {
            if (hasEmployerCode && employerCode.isBlank()) {
                Toast.makeText(this, "Please enter the Employer code", Toast.LENGTH_SHORT).show()
                return
            }
            sharedPref?.edit()?.apply {
                putString("email", email)
                putString("password", password)
                putString("name", name)
                putString("surname", surname)
                putString("employerCode", employerCode)
                putString("registrationDate", registrationDate)
                apply()
            }
            registerUserApi(email, password, name, surname, registrationDate)
            // Transition to CentralActivity
            val intent = Intent(this, CentralActivity::class.java)
            startActivity(intent)
            finish()
        } else {
            Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isEmailValid(email: String) = email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()

    private fun isPasswordValid(password: String) = password.isNotEmpty()

    data class ErrorResponse(@SerializedName("error") val error: String)

    private fun registerUserApi(email: String, password: String, name: String, surname: String, registrationDate: String) {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://20.0.164.108:3000")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(UserApi::class.java)
        val user = User(email, 1, registrationDate)

        CoroutineScope(Dispatchers.IO).launch {
            repeat(10) {
                try {
                    val response = api.registerUser(user)
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            Toast.makeText(this@RegisterActivity, "User registered successfully", Toast.LENGTH_SHORT).show()
                            return@withContext
                        } else {
                            val errorMessage = Gson().fromJson(response.errorBody()?.string(), ErrorResponse::class.java)?.error ?: "Registration failed"
                            Toast.makeText(this@RegisterActivity, errorMessage, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        val errorMessage = (e as? HttpException)?.response()?.errorBody()?.string()?.let { Gson().fromJson(it, ErrorResponse::class.java)?.error } ?: e.message ?: "Unknown error"
                        Toast.makeText(this@RegisterActivity, "Error: $errorMessage", Toast.LENGTH_LONG).show()
                    }
                }
                delay(1000)
            }
        }
    }

    private fun getCurrentDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            handleSignInResult(GoogleSignIn.getSignedInAccountFromIntent(data))
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            readStepCount(completedTask.getResult(ApiException::class.java))
        } catch (e: ApiException) {
            Log.w(TAG, "signInResult:failed code=" + e.statusCode)
        }
    }

    private fun readStepCount(account: GoogleSignInAccount) {
        // Make the API call to read the step count
    }

    companion object {
        const val RC_SIGN_IN = 9001
        private const val TAG = "RegisterActivity"
        private const val REQUEST_ACTIVITY_RECOGNITION_PERMISSION = 1002
    }

    interface UserApi {
        @POST("/api/register")
        suspend fun registerUser(@Body user: User): Response<Void>
    }

    data class User(val email: String, val connection_code: Int, val registration_date: String)
}