package silverbackgarden.example.luga

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Static privacy disclosure screen (Phase 8), ported from iOS PrivacyDisclosureView.
 * Explains what activity data is read, how it's used, and how the user stays in control.
 */
class PrivacyDisclosureActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_disclosure)
        title = "Privacy"
    }
}
