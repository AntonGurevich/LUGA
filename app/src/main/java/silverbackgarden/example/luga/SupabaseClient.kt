package silverbackgarden.example.luga

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

/**
 * Supabase client configuration for the Acteamity app.
 * 
 * This object provides a singleton instance of the Supabase client with all necessary
 * modules configured for authentication, database operations, realtime subscriptions,
 * and file storage.
 * 
 * Credentials are securely loaded from BuildConfig, which reads from local.properties
 * to avoid exposing API keys in source code.
 */
object SupabaseClient {
    
    init {
        // Debug: Log the BuildConfig values to verify they're being read correctly
        android.util.Log.d("SupabaseClient", "SUPABASE_URL: ${BuildConfig.SUPABASE_URL}")
        android.util.Log.d("SupabaseClient", "SUPABASE_ANON_KEY: ${BuildConfig.SUPABASE_ANON_KEY.take(20)}...")
    }
    
    /**
     * Singleton Supabase client instance.
     * Configured with Auth, Postgrest, Realtime, Storage, and Functions modules.
     * Token refresh and session persistence are handled automatically by the SDK.
     */
    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
        install(Storage)
        install(Functions)
    }
}
