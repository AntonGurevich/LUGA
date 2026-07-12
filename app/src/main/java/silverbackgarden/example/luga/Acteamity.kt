package silverbackgarden.example.luga

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

/**
 * Main Application class for the Acteamity app.
 *
 * This class extends Android's Application class and serves as the entry point
 * for the entire application. It initializes global resources and provides
 * access to shared preferences that can be used across all activities.
 *
 * The Application class is created before any other components and remains
 * active throughout the app's lifecycle, making it ideal for global initialization.
 */
class Acteamity : Application() {
    /**
     * Shared preferences instance for storing app-wide settings and data.
     * This provides persistent storage that survives app restarts and can be
     * accessed from any component in the application.
     */
    lateinit var sharedPref: SharedPreferences

    companion object {
        const val PREFS_APPEARANCE = "AppearancePrefs"
        const val KEY_PREFERS_DARK_MODE = "prefers_dark_mode"
        const val KEY_PREFERS_HIGH_CONTRAST = "prefers_high_contrast"
    }

    /**
     * Called when the application is first created.
     * Initializes global resources and sets up shared preferences.
     */
    override fun onCreate() {
        super.onCreate()
        // Initialize shared preferences with a private mode for app-specific storage
        sharedPref = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

        // Apply the saved Dark Mode preference (Phase 8 Appearance settings) before any
        // Activity is created, so it's consistent across the whole app from launch.
        val appearancePrefs = getSharedPreferences(PREFS_APPEARANCE, Context.MODE_PRIVATE)
        val prefersDarkMode = appearancePrefs.getBoolean(KEY_PREFERS_DARK_MODE, false)
        AppCompatDelegate.setDefaultNightMode(
            if (prefersDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}