package silverbackgarden.example.luga.ui

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import silverbackgarden.example.luga.Acteamity
import silverbackgarden.example.luga.R
import silverbackgarden.example.luga.ui.dashboard.DashboardFragment
import silverbackgarden.example.luga.ui.profile.ProfileFragment
import silverbackgarden.example.luga.ui.stats.StatsFragment
import silverbackgarden.example.luga.ui.tokens.TokenBreakdownFragment

/**
 * Host activity for the four main tabs (Dashboard / Stats / Tokens / Profile),
 * replacing CentralActivity as the app's post-login entry point.
 */
class MainTabActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        val appearancePrefs = getSharedPreferences(Acteamity.PREFS_APPEARANCE, Context.MODE_PRIVATE)
        if (appearancePrefs.getBoolean(Acteamity.KEY_PREFERS_HIGH_CONTRAST, false)) {
            setTheme(R.style.Theme_LUGA_HighContrast)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_tab)

        bottomNav = findViewById(R.id.bottomNavigation)

        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_dashboard -> DashboardFragment()
                R.id.nav_stats -> StatsFragment()
                R.id.nav_tokens -> TokenBreakdownFragment()
                R.id.nav_profile -> ProfileFragment()
                else -> return@setOnItemSelectedListener false
            }
            showFragment(fragment)
            true
        }

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_dashboard
        }
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.tabContainer, fragment)
            .commit()
    }

    /** Called by tabs that need to programmatically switch to the Profile tab. */
    fun switchToProfileTab() {
        bottomNav.selectedItemId = R.id.nav_profile
    }

    /** Called by tabs that need to programmatically switch to the Dashboard tab. */
    fun switchToDashboardTab() {
        bottomNav.selectedItemId = R.id.nav_dashboard
    }

    /** Called by tabs that need to programmatically switch to the Stats tab. */
    fun switchToStatsTab() {
        bottomNav.selectedItemId = R.id.nav_stats
    }

    /** Called by tabs that need to programmatically switch to the Tokens tab. */
    fun switchToTokensTab() {
        bottomNav.selectedItemId = R.id.nav_tokens
    }
}
