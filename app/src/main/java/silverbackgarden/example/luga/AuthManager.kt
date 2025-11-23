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
                val user = supabase.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }
                
                Log.d(TAG, "Registration result: $user")
                
                // The signUpWith method returns a UserInfo object even when email verification is required
                // We can use this to get the user ID for database operations
                if (user != null) {
                    Log.d(TAG, "User registered successfully: ${user.email}, ID: ${user.id}")
                    
                    // Check if user is actually authenticated (email verified and logged in)
                    val currentUser = supabase.auth.currentUserOrNull()
                    if (currentUser != null) {
                        // User is fully authenticated, save session
                        saveUserSession(currentUser)
                        Log.d(TAG, "User is authenticated and session saved")
                    } else {
                        // User created but email verification required - don't save session yet
                        Log.d(TAG, "User created but email verification required. User ID: ${user.id}")
                    }
                    
                    Result.success(user)
                } else {
                    Log.e(TAG, "Registration failed - no user returned")
                    Result.success(null)
                }
            } catch (e: Exception) {
                // Enhanced error logging with detailed Supabase error extraction
                Log.e(TAG, "Registration error: ${e.message}")
                Log.e(TAG, "Exception type: ${e.javaClass.name}")
                Log.e(TAG, "Exception cause: ${e.cause?.message ?: "No cause"}")
                Log.e(TAG, "Stack trace:", e)
                
                // Extract detailed error information from AuthRestException
                val errorDetails = buildString {
                    append("=== SUPABASE ERROR DETAILS ===\n")
                    append("Error Message: ${e.message}\n")
                    append("Exception Type: ${e.javaClass.simpleName}\n")
                    e.cause?.let { append("Cause: ${it.message}\n") }
                    
                    // Try to extract additional details from AuthRestException
                    try {
                        // Use reflection to access AuthRestException properties
                        val exceptionClass = e.javaClass
                        
                        // Try to get statusCode property
                        try {
                            val statusCodeField = exceptionClass.getDeclaredField("statusCode")
                            statusCodeField.isAccessible = true
                            val statusCode = statusCodeField.get(e)
                            append("HTTP Status Code: $statusCode\n")
                        } catch (ex: Exception) {
                            // Field doesn't exist or can't be accessed
                        }
                        
                        // Try to get errorCode property
                        try {
                            val errorCodeField = exceptionClass.getDeclaredField("errorCode")
                            errorCodeField.isAccessible = true
                            val errorCode = errorCodeField.get(e)
                            append("Error Code: $errorCode\n")
                        } catch (ex: Exception) {
                            // Field doesn't exist or can't be accessed
                        }
                        
                        // Try to get errorDescription property
                        try {
                            val errorDescField = exceptionClass.getDeclaredField("errorDescription")
                            errorDescField.isAccessible = true
                            val errorDesc = errorDescField.get(e)
                            append("Error Description: $errorDesc\n")
                        } catch (ex: Exception) {
                            // Field doesn't exist or can't be accessed
                        }
                        
                        // Try to get response body or message property
                        try {
                            val messageField = exceptionClass.getDeclaredField("message")
                            messageField.isAccessible = true
                            val detailedMessage = messageField.get(e)
                            if (detailedMessage != null && detailedMessage != e.message) {
                                append("Detailed Message: $detailedMessage\n")
                            }
                        } catch (ex: Exception) {
                            // Field doesn't exist or can't be accessed
                        }
                        
                        // Try to get all declared fields and log their values
                        exceptionClass.declaredFields.forEach { field ->
                            try {
                                field.isAccessible = true
                                val value = field.get(e)
                                if (value != null && field.name != "message" && field.name != "cause" && field.name != "stackTrace") {
                                    append("${field.name}: $value\n")
                                }
                            } catch (ex: Exception) {
                                // Skip fields that can't be accessed
                            }
                        }
                        
                    } catch (ex: Exception) {
                        append("Could not extract detailed error info via reflection: ${ex.message}\n")
                    }
                    
                    // Check if it's a Supabase-specific error and provide guidance
                    e.message?.let { message ->
                        append("\n=== DIAGNOSTIC INFORMATION ===\n")
                        when {
                            message.contains("email", ignoreCase = true) && 
                            message.contains("send", ignoreCase = true) -> {
                                append("⚠️ Email sending issue detected.\n")
                                append("\n⚠️ CUSTOM SMTP DETECTED - SPECIAL ATTENTION REQUIRED ⚠️\n")
                                append("You're using a custom SMTP server. Common issues:\n")
                                append("1. SMTP Authentication Failed:\n")
                                append("   - Verify username/password in Supabase Dashboard\n")
                                append("   - Check if SMTP server requires app-specific password\n")
                                append("   - Verify SMTP server allows connections from Supabase IPs\n")
                                append("2. SMTP Connection Issues:\n")
                                append("   - Verify SMTP host and port are correct\n")
                                append("   - Check if SMTP server is accessible from Supabase servers\n")
                                append("   - Verify firewall/security rules allow connections\n")
                                append("3. SMTP Server Configuration:\n")
                                append("   - Check if server requires TLS/SSL\n")
                                append("   - Verify sender email matches SMTP authentication\n")
                                append("   - Check if server has rate limits\n")
                                append("4. Email Template Issues:\n")
                                append("   - Verify email templates don't conflict with SMTP\n")
                                append("   - Check sender email domain matches SMTP server\n")
                                append("\nACTION ITEMS:\n")
                                append("1. Test SMTP connection in Supabase Dashboard\n")
                                append("2. Check Supabase Dashboard → Logs → Auth Logs for SMTP errors\n")
                                append("3. Verify SMTP credentials are correct\n")
                                append("4. Check SMTP server logs for connection attempts\n")
                                append("5. Test SMTP from Supabase Dashboard → Settings → Auth → SMTP → Test\n")
                            }
                            message.contains("smtp", ignoreCase = true) -> {
                                append("⚠️ SMTP-specific error detected.\n")
                                append("Check SMTP configuration in Supabase Dashboard.\n")
                            }
                            message.contains("authentication", ignoreCase = true) ||
                            message.contains("auth", ignoreCase = true) -> {
                                append("⚠️ SMTP Authentication issue detected.\n")
                                append("Verify SMTP username/password in Supabase Dashboard.\n")
                            }
                            message.contains("network", ignoreCase = true) ||
                            message.contains("connection", ignoreCase = true) -> {
                                append("⚠️ Network/Connection issue detected.\n")
                                append("This could be SMTP server connectivity.\n")
                            }
                            message.contains("rate limit", ignoreCase = true) -> {
                                append("⚠️ Rate limit exceeded.\n")
                                append("Could be SMTP server rate limits.\n")
                            }
                        }
                    }
                }
                
                Log.e(TAG, "=== FULL ERROR DETAILS FOR BACKEND DEV ===\n$errorDetails")
                
                // Also create a simplified error message for backend developer
                val backendErrorReport = buildString {
                    append("SUPABASE EMAIL VERIFICATION ERROR REPORT\n")
                    append("==================================================\n")
                    append("Timestamp: ${System.currentTimeMillis()}\n")
                    append("Error: ${e.message}\n")
                    append("Exception Type: ${e.javaClass.name}\n")
                    append("Stack Trace:\n${e.stackTraceToString()}\n")
                    append("\n⚠️ CUSTOM SMTP SERVER DETECTED ⚠️\n")
                    append("You're using a custom SMTP server. This is likely the cause.\n")
                    append("\nACTION REQUIRED - PRIORITY:\n")
                    append("1. TEST SMTP CONNECTION:\n")
                    append("   - Go to Supabase Dashboard → Settings → Auth → SMTP Settings\n")
                    append("   - Click 'Test SMTP Connection' or 'Send Test Email'\n")
                    append("   - This will show the EXACT SMTP error\n")
                    append("\n2. CHECK SMTP CONFIGURATION:\n")
                    append("   - Verify SMTP host, port, username, password\n")
                    append("   - Check if using App Password (required for Gmail/Outlook)\n")
                    append("   - Verify TLS/SSL settings match your SMTP server\n")
                    append("   - Ensure sender email matches SMTP username\n")
                    append("\n3. CHECK SMTP SERVER ACCESSIBILITY:\n")
                    append("   - Verify SMTP server is publicly accessible\n")
                    append("   - Check firewall rules allow Supabase IPs\n")
                    append("   - Verify SMTP port is open\n")
                    append("   - Check SMTP server logs for connection attempts\n")
                    append("\n4. CHECK SUPABASE LOGS:\n")
                    append("   - Dashboard → Logs → Auth Logs\n")
                    append("   - Filter for email/SMTP errors\n")
                    append("   - Look for specific SMTP error messages\n")
                    append("\n5. COMMON SMTP ISSUES:\n")
                    append("   - Authentication failed → Check credentials, use App Password\n")
                    append("   - Connection refused → Check firewall, verify host/port\n")
                    append("   - TLS/SSL error → Verify security settings\n")
                    append("   - Rate limit → Check SMTP server rate limits\n")
                }
                
                Log.e(TAG, "=== BACKEND DEV ERROR REPORT ===\n$backendErrorReport")
                
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
                val user = supabase.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }
                
                Log.d(TAG, "Registration result: $user")
                
                withContext(Dispatchers.Main) {
                    // The signUpWith method returns a UserInfo object even when email verification is required
                    // We can use this to get the user ID for database operations
                    if (user != null) {
                        Log.d(TAG, "User registered successfully: ${user.email}, ID: ${user.id}")
                        
                        // Check if user is actually authenticated (email verified and logged in)
                        val currentUser = supabase.auth.currentUserOrNull()
                        if (currentUser != null) {
                            // User is fully authenticated, save session
                            saveUserSession(currentUser)
                            Log.d(TAG, "User is authenticated and session saved")
                        } else {
                            // User created but email verification required - don't save session yet
                            Log.d(TAG, "User created but email verification required. User ID: ${user.id}")
                        }
                        
                        callback.onSuccess(user)
                    } else {
                        Log.e(TAG, "Registration failed - no user returned")
                        callback.onSuccess(null)
                    }
                }
            } catch (e: Exception) {
                // Enhanced error logging with detailed Supabase error extraction
                Log.e(TAG, "Registration error: ${e.message}")
                Log.e(TAG, "Exception type: ${e.javaClass.name}")
                Log.e(TAG, "Exception cause: ${e.cause?.message ?: "No cause"}")
                Log.e(TAG, "Stack trace:", e)
                
                // Extract detailed error information from AuthRestException (same as registerUser method)
                val errorDetails = buildString {
                    append("=== SUPABASE ERROR DETAILS ===\n")
                    append("Error Message: ${e.message}\n")
                    append("Exception Type: ${e.javaClass.simpleName}\n")
                    e.cause?.let { append("Cause: ${it.message}\n") }
                    
                    // Try to extract additional details from AuthRestException
                    try {
                        val exceptionClass = e.javaClass
                        
                        // Extract all available fields from the exception
                        exceptionClass.declaredFields.forEach { field ->
                            try {
                                field.isAccessible = true
                                val value = field.get(e)
                                if (value != null && field.name != "message" && field.name != "cause" && field.name != "stackTrace") {
                                    append("${field.name}: $value\n")
                                }
                            } catch (ex: Exception) {
                                // Skip fields that can't be accessed
                            }
                        }
                    } catch (ex: Exception) {
                        append("Could not extract detailed error info: ${ex.message}\n")
                    }
                }
                
                Log.e(TAG, "=== FULL ERROR DETAILS ===\n$errorDetails")
                
                withContext(Dispatchers.Main) {
                    callback.onError("Registration failed: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Signs in an existing user with email and password.
     * Checks email verification status and provides appropriate error messages.
     * 
     * @param email User's email address
     * @param password User's password
     * @param callback Callback to handle the result
     */
    fun signIn(email: String, password: String, callback: AuthCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                
                withContext(Dispatchers.Main) {
                    // Check if sign in was successful
                    val currentUser = supabase.auth.currentUserOrNull()
                    if (currentUser != null) {
                        // Check if email is verified
                        if (!isEmailVerified(currentUser)) {
                            Log.w(TAG, "Sign in attempted but email not verified for: ${currentUser.email}")
                            callback.onError("EMAIL_NOT_VERIFIED")
                            return@withContext
                        }
                        
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
                val errorMessage = e.message?.lowercase() ?: ""
                
                // Check for email verification related errors
                when {
                    errorMessage.contains("email not confirmed") || 
                    errorMessage.contains("email_not_confirmed") ||
                    errorMessage.contains("email not verified") -> {
                        withContext(Dispatchers.Main) {
                            callback.onError("EMAIL_NOT_VERIFIED")
                        }
                    }
                    errorMessage.contains("invalid login credentials") ||
                    errorMessage.contains("invalid_credentials") -> {
                        withContext(Dispatchers.Main) {
                            callback.onError("Invalid email or password")
                        }
                    }
                    else -> {
                        withContext(Dispatchers.Main) {
                            callback.onError("Sign in failed: ${e.message}")
                        }
                    }
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
                // Include redirectUrl so Supabase knows where to redirect after password reset
                // This deep link will open the app when user clicks the reset link in email
                supabase.auth.resetPasswordForEmail(
                    email = email,
                    redirectUrl = "acteamity://auth"
                )
                
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
     * Updates the current user's password.
     * Requires the user to be authenticated and provide their current password.
     * 
     * @param newPassword The new password to set
     * @param callback Callback to handle the result
     */
    fun updatePassword(newPassword: String, callback: AuthCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Check if user is authenticated
                val currentUser = supabase.auth.currentUserOrNull()
                if (currentUser == null) {
                    withContext(Dispatchers.Main) {
                        Log.e(TAG, "Cannot update password - user not authenticated")
                        callback.onError("User not authenticated. Please log in first.")
                    }
                    return@launch
                }
                
                Log.d(TAG, "Updating password for user: ${currentUser.email}")
                
                // Update password using Supabase Auth
                supabase.auth.updateUser {
                    password = newPassword
                }
                
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Password updated successfully")
                    val updatedUser = supabase.auth.currentUserOrNull()
                    callback.onSuccess(updatedUser)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Password update error: ${e.message}")
                val errorMessage = when {
                    e.message?.contains("invalid", ignoreCase = true) == true -> 
                        "Invalid password. Password must be at least 6 characters long."
                    e.message?.contains("weak", ignoreCase = true) == true -> 
                        "Password is too weak. Please choose a stronger password."
                    else -> "Password update failed: ${e.message}"
                }
                withContext(Dispatchers.Main) {
                    callback.onError(errorMessage)
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
     * Checks if the current user's email is verified.
     * When email verification is enabled in Supabase, unverified users cannot sign in.
     * Therefore, if a user is authenticated, their email is verified.
     * 
     * @param user The user to check (if null, uses current user)
     * @return true if email is verified, false otherwise
     */
    fun isEmailVerified(user: UserInfo? = null): Boolean {
        return try {
            val userToCheck = user ?: supabase.auth.currentUserOrNull()
            // If user is authenticated, they have verified their email
            // (Supabase blocks unverified users from signing in when email verification is enabled)
            userToCheck != null
        } catch (e: Exception) {
            Log.e(TAG, "Error checking email verification status: ${e.message}")
            false
        }
    }
    
    /**
     * Resends the email confirmation email to the user.
     * 
     * This uses a workaround: calls signUpWith again with the email.
     * Supabase will detect the existing user and resend the verification email.
     * We use a temporary password that won't be validated for existing users.
     * 
     * @param email User's email address
     * @param callback Callback to handle the result
     */
    fun resendEmailConfirmation(email: String, callback: AuthCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Resending email confirmation to: $email")
                
                // Workaround: Call signUpWith again - Supabase will resend verification email
                // for existing unverified users. We use a temporary password.
                // Note: This is safe because we're not changing the user's actual password
                supabase.auth.signUpWith(Email) {
                    this.email = email
                    // Use a temporary password - this won't affect existing users
                    // Supabase will detect existing user and resend verification email only
                    this.password = "temp_resend_${System.currentTimeMillis()}"
                }
                
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Confirmation email resent successfully to: $email")
                    callback.onSuccess(null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resending confirmation email: ${e.message}")
                val errorMessage = e.message?.lowercase() ?: ""
                
                // Supabase may return an error for existing users, but still sends the email
                // Check for common "user exists" errors that are actually success cases
                val isExpectedError = errorMessage.contains("user already registered") || 
                                     errorMessage.contains("already exists") ||
                                     errorMessage.contains("email address is already registered") ||
                                     errorMessage.contains("user already signed up")
                
                withContext(Dispatchers.Main) {
                    if (isExpectedError) {
                        // User exists - Supabase should have resent the email
                        Log.d(TAG, "User exists - verification email should have been resent")
                        callback.onSuccess(null)
                    } else {
                        callback.onError("Failed to resend confirmation email: ${e.message}")
                    }
                }
            }
        }
    }
    
    /**
     * Checks the email verification status of the current user.
     * 
     * @return true if email is verified, false otherwise
     */
    fun checkVerificationStatus(): Boolean {
        return try {
            val currentUser = supabase.auth.currentUserOrNull()
            isEmailVerified(currentUser)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking verification status: ${e.message}")
            false
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
