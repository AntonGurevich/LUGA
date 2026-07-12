package silverbackgarden.example.luga.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient

/**
 * Wraps Health Connect's availability check + the install/update prompt for when it's
 * missing (Android 13 and below only — on Android 14+ Health Connect is built into the OS
 * and this path is never hit). See Phase HC-1 of the Fit → Health Connect migration plan.
 */
object HealthConnectAvailability {

    const val PROVIDER_PACKAGE_NAME = "com.google.android.apps.healthdata"

    enum class Status { AVAILABLE, NOT_INSTALLED, UPDATE_REQUIRED }

    fun check(context: Context): Status {
        return when (HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE_NAME)) {
            HealthConnectClient.SDK_AVAILABLE -> Status.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Status.UPDATE_REQUIRED
            else -> Status.NOT_INSTALLED
        }
    }

    /**
     * Deep-links into the Health Connect app's Play Store listing (with its own onboarding
     * flow attached), for prompting install or update.
     */
    fun installOrUpdateIntent(context: Context): Intent {
        val uri = Uri.parse(
            "market://details?id=$PROVIDER_PACKAGE_NAME&url=healthconnect%3A%2F%2Fonboarding"
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setPackage("com.android.vending")
            data = uri
            putExtra("overlay", true)
            putExtra("callerId", context.packageName)
        }
    }
}
