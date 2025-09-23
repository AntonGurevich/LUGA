package silverbackgarden.example.luga

import android.util.Log
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Database manager for handling user operations with Supabase.
 * 
 * This class provides methods to interact with the Users2 table in Supabase,
 * including user registration, duplicate checking, and data retrieval.
 * 
 * All database operations are performed asynchronously using coroutines
 * to ensure smooth UI experience.
 */
class SupabaseUserManager {
    
    companion object {
        private const val TAG = "SupabaseUserManager"
        private const val USERS_TABLE = "Users2"
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
                    Log.d(TAG, "Found user: UID=${user.UID}, email=${user.email}, connection_code=${user.connection_code}")
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
     * Registers a new user in the Users2 table using the Supabase Auth user ID.
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
                    withContext(Dispatchers.Main) {
                        callback.onSuccess(existingUser)
                    }
                    return@launch
                }
                
                val registrationDate = getCurrentDateString()
                
                // Convert Auth UID to a unique Long value for the database
                val uidLong = generateUniqueUID(authUserId)
                Log.d(TAG, "Generated UID: $uidLong from Auth UID: $authUserId")
                
                val newUser = CreateUserData(
                    UID = uidLong,
                    email = email,
                    connection_code = connectionCode,
                    registration_date = registrationDate
                )
                
                // Insert the user
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
     * Gets all users from the Users2 table.
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
                    Log.d(TAG, "DEBUG: User $index: UID=${user.UID}, email=${user.email}, connection_code=${user.connection_code}, registration_date=${user.registration_date}")
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
}
