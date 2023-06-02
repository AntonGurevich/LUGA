package silverbackgarden.example.luga

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

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

        loginEmailButton.setBackgroundColor(ContextCompat.getColor(this, R.color.background_teal))
        loginEmailButton.setTextColor(ContextCompat.getColor(this, R.color.luga_blue))

        loginGoogleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.background_teal))
        loginGoogleButton.setTextColor(ContextCompat.getColor(this, R.color.luga_blue))

        loginMicrosoftButton.setBackgroundColor(ContextCompat.getColor(this, R.color.background_teal))
        loginMicrosoftButton.setTextColor(ContextCompat.getColor(this, R.color.luga_blue))

        loginFacebookButton.setBackgroundColor(ContextCompat.getColor(this, R.color.background_teal))
        loginFacebookButton.setTextColor(ContextCompat.getColor(this, R.color.luga_blue))

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