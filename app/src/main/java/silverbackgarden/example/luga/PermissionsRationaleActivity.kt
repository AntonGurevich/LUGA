package silverbackgarden.example.luga

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Required by Health Connect: a static screen explaining fitness-data usage, shown from
 * Health Connect's own permissions UI (via the androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE
 * intent declared in the manifest). Reuses the same content as PrivacyDisclosureActivity.
 */
class PermissionsRationaleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_disclosure)
        title = "Privacy"
    }
}
