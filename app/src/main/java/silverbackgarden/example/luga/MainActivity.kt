package silverbackgarden.example.luga

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import android.os.Bundle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Add a delay to simulate a splash screen if desired
        lifecycleScope.launch {
            delay(2000) // Delay in milliseconds
            startActivity(Intent(this@MainActivity, LoginActivity::class.java))
            finish()
        }
    }
}