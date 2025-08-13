package silverbackgarden.example.luga

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Pre-login activity that provides users with multiple authentication options.
 * 
 * This activity serves as a gateway where users can choose their preferred
 * login method. It displays buttons for different authentication providers
 * including email/password, Google, Microsoft, and Facebook, along with
 * a registration option for new users.
 * 
 * Currently, only email login and registration are fully implemented in the MVP.
 * Other authentication methods show placeholder messages indicating they're
 * not yet supported.
 */
class PreLoginActivity: AppCompatActivity() {

    // UI Elements - Login method selection buttons
    private lateinit var loginEmailButton: Button      // Traditional email/password login
    private lateinit var loginGoogleButton: Button     // Google Sign-In (MVP placeholder)
    private lateinit var loginMicrosoftButton: Button  // Microsoft authentication (MVP placeholder)
    private lateinit var loginFacebookButton: Button   // Facebook authentication (MVP placeholder)
    private lateinit var registerButton: Button        // New user registration

    /**
     * Called when the activity is first created.
     * Initializes the UI elements and sets up click listeners for all buttons.
     * 
     * @param savedInstanceState Bundle containing the activity's previously saved state
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prelogin)

        // Initialize all button references from the layout
        loginEmailButton = findViewById(R.id.btnLoginEmail)
        loginGoogleButton = findViewById(R.id.btnLoginGoogle)
        loginMicrosoftButton = findViewById(R.id.btnLoginMicrosoft)
        loginFacebookButton = findViewById(R.id.btnLoginFacebook)
        registerButton = findViewById(R.id.btnRegister)

        // Apply consistent styling to all login buttons
        // Set background color to teal and text color to blue for visual consistency
        loginEmailButton.setBackgroundColor(ContextCompat.getColor(this, R.color.background_teal))
        loginEmailButton.setTextColor(ContextCompat.getColor(this, R.color.luga_blue))

        loginGoogleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.background_teal))
        loginGoogleButton.setTextColor(ContextCompat.getColor(this, R.color.luga_blue))

        loginMicrosoftButton.setBackgroundColor(ContextCompat.getColor(this, R.color.background_teal))
        loginMicrosoftButton.setTextColor(ContextCompat.getColor(this, R.color.luga_blue))

        loginFacebookButton.setBackgroundColor(ContextCompat.getColor(this, R.color.background_teal))
        loginFacebookButton.setTextColor(ContextCompat.getColor(this, R.color.luga_blue))

        // Set up click listeners for all buttons
        loginEmailButton.setOnClickListener {
            // Navigate to traditional email/password login screen
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        loginGoogleButton.setOnClickListener {
            // Show placeholder message for Google authentication (not implemented in MVP)
            Toast.makeText(this, "This capability is not supported in MVP", Toast.LENGTH_LONG).show()
        }

        loginMicrosoftButton.setOnClickListener {
            // Show placeholder message for Microsoft authentication (not implemented in MVP)
            Toast.makeText(this, "This capability is not supported in MVP", Toast.LENGTH_LONG).show()
        }

        loginFacebookButton.setOnClickListener {
            // Show placeholder message for Facebook authentication (not implemented in MVP)
            Toast.makeText(this, "This capability is not supported in MVP", Toast.LENGTH_LONG).show()
        }

        registerButton.setOnClickListener {
            // Navigate to user registration screen for new users
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }
}