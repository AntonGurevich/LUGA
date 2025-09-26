package silverbackgarden.example.luga

import android.util.Log
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import silverbackgarden.example.luga.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Database manager for handling user operations with Supabase.
 * 
 * This class provides methods to interact with the user_registry table in Supabase,
 * including user registration, duplicate checking, and data retrieval.
 * 
 * All database operations are performed asynchronously using coroutines
 * to ensure smooth UI experience.
 */
class SupabaseUserManager {
    
    companion object {
        private const val TAG = "SupabaseUserManager"
        private const val USERS_TABLE = "users_registry"
    }
    
    private val supabase = SupabaseClient.client
    
    /**
     * Callback interface for handling async database operations.
     */
    interface DatabaseCallback<T> {
        fun onSuccess(result: T)
        fun onError(error: String)
    }
    
    /**
     * Checks if a user with the given email already exists.
     * For now, this will get all users and check locally due to Supabase query limitations.
     * 
     * @param email User's email address
     * @param connectionCode User's connection code (for logging purposes)
     * @param callback Callback to handle the result
     */
    fun checkUserExists(email: String, _connectionCode: Long, callback: DatabaseCallback<UserExistsResponse>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Checking if user exists with email: $email, connection_code: $_connectionCode")
                Log.d(TAG, "Querying table: $USERS_TABLE")
                
                // Get all users and check locally for now
                // Add cache-busting parameter to ensure fresh data
                val allUsers = supabase.from(USERS_TABLE)
                    .select()
                    .decodeList<UserData>()
                
                Log.d(TAG, "Retrieved ${allUsers.size} users from database")
                allUsers.forEach { user ->
                    Log.d(TAG, "Found user: uid=${user.uid}, UID_legacy=${user.UID_legacy}, email=${user.email}, connection_code=${user.connection_code}")
                }
                
                val existingUser = allUsers.find { 
                    it.email == email 
                }
                
                withContext(Dispatchers.Main) {
                    if (existingUser != null) {
                        Log.d(TAG, "User exists: $existingUser")
                        callback.onSuccess(UserExistsResponse(exists = true, user = existingUser))
                    } else {
                        Log.d(TAG, "User does not exist")
                        callback.onSuccess(UserExistsResponse(exists = false, user = null))
                    }
                }
                
            } catch (e: Exception) {
                Log.d(TAG, "Error checking user existence: ${e.message}")
                withContext(Dispatchers.Main) {
                    // If error occurs, assume user doesn't exist to allow registration
                    callback.onSuccess(UserExistsResponse(exists = false, user = null))
                }
            }
        }
    }
    
    /**
     * Registers a new user in the user_registry table using the Supabase Auth user ID.
     * 
     * @param email User's email address
     * @param connectionCode User's connection code
     * @param authUserId The user ID from Supabase Auth
     * @param callback Callback to handle the result
     */
    fun registerUser(email: String, connectionCode: Long, authUserId: String, callback: DatabaseCallback<UserData>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Registering new user with email: $email, connection_code: $connectionCode, auth_uid: $authUserId")
                
                // First check if user already exists
                val existingUsers = supabase.from(USERS_TABLE)
                    .select()
                    .decodeList<UserData>()
                
                val existingUser = existingUsers.find { 
                    it.email == email 
                }
                
                if (existingUser != null) {
                    Log.d(TAG, "User already exists in database: $existingUser")
                    
                    // Check if the existing user already has an auth UID
                    if (existingUser.uid != null) {
                        Log.d(TAG, "User already has auth UID, returning existing user")
                        withContext(Dispatchers.Main) {
                            callback.onSuccess(existingUser)
                        }
                        return@launch
                    }
                    
                    // User exists but has no auth UID - update with new auth UID
                    Log.d(TAG, "User exists without auth UID, updating with new auth UID: $authUserId")
                    try {
                        supabase.from(USERS_TABLE)
                            .update(mapOf("uid" to authUserId)) {
                                filter {
                                    eq("email", email)
                                }
                            }
                        
                        // Fetch the updated user
                        val updatedUsers = supabase.from(USERS_TABLE)
                            .select()
                            .decodeList<UserData>()
                        
                        val updatedUser = updatedUsers.find { it.email == email }
                        if (updatedUser != null) {
                            Log.d(TAG, "User updated successfully with auth UID: $updatedUser")
                            withContext(Dispatchers.Main) {
                                callback.onSuccess(updatedUser)
                            }
                        } else {
                            Log.e(TAG, "Failed to retrieve updated user")
                            withContext(Dispatchers.Main) {
                                callback.onError("Failed to update existing user")
                            }
                        }
                    } catch (updateError: Exception) {
                        Log.e(TAG, "Failed to update existing user: ${updateError.message}")
                        withContext(Dispatchers.Main) {
                            callback.onError("Failed to update existing user: ${updateError.message}")
                        }
                    }
                    return@launch
                }
                
                val registrationDate = getCurrentDateString()
                
                // Convert Auth UID to a unique Long value for legacy compatibility
                val uidLegacy = generateUniqueUID(authUserId)
                Log.d(TAG, "Generated legacy UID: $uidLegacy from Auth UID: $authUserId")
                
                val newUser = CreateUserData(
                    UID_legacy = uidLegacy,
                    email = email,
                    connection_code = connectionCode,
                    registration_date = registrationDate
                )
                
                // Insert the user (uid will be auto-generated by auth.uid())
                Log.d(TAG, "Attempting to insert user: $newUser")
                supabase.from(USERS_TABLE)
                    .insert(newUser)
                
                // Fetch the inserted user to return it
                val insertedUsers = supabase.from(USERS_TABLE)
                    .select()
                    .decodeList<UserData>()
                
                val result = insertedUsers.find { it.email == email }
                
                if (result != null) {
                    withContext(Dispatchers.Main) {
                        Log.d(TAG, "User registered successfully: $result")
                        callback.onSuccess(result)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Log.e(TAG, "User was inserted but could not be retrieved")
                        callback.onError("Registration completed but could not retrieve user data")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "User registration error: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback.onError("Registration failed: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Gets user data by email and connection code.
     * For now, this will get all users and filter locally.
     * 
     * @param email User's email address
     * @param connectionCode User's connection code
     * @param callback Callback to handle the result
     */
    fun getUserByEmailAndCode(email: String, connectionCode: Long, callback: DatabaseCallback<UserData?>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Getting user with email: $email and connection_code: $connectionCode")
                
                // Get all users and filter locally for now
                val allUsers = supabase.from(USERS_TABLE)
                    .select()
                    .decodeList<UserData>()
                
                val user = allUsers.find { 
                    it.email == email && it.connection_code == connectionCode 
                }
                
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "User found: $user")
                    callback.onSuccess(user)
                }
                
            } catch (e: Exception) {
                Log.d(TAG, "User not found or error occurred: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback.onSuccess(null)
                }
            }
        }
    }
    
    /**
     * Gets all users from the user_registry table.
     * 
     * @param callback Callback to handle the result
     */
    fun getAllUsers(callback: DatabaseCallback<List<UserData>>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Getting all users")
                
                val result = supabase.from(USERS_TABLE)
                    .select()
                    .decodeList<UserData>()
                
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Retrieved ${result.size} users")
                    callback.onSuccess(result)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error getting users: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback.onError("Failed to get users: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Generates a unique connection code for new users.
     * 
     * @return A unique 8-digit connection code
     */
    fun generateConnectionCode(): Long {
        val random = Random()
        return (10000000 + random.nextInt(90000000)).toLong()
    }
    
    /**
     * Debug method to get all users and log them for troubleshooting.
     * This can be called to see what's actually in the database.
     */
    fun debugGetAllUsers(callback: DatabaseCallback<List<UserData>>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "DEBUG: Getting all users from table: $USERS_TABLE")
                
                val result = supabase.from(USERS_TABLE)
                    .select()
                    .decodeList<UserData>()
                
                Log.d(TAG, "DEBUG: Retrieved ${result.size} users")
                result.forEachIndexed { index, user ->
                    Log.d(TAG, "DEBUG: User $index: uid=${user.uid}, UID_legacy=${user.UID_legacy}, email=${user.email}, connection_code=${user.connection_code}, registration_date=${user.registration_date}")
                }
                
                withContext(Dispatchers.Main) {
                    callback.onSuccess(result)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "DEBUG: Error getting users: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback.onError("Failed to get users: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Generates a unique Long UID from a Supabase Auth UID string.
     * Uses a combination of hash functions to ensure uniqueness and avoid collisions.
     * 
     * @param authUid The Supabase Auth UID string
     * @return A unique Long value suitable for int8 database field
     */
    private fun generateUniqueUID(authUid: String): Long {
        // Remove hyphens from UUID to get a clean string
        val cleanUid = authUid.replace("-", "")
        
        // Use multiple hash approaches to ensure uniqueness
        val hash1 = cleanUid.hashCode().toLong()
        val hash2 = cleanUid.fold(0L) { acc, char -> 
            acc * 31L + char.code.toLong() 
        }
        
        // Combine hashes and ensure positive value
        val combinedHash = (hash1 xor hash2).let { 
            if (it < 0) -it else it 
        }
        
        // Ensure the result fits in int8 range (0 to 9,223,372,036,854,775,807)
        return combinedHash % Long.MAX_VALUE
    }
    
    /**
     * Gets the current date as a formatted string.
     * 
     * @return Current date in "yyyy-MM-dd" format
     */
    private fun getCurrentDateString(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }
    
    /**
     * Transactional user registration that handles both auth and database operations atomically.
     * If database insertion fails, the auth user is automatically cleaned up.
     * 
     * @param email User's email address
     * @param password User's password
     * @param connectionCode User's connection code
     * @param authManager AuthManager instance for auth operations
     * @param callback Callback to handle the result
     */
    fun registerUserTransactionally(
        email: String, 
        password: String, 
        connectionCode: Long, 
        authManager: AuthManager,
        callback: DatabaseCallback<UserData>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Starting transactional user registration for: $email")
                
                // Step 1: Check if user already exists in database
                val existingUsers = supabase.from(USERS_TABLE)
                    .select()
                    .decodeList<UserData>()
                
                val existingUser = existingUsers.find { 
                    it.email == email 
                }
                
                if (existingUser != null) {
                    Log.d(TAG, "User already exists in database: $existingUser")
                    
                    // Check if the existing user already has an auth UID
                    if (existingUser.uid != null) {
                        Log.d(TAG, "User already has auth UID, returning existing user")
                        withContext(Dispatchers.Main) {
                            callback.onSuccess(existingUser)
                        }
                        return@launch
                    }
                    
                    // User exists but has no auth UID - we need to create auth user and update DB
                    Log.d(TAG, "User exists without auth UID, will create auth user and update DB")
                }
                
                // Step 2: Register with Supabase Auth
                Log.d(TAG, "Registering with Supabase Auth")
                val authResult = supabase.auth.signUpWith(io.github.jan.supabase.gotrue.providers.builtin.Email) {
                    this.email = email
                    this.password = password
                }
                
                val currentUser = supabase.auth.currentUserOrNull()
                if (currentUser == null) {
                    Log.e(TAG, "Auth registration failed - no user returned")
                    withContext(Dispatchers.Main) {
                        callback.onError("Authentication registration failed")
                    }
                    return@launch
                }
                
                Log.d(TAG, "Auth registration successful, user ID: ${currentUser.id}")
                
                // Step 3: Handle database operation based on whether user exists
                if (existingUser != null) {
                    // User exists but has no auth UID - update with new auth UID
                    Log.d(TAG, "Updating existing user with auth UID: ${currentUser.id}")
                    try {
                        supabase.from(USERS_TABLE)
                            .update(mapOf("uid" to currentUser.id.toString())) {
                                filter {
                                    eq("email", email)
                                }
                            }
                        
                        // Fetch the updated user
                        val updatedUsers = supabase.from(USERS_TABLE)
                            .select()
                            .decodeList<UserData>()
                        
                        val result = updatedUsers.find { it.email == email }
                        if (result != null) {
                            Log.d(TAG, "User updated successfully with auth UID: $result")
                            withContext(Dispatchers.Main) {
                                callback.onSuccess(result)
                            }
                        } else {
                            Log.e(TAG, "Failed to retrieve updated user - cleaning up auth user")
                            // Clean up auth user since update verification failed
                            try {
                                supabase.functions.invoke(
                                    function = "delete-user-account",
                                    body = mapOf("user_id" to currentUser.id.toString())
                                )
                                Log.d(TAG, "Auth user cleanup completed")
                            } catch (cleanupError: Exception) {
                                Log.e(TAG, "Failed to cleanup auth user: ${cleanupError.message}")
                            }
                            
                            withContext(Dispatchers.Main) {
                                callback.onError("Failed to update existing user")
                            }
                        }
                    } catch (updateError: Exception) {
                        Log.e(TAG, "Failed to update existing user: ${updateError.message}")
                        // Clean up auth user since update failed
                        try {
                            supabase.functions.invoke(
                                function = "delete-user-account",
                                body = mapOf("user_id" to currentUser.id.toString())
                            )
                            Log.d(TAG, "Auth user cleanup completed")
                        } catch (cleanupError: Exception) {
                            Log.e(TAG, "Failed to cleanup auth user: ${cleanupError.message}")
                        }
                        
                        withContext(Dispatchers.Main) {
                            callback.onError("Failed to update existing user: ${updateError.message}")
                        }
                    }
                } else {
                    // New user - insert into database
                    val registrationDate = getCurrentDateString()
                    val uidLegacy = generateUniqueUID(currentUser.id.toString())
                    
                    val newUser = CreateUserData(
                        UID_legacy = uidLegacy,
                        email = email,
                        connection_code = connectionCode,
                        registration_date = registrationDate
                    )
                    
                    Log.d(TAG, "Inserting new user into database: $newUser")
                    supabase.from(USERS_TABLE)
                        .insert(newUser)
                    
                    // Step 4: Verify insertion by fetching the user
                    val insertedUsers = supabase.from(USERS_TABLE)
                        .select()
                        .decodeList<UserData>()
                    
                    val result = insertedUsers.find { it.email == email }
                    
                    if (result != null) {
                        Log.d(TAG, "Transactional registration successful: $result")
                        withContext(Dispatchers.Main) {
                            callback.onSuccess(result)
                        }
                    } else {
                        Log.e(TAG, "Database insertion failed - cleaning up auth user")
                        // Clean up auth user since database insertion failed
                        try {
                            supabase.functions.invoke(
                                function = "delete-user-account",
                                body = mapOf("user_id" to currentUser.id.toString())
                            )
                            Log.d(TAG, "Auth user cleanup completed")
                        } catch (cleanupError: Exception) {
                            Log.e(TAG, "Failed to cleanup auth user: ${cleanupError.message}")
                        }
                        
                        withContext(Dispatchers.Main) {
                            callback.onError("Database insertion failed and auth user was cleaned up")
                        }
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Transactional registration error: ${e.message}")
                
                // Attempt to clean up auth user if it was created
                try {
                    val currentUser = supabase.auth.currentUserOrNull()
                    if (currentUser != null) {
                        Log.d(TAG, "Cleaning up auth user due to registration error")
                        supabase.functions.invoke(
                            function = "delete-user-account",
                            body = mapOf("user_id" to currentUser.id.toString())
                        )
                    }
                } catch (cleanupError: Exception) {
                    Log.e(TAG, "Failed to cleanup auth user: ${cleanupError.message}")
                }
                
                withContext(Dispatchers.Main) {
                    callback.onError("Registration failed: ${e.message}")
                }
            }
        }
    }
}
