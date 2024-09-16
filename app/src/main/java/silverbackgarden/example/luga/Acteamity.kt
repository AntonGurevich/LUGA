package silverbackgarden.example.luga

import android.app.Application
import android.content.Context
import android.content.SharedPreferences

class Acteamity : Application() {
    lateinit var sharedPref: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        sharedPref = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
    }
}