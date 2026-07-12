package silverbackgarden.example.luga.ui

import android.util.Log
import silverbackgarden.example.luga.CompanyRules
import silverbackgarden.example.luga.CompanyRulesRecord
import silverbackgarden.example.luga.SupabaseUserManager
import silverbackgarden.example.luga.UserCorpLink

/**
 * Session-scoped cache of the current user's employer wellness-token rules,
 * mirroring iOS AppState.companyRules. Loaded once per session (see
 * DashboardFragment.loadTokenData) and read by Dashboard/Stats/Tokens once
 * their real UI lands in later phases.
 */
object CompanyRulesCache {

    private const val TAG = "CompanyRulesCache"

    var current: CompanyRules? = null
        private set

    fun load(supabaseUserManager: SupabaseUserManager, uid: String, onLoaded: (CompanyRules?) -> Unit = {}) {
        supabaseUserManager.fetchUserCorpLink(uid, object : SupabaseUserManager.DatabaseCallback<UserCorpLink?> {
            override fun onSuccess(result: UserCorpLink?) {
                val corpuid = result?.corpuid
                if (corpuid == null) {
                    Log.d(TAG, "No employer link for uid=$uid; clearing cached company rules")
                    current = null
                    onLoaded(null)
                    return
                }
                supabaseUserManager.fetchCompanyRules(corpuid, object : SupabaseUserManager.DatabaseCallback<CompanyRulesRecord?> {
                    override fun onSuccess(result: CompanyRulesRecord?) {
                        current = result?.let { CompanyRules.from(it) }
                        Log.d(TAG, "Loaded company rules for corpuid=$corpuid: $current")
                        onLoaded(current)
                    }
                    override fun onError(error: String) {
                        Log.e(TAG, "Failed to load company rules for corpuid=$corpuid: $error")
                        onLoaded(current)
                    }
                })
            }
            override fun onError(error: String) {
                Log.e(TAG, "Failed to load user_corp_link for uid=$uid: $error")
                onLoaded(current)
            }
        })
    }
}
