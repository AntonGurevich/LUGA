package silverbackgarden.example.luga

import android.content.Intent
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
 * The activity uses Kotlin coroutines to handle the delayed transition
 * without blocking the main thread, ensuring a responsive user experience.
 */
class MainActivity : AppCompatActivity() {

    /**
     * Called when the activity is first created.
     * Sets up the layout and schedules the transition to LoginActivity.
     * 
     * @param savedInstanceState Bundle containing the activity's previously saved state
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Add a delay to simulate a splash screen if desired
        // This creates a brief pause to show the main layout before moving to login
        lifecycleScope.launch {
            delay(2000) // Delay in milliseconds (2 seconds)
            startActivity(Intent(this@MainActivity, LoginActivity::class.java))
            finish() // Close this activity to prevent going back to it
        }
    }
}