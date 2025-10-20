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
 * IMPORTANT: Replace the placeholder values with your actual Supabase project credentials:
 * - SUPABASE_URL: Your project URL from Supabase dashboard
 * - SUPABASE_ANON_KEY: Your anonymous public key from Supabase dashboard
 */
object SupabaseClient {
    
    // Supabase project credentials
    private const val SUPABASE_URL = "https://ipdxcbuvvlshkzgycrer.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImlwZHhjYnV2dmxzaGt6Z3ljcmVyIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTgwOTQzMjcsImV4cCI6MjA3MzY3MDMyN30.-PjdKIAWllqNZpVcgju9zI7-M2M8arkwI4b3SZrOtbs"
    
    /**
     * Singleton Supabase client instance.
     * Configured with Auth, Postgrest, Realtime, Storage, and Functions modules.
     * Token refresh and session persistence are handled automatically by the SDK.
     */
    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
        install(Storage)
        install(Functions)
    }
}
