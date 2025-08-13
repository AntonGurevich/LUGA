package silverbackgarden.example.luga

import android.app.Application
import android.content.Context
import android.content.SharedPreferences

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

    /**
     * Called when the application is first created.
     * Initializes global resources and sets up shared preferences.
     */
    override fun onCreate() {
        super.onCreate()
        // Initialize shared preferences with a private mode for app-specific storage
        sharedPref = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
    }
}