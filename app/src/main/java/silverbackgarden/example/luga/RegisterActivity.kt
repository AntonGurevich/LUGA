package silverbackgarden.example.luga

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Legacy entry point kept only as a redirect shim: some old intents/deep links may still
 * target RegisterActivity, so it forwards straight to the current 2-step registration flow.
 */
class RegisterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, RegisterStep1Activity::class.java))
        finish()
    }
}
