package silverbackgarden.example.luga

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PreLoginActivity: AppCompatActivity() {


    private lateinit var loginEmailButton: Button
    private lateinit var loginGoogleButton: Button
    private lateinit var loginMicrosoftButton: Button
    private lateinit var loginFacebookButton: Button
    private lateinit var registerButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prelogin)

        loginEmailButton = findViewById(R.id.btnLoginEmail)
        loginGoogleButton = findViewById(R.id.btnLoginGoogle)
        loginMicrosoftButton = findViewById(R.id.btnLoginMicrosoft)
        loginFacebookButton = findViewById(R.id.btnLoginFacebook)
        registerButton = findViewById(R.id.btnRegister)

        loginEmailButton.setBackgroundColor()

        loginEmailButton.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        loginGoogleButton.setOnClickListener {
            Toast.makeText(this, "This capability is not supported in MVP", Toast.LENGTH_LONG).show()
        }

        loginMicrosoftButton.setOnClickListener {
            Toast.makeText(this, "This capability is not supported in MVP", Toast.LENGTH_LONG).show()
        }

        loginFacebookButton.setOnClickListener {
            Toast.makeText(this, "This capability is not supported in MVP", Toast.LENGTH_LONG).show()
        }

        registerButton.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }


    }
}