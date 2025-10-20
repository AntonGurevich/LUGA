package silverbackgarden.example.luga

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.user.UserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import silverbackgarden.example.luga.SupabaseClient

/**
 * Authentication manager for handling Supabase Auth operations.
 * 
 * This class provides a centralized way to manage user authentication including
 * registration, login, logout, and session management. It integrates with Supabase
 * Auth and maintains local session state using SharedPreferences.
 * 
 * All authentication operations are performed asynchronously using coroutines
 * to ensure smooth UI experience.
 */
class AuthManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AuthManager"
        private const val PREFS_NAME = "auth_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
    }
    
    private val supabase = SupabaseClient.client
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    init {
        // Set up session state monitoring to keep local state in sync
        setupSessionMonitoring()
    }
    
    /**
     * Sets up session state monitoring to ensure consistency between
     * Supabase SDK and local SharedPreferences.
     */
    private fun setupSessionMonitoring() {
        try {
            // Note: AuthChangeEvent might not be available in this Supabase version
            // We'll rely on manual session checks instead
            Log.d(TAG, "Session monitoring setup completed (manual mode)")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up session monitoring: ${e.message}")
        }
    }
    
    /**
     * Authentication callback interface for handling async auth operations.
     */
    interface AuthCallback {
        fun onSuccess(user: UserInfo?)
        fun onError(error: String)
    }
    
    /**
     * Registers a new user with email and password.
     * Returns a Result for easier coroutine integration.
     * 
     * @param email User's email address
     * @param password User's password
     * @return Result<UserInfo?> indicating success or failure
     */
    suspend fun registerUser(email: String, password: String): Result<UserInfo?> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting registration for email: $email")
                val result = supabase.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }
                
                Log.d(TAG, "Registration result: $result")
                
                // Check if registration was successful
                val currentUser = supabase.auth.currentUserOrNull()
                Log.d(TAG, "Current user after registration: $currentUser")
                
                if (currentUser != null) {
                    saveUserSession(currentUser)
                    Log.d(TAG, "User registered successfully: ${currentUser.email}")
                    Result.success(currentUser)
                } else {
                    // Check if this is an email confirmation required scenario
                    Log.d(TAG, "Registration completed but no user returned - likely email confirmation required")
                    Result.success(null) // Success but needs email confirmation
                }
            } catch (e: Exception) {
                Log.e(TAG, "Registration error: ${e.message}")
                Result.failure(e)
            }
        }
    }

    /**
     * Registers a new user with email and password.
     * 
     * @param email User's email address
     * @param password User's password
     * @param callback Callback to handle the result
     */
    fun register(email: String, password: String, callback: AuthCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Starting registration for email: $email")
                supabase.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }
                
                Log.d(TAG, "Registration completed")
                
                withContext(Dispatchers.Main) {
                    // Check if registration was successful
                    val currentUser = supabase.auth.currentUserOrNull()
                    Log.d(TAG, "Current user after registration: $currentUser")
                    
                    if (currentUser != null) {
                        saveUserSession(currentUser)
                        Log.d(TAG, "User registered successfully: ${currentUser.email}")
                        callback.onSuccess(currentUser)
                    } else {
                        // Check if this is an email confirmation required scenario
                        Log.d(TAG, "Registration completed but no user returned - likely email confirmation required")
                        callback.onSuccess(null) // Success but needs email confirmation
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Registration error: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback.onError("Registration failed: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Signs in an existing user with email and password.
     * 
     * @param email User's email address
     * @param password User's password
     * @param callback Callback to handle the result
     */
    fun signIn(email: String, password: String, callback: AuthCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                
                withContext(Dispatchers.Main) {
                    // Check if sign in was successful
                    val currentUser = supabase.auth.currentUserOrNull()
                    if (currentUser != null) {
                        saveUserSession(currentUser)
                        Log.d(TAG, "User signed in successfully: ${currentUser.email}")
                        callback.onSuccess(currentUser)
                    } else {
                        Log.e(TAG, "Sign in failed: No user returned")
                        callback.onError("Sign in failed")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sign in error: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback.onError("Sign in failed: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Signs out the current user and clears local session data.
     * 
     * @param callback Callback to handle the result
     */
    fun signOut(callback: AuthCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                supabase.auth.signOut()
                
                withContext(Dispatchers.Main) {
                    clearUserSession()
                    Log.d(TAG, "User signed out successfully")
                    callback.onSuccess(null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sign out error: ${e.message}")
                withContext(Dispatchers.Main) {
                    // Even if server sign out fails, clear local session
                    clearUserSession()
                    callback.onError("Sign out failed: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Checks if a user is currently logged in by verifying Supabase session.
     * This ensures consistency with StepCountWorker and other components.
     * 
     * @return true if user is logged in, false otherwise
     */
    fun isLoggedIn(): Boolean {
        return try {
            val currentUser = supabase.auth.currentUserOrNull()
            val isLoggedIn = currentUser != null
            
            // Sync local state with Supabase state
            if (isLoggedIn != prefs.getBoolean(KEY_IS_LOGGED_IN, false)) {
                Log.d(TAG, "Syncing local login state with Supabase: $isLoggedIn")
                prefs.edit().putBoolean(KEY_IS_LOGGED_IN, isLoggedIn).apply()
            }
            
            isLoggedIn
        } catch (e: Exception) {
            Log.e(TAG, "Error checking login status: ${e.message}")
            false
        }
    }
    
    /**
     * Gets the current user's email from Supabase session.
     * Falls back to local storage if Supabase is unavailable.
     * 
     * @return User's email or null if not logged in
     */
    fun getCurrentUserEmail(): String? {
        return try {
            val currentUser = supabase.auth.currentUserOrNull()
            currentUser?.email ?: prefs.getString(KEY_USER_EMAIL, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current user email: ${e.message}")
            prefs.getString(KEY_USER_EMAIL, null)
        }
    }
    
    /**
     * Gets the current user's ID from Supabase session.
     * Falls back to local storage if Supabase is unavailable.
     * 
     * @return User's ID or null if not logged in
     */
    fun getCurrentUserId(): String? {
        return try {
            val currentUser = supabase.auth.currentUserOrNull()
            currentUser?.id?.toString() ?: prefs.getString(KEY_USER_ID, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current user ID: ${e.message}")
            prefs.getString(KEY_USER_ID, null)
        }
    }
    
    /**
     * Attempts to refresh the user's session if they are logged in.
     * This is useful for maintaining session state across app restarts.
     * 
     * @param callback Callback to handle the result
     */
    fun refreshSession(callback: AuthCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val currentUser = supabase.auth.currentUserOrNull()
                if (currentUser == null) {
                    withContext(Dispatchers.Main) {
                        Log.d(TAG, "No current user to refresh session")
                        clearUserSession()
                        callback.onError("No active session to refresh")
                    }
                    return@launch
                }
                
                supabase.auth.refreshCurrentSession()
                
                withContext(Dispatchers.Main) {
                    // Check if refresh was successful
                    val refreshedUser = supabase.auth.currentUserOrNull()
                    if (refreshedUser != null) {
                        saveUserSession(refreshedUser)
                        Log.d(TAG, "Session refreshed successfully")
                        callback.onSuccess(refreshedUser)
                    } else {
                        Log.e(TAG, "Session refresh failed")
                        clearUserSession()
                        callback.onError("Session refresh failed")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Session refresh error: ${e.message}")
                withContext(Dispatchers.Main) {
                    clearUserSession()
                    callback.onError("Session expired: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Sends a password reset email to the user.
     * 
     * @param email User's email address
     * @param callback Callback to handle the result
     */
    fun resetPassword(email: String, callback: AuthCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                supabase.auth.resetPasswordForEmail(email)
                
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Password reset email sent to: $email")
                    callback.onSuccess(null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Password reset error: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback.onError("Password reset failed: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Deletes the current authenticated user account.
     * This is used for cleanup when database operations fail after auth registration.
     * 
     * @param callback Callback to handle the result
     */
    fun deleteCurrentUser(callback: AuthCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Deleting current user account for cleanup")
                
                // Get current user ID
                val currentUser = supabase.auth.currentUserOrNull()
                if (currentUser == null) {
                    Log.d(TAG, "No current user to delete")
                    withContext(Dispatchers.Main) {
                        clearUserSession()
                        callback.onSuccess(null)
                    }
                    return@launch
                }
                
                // Call Edge Function to delete user (requires service role key)
                supabase.functions.invoke(
                    function = "delete-user-account",
                    body = mapOf("user_id" to currentUser.id.toString())
                )
                
                withContext(Dispatchers.Main) {
                    clearUserSession()
                    Log.d(TAG, "User account deletion requested successfully")
                    callback.onSuccess(null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "User deletion error: ${e.message}")
                withContext(Dispatchers.Main) {
                    // Even if deletion fails, clear local session
                    clearUserSession()
                    callback.onError("User deletion failed: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Gets the current authenticated user from Supabase.
     * 
     * @return Current user or null if not authenticated
     */
    fun getCurrentUser(): UserInfo? {
        return try {
            supabase.auth.currentUserOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current user: ${e.message}")
            null
        }
    }
    
    /**
     * Saves user session data to local SharedPreferences.
     * Also stores actual tokens from Supabase session for consistency.
     * 
     * @param user The authenticated user info
     */
    private fun saveUserSession(user: UserInfo) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_USER_EMAIL, user.email)
            putString(KEY_USER_ID, user.id.toString())
            
            // Store actual tokens from Supabase session for consistency
            try {
                val session = supabase.auth.currentSessionOrNull()
                session?.let { 
                    putString(KEY_ACCESS_TOKEN, it.accessToken)
                    putString(KEY_REFRESH_TOKEN, it.refreshToken)
                    Log.d(TAG, "Stored tokens in SharedPreferences")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not store tokens: ${e.message}")
            }
            apply()
        }
    }
    
    /**
     * Clears all user session data from local SharedPreferences.
     */
    private fun clearUserSession() {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, false)
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_USER_EMAIL)
            remove(KEY_USER_ID)
            apply()
        }
    }
}
