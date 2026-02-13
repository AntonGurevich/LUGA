package silverbackgarden.example.luga

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import android.os.Bundle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Main activity that serves as the app's entry point and splash screen.
 * 
 * This activity displays the main layout briefly (2 seconds) before
 * automatically transitioning to the LoginActivity. It provides a smooth
 * app launch experience and can be customized to show branding or
 * loading animations during the delay period.
 * 
 * The activity also handles deep links from email verification callbacks.
 * 
 * The activity uses Kotlin coroutines to handle the delayed transition
 * without blocking the main thread, ensuring a responsive user experience.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    /**
     * Called when the activity is first created.
     * Sets up the layout and schedules the transition to LoginActivity.
     * Handles deep links from email verification.
     * 
     * @param savedInstanceState Bundle containing the activity's previously saved state
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Handle deep link from email verification
        handleIntent(intent)

        // Add a delay to simulate a splash screen if desired
        // This creates a brief pause to show the main layout before moving to login
        lifecycleScope.launch {
            delay(2000) // Delay in milliseconds (2 seconds)
            
            // Navigate to login, passing along any verification info
            val loginIntent = Intent(this@MainActivity, LoginActivity::class.java)
            intent.data?.let { uri ->
                // Pass verification data to login activity if present
                loginIntent.data = uri
            }
            startActivity(loginIntent)
            finish() // Close this activity to prevent going back to it
        }
    }

    /**
     * Handles incoming intents, particularly deep links from email verification.
     */
    private fun handleIntent(intent: Intent?) {
        val data: Uri? = intent?.data
        if (data != null) {
            Log.d(TAG, "Deep link received: $data")
            
            // Check if this is an email verification callback
            if (data.scheme == "acteamity" && data.host == "auth") {
                Log.d(TAG, "Email verification callback detected")
                
                // Extract any verification tokens or parameters from the URL
                val accessToken = data.getQueryParameter("access_token")
                val type = data.getQueryParameter("type")
                
                if (type == "signup" || accessToken != null) {
                    // User has verified their email
                    Toast.makeText(this, "Email verification successful! Please log in.", Toast.LENGTH_LONG).show()
                    Log.d(TAG, "Email verification successful")
                }
            }
        }
    }

    /**
     * Called when a new intent is received (e.g., when app is opened from a deep link).
     */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }
}