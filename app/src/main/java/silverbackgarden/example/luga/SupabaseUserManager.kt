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
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

/**
 * Database manager for handling user operations with Supabase.
 * 
 * This class provides methods to interact with the users_registry table in Supabase,
 * including user registration, duplicate checking, and data retrieval.
 * 
 * All database operations are performed asynchronously using coroutines
 * to ensure smooth UI experience.
 */
class SupabaseUserManager {
    
    companion object {
        private const val TAG = "SupabaseUserManager"
        private const val USERS_TABLE = "users_registry"
        private const val STEPS_TABLE = "raw_steps"
        private const val BIKE_TABLE = "raw_bike"
        private const val SWIM_TABLE = "raw_swim"
        private const val COMPANY_USER_REGISTRY_TABLE = "dmp_company_user_registry"
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
                    Log.d(TAG, "Found user: uid=${user.uid}, email=${user.email}, connection_code=${user.connection_code}")
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
     * Registers a new user in the users_registry table using the Supabase Auth user ID.
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
                
                val newUser = CreateUserData(
                    uid = authUserId,  // Always use authenticated user's UID from auth (required for RLS policy: uid = auth.uid())
                    email = email,
                    connection_code = connectionCode,
                    registration_date = registrationDate
                )
                
                // Insert the user with the authenticated user's UID
                // IMPORTANT: authUserId must match auth.uid() for RLS policies to work
                Log.d(TAG, "Attempting to insert user with authenticated UID: $newUser")
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
     * Gets all users from the users_registry table.
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
                    Log.d(TAG, "DEBUG: User $index: uid=${user.uid}, email=${user.email}, connection_code=${user.connection_code}, registration_date=${user.registration_date}")
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
                    // Always use the authenticated user's UID from auth (required for RLS policies)
                    val registrationDate = getCurrentDateString()
                    
                    val newUser = CreateUserData(
                        uid = currentUser.id.toString(),  // Always use auth.uid() - required for RLS policy
                        email = email,
                        connection_code = connectionCode,
                        registration_date = registrationDate
                    )
                    
                    Log.d(TAG, "Inserting new user into database with authenticated UID: $newUser")
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

    // ================================
    // STEP DATA MANAGEMENT METHODS
    // ================================
    
    /**
     * Checks if step data exists for a specific user and date.
     * 
     * @param userUid User's UID from Supabase Auth
     * @param date Date string in "yyyy-MM-dd" format
     * @param callback Callback to handle the result (true if exists, false if not)
     */
    fun checkStepDataExists(userUid: String, date: String, callback: DatabaseCallback<Boolean>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Log.d(TAG, "Checking if step data exists for user: $userUid, date: $date")
                
                val result = supabase.from(STEPS_TABLE)
                    .select()
                    .decodeList<StepData>()
                
                val existingRecord = result.find { 
                    it.uid == userUid && it.date == date 
                }
                
                withContext(Dispatchers.Main) {
                    val exists = existingRecord != null
                    // Log.d(TAG, "Step data exists for $userUid on $date: $exists")
                    callback.onSuccess(exists)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking step data existence: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback.onError("Failed to check step data: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Inserts step data for a specific user and date.
     * 
     * @param userUid User's UID from Supabase Auth
     * @param date Date string in "yyyy-MM-dd" format
     * @param steps Number of steps for the day
     * @param callback Callback to handle the result
     */
    fun insertStepData(userUid: String, date: String, steps: Int, callback: DatabaseCallback<StepData>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Log.d(TAG, "Inserting step data for user: $userUid, date: $date, steps: $steps")
                
                val newStepData = CreateStepData(
                    uid = userUid,
                    date = date,
                    steps = steps
                )
                
                supabase.from(STEPS_TABLE)
                    .insert(newStepData)
                
                // Fetch the inserted record to return it
                val insertedRecords = supabase.from(STEPS_TABLE)
                    .select()
                    .decodeList<StepData>()
                
                val result = insertedRecords.find { 
                    it.uid == userUid && it.date == date 
                }
                
                if (result != null) {
                    withContext(Dispatchers.Main) {
                        Log.d(TAG, "Step data inserted successfully: $result")
                        callback.onSuccess(result)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Log.e(TAG, "Step data was inserted but could not be retrieved")
                        callback.onError("Insert completed but could not retrieve step data")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error inserting step data: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback.onError("Failed to insert step data: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Checks and inserts step data for multiple dates if they don't exist.
     * This is the main method used by the background worker.
     * Uses upsert strategy to handle race conditions and concurrent workers.
     * 
     * @param userUid User's UID from Supabase Auth
     * @param stepReports List of step reports to check and potentially insert
     * @param callback Callback to handle the result (returns count of inserted records)
     */
    fun syncStepData(userUid: String, stepReports: List<StepDataReport>, callback: DatabaseCallback<Int>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Syncing step data for user: $userUid, reports: ${stepReports.size}")
                
                var insertedCount = 0
                
                // Get all existing step data for this user to check in bulk
                val existingRecords = try {
                    supabase.from(STEPS_TABLE)
                        .select()
                        .decodeList<StepData>()
                        .filter { it.uid == userUid }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not fetch existing records, proceeding with upsert: ${e.message}")
                    emptyList()
                }
                
                Log.d(TAG, "Found ${existingRecords.size} existing step records for user")
                
                stepReports.forEach { report ->
                    val existingRecord = existingRecords.find { it.date == report.date }
                    
                    if (existingRecord == null) {
                        // Record doesn't exist, try to insert it with upsert logic
                        Log.d(TAG, "Attempting to upsert step data: ${report.date} - ${report.steps} steps")
                        
                        val success = upsertStepData(userUid, report.date, report.steps)
                        if (success) {
                            insertedCount++
                        }
                    } else if (existingRecord.steps < report.steps) {
                        // Current DB value is less than new app value, update it
                        Log.d(TAG, "Updating step data for ${report.date}: ${existingRecord.steps} -> ${report.steps} steps")
                        
                        val success = updateStepData(userUid, report.date, report.steps)
                        if (success) {
                            insertedCount++
                        }
                    } else {
                        // Log.d(TAG, "Step data for ${report.date} is up to date (DB: ${existingRecord.steps} >= App: ${report.steps})")
                    }
                }
                
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Step data sync completed. Inserted $insertedCount new records.")
                    callback.onSuccess(insertedCount)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing step data: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback.onError("Failed to sync step data: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Updates existing step data with new value.
     * 
     * @param userUid User's UID from Supabase Auth
     * @param date Date string in "yyyy-MM-dd" format
     * @param steps New number of steps for the day
     * @return True if the record was successfully updated
     */
    private suspend fun updateStepData(userUid: String, date: String, steps: Int): Boolean {
        return try {
            val updateData = CreateStepData(
                uid = userUid,
                date = date,
                steps = steps
            )
            
            supabase.from(STEPS_TABLE)
                .update(updateData) {
                    filter {
                        eq("uid", userUid)
                        eq("date", date)
                    }
                }
            
            Log.d(TAG, "Successfully updated step data: $date - $steps steps")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error updating step data for $date: ${e.message}")
            false
        }
    }

    /**
     * Safely inserts step data using upsert strategy to handle duplicates.
     * This method handles race conditions where multiple workers might try to insert the same record.
     * 
     * @param userUid User's UID from Supabase Auth
     * @param date Date string in "yyyy-MM-dd" format
     * @param steps Number of steps for the day
     * @return True if the record was successfully inserted, false if it already existed
     */
    private suspend fun upsertStepData(userUid: String, date: String, steps: Int): Boolean {
        return try {
            val newStepData = CreateStepData(
                uid = userUid,
                date = date,
                steps = steps
            )
            
            // Try to insert the record
            supabase.from(STEPS_TABLE)
                .insert(newStepData)
            
            Log.d(TAG, "Successfully inserted step data: $date - $steps steps")
            true
            
        } catch (e: Exception) {
            when {
                e.message?.contains("duplicate key") == true -> {
                    // Record already exists (race condition), this is expected
                    // Log.d(TAG, "Step data already exists for $date (race condition), continuing")
                    false
                }
                e.message?.contains("violates unique constraint") == true -> {
                    // Same as above, just different error message format
                    // Log.d(TAG, "Step data already exists for $date (unique constraint), continuing")
                    false
                }
                else -> {
                    // Unexpected error, log it but don't fail the entire sync
                    Log.e(TAG, "Unexpected error inserting step data for $date: ${e.message}")
                    false
                }
            }
        }
    }
    
    /**
     * Gets step data for a specific user and date range.
     * 
     * @param userUid User's UID from Supabase Auth
     * @param startDate Start date in "yyyy-MM-dd" format (inclusive)
     * @param endDate End date in "yyyy-MM-dd" format (inclusive)
     * @param callback Callback to handle the result
     */
    fun getStepDataRange(userUid: String, startDate: String, endDate: String, callback: DatabaseCallback<List<StepData>>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Getting step data for user: $userUid, range: $startDate to $endDate")
                
                val allRecords = supabase.from(STEPS_TABLE)
                    .select()
                    .decodeList<StepData>()
                
                val filteredRecords = allRecords.filter { 
                    it.uid == userUid && it.date >= startDate && it.date <= endDate 
                }
                
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Retrieved ${filteredRecords.size} step records for date range")
                    callback.onSuccess(filteredRecords)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error getting step data range: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback.onError("Failed to get step data: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Gets the current Supabase user UID.
     * Helper method for other components that need user identification.
     * 
     * @return User UID string or null if no user is signed in
     */
    fun getCurrentUserUid(): String? {
        return try {
            val currentUser = supabase.auth.currentUserOrNull()
            currentUser?.id?.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current user: ${e.message}")
            null
        }
    }
    
    /**
     * Fetches token data for the current user and month from the public.token_record2 table.
     * 
     * @param userId The user's UID
     * @param callback Callback to handle the token data result
     */
    fun fetchTokenData(userId: String, callback: DatabaseCallback<TokenRecord>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Fetching token data for user: $userId")
                
                // Get current month (first day of current month)
                val currentMonth = java.time.LocalDate.now().withDayOfMonth(1).toString()
                Log.d(TAG, "Current month: $currentMonth")
                
                // Fetch token data from public.token_record2
                val allTokenRecords = supabase.from("token_record2")
                    .select()
                    .decodeList<TokenRecord>()
                
                // Filter locally for the specific user and month
                val tokenData = allTokenRecords.find { 
                    it.uid == userId && it.month == currentMonth 
                }
                
                if (tokenData != null) {
                    Log.d(TAG, "Token data found: $tokenData")
                    withContext(Dispatchers.Main) {
                        callback.onSuccess(tokenData)
                    }
                } else {
                    // No token data found - this is normal for new users who haven't synced data yet
                    // Return a default TokenRecord with zeros instead of an error
                    Log.d(TAG, "No token data found for user $userId and month $currentMonth - returning default empty record")
                    val defaultTokenData = TokenRecord(
                        uid = userId,
                        corpuid = null,
                        month = currentMonth,
                        reimbursable_tokens = 0.0,
                        nonreimbursable_tokens = 0.0,
                        token_limit = 30.0, // Default token limit
                        swim_to_token = 0.0,
                        bike_to_token = 0.0,
                        steps_to_token = 0.0
                    )
                    withContext(Dispatchers.Main) {
                        callback.onSuccess(defaultTokenData)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching token data: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    callback.onError("Failed to fetch token data: ${e.message}")
                }
            }
        }
    }

    /**
     * Validates an employer connection code against the company user registry.
     * Checks if the code exists, is effective, and is not already used by another user.
     * 
     * @param connectionCode The employer connection code to validate
     * @param callback Callback to handle the validation result
     */
    fun validateEmployerCode(connectionCode: Long, callback: DatabaseCallback<EmployerCodeValidationResult>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Validating employer code: $connectionCode")
                
                // Step 1: Check if code exists in dmp_company_user_registry
                val companyRegistry = try {
                    supabase.from(COMPANY_USER_REGISTRY_TABLE)
                        .select()
                        .decodeList<CompanyUserRegistry>()
                        .find { it.connection_code == connectionCode }
                } catch (e: Exception) {
                    Log.e(TAG, "Error querying company registry: ${e.message}")
                    null
                }
                
                if (companyRegistry == null) {
                    Log.d(TAG, "Employer code $connectionCode not found in company registry")
                    withContext(Dispatchers.Main) {
                        callback.onSuccess(EmployerCodeValidationResult(
                            isValid = false,
                            errorMessage = "Invalid employer code. Please check your code and try again.",
                            companyInfo = null
                        ))
                    }
                    return@launch
                }
                
                // Step 2: Check if code is effective (current date is within effective period)
                val currentDate = java.time.LocalDate.now()
                val effectiveFrom = companyRegistry.effective_from?.let { 
                    try { java.time.LocalDate.parse(it) } catch (e: Exception) { null }
                }
                val effectiveTo = companyRegistry.effective_to?.let { 
                    try { java.time.LocalDate.parse(it) } catch (e: Exception) { null }
                }
                
                val isEffective = when {
                    effectiveFrom == null -> false // No effective date means not effective
                    effectiveTo == null -> currentDate.isAfter(effectiveFrom) || currentDate.isEqual(effectiveFrom) // NULL means no end date
                    else -> currentDate.isAfter(effectiveFrom) && currentDate.isBefore(effectiveTo) || 
                           currentDate.isEqual(effectiveFrom) || currentDate.isEqual(effectiveTo)
                }
                
                if (!isEffective) {
                    Log.d(TAG, "Employer code $connectionCode is not effective. From: $effectiveFrom, To: $effectiveTo, Current: $currentDate")
                    withContext(Dispatchers.Main) {
                        callback.onSuccess(EmployerCodeValidationResult(
                            isValid = false,
                            errorMessage = "This employer code is not currently active. Please contact your administrator.",
                            companyInfo = companyRegistry
                        ))
                    }
                    return@launch
                }
                
                // Step 3: Check if code is already used by another user in users_registry
                val existingUsers = try {
                    supabase.from(USERS_TABLE)
                        .select()
                        .decodeList<UserData>()
                        .filter { it.connection_code == connectionCode }
                } catch (e: Exception) {
                    Log.e(TAG, "Error querying users registry: ${e.message}")
                    emptyList()
                }
                
                if (existingUsers.isNotEmpty()) {
                    Log.d(TAG, "Employer code $connectionCode is already used by ${existingUsers.size} user(s)")
                    withContext(Dispatchers.Main) {
                        callback.onSuccess(EmployerCodeValidationResult(
                            isValid = false,
                            errorMessage = "This employer code is already in use by another user. Please contact your administrator.",
                            companyInfo = companyRegistry
                        ))
                    }
                    return@launch
                }
                
                // All validations passed
                Log.d(TAG, "Employer code $connectionCode is valid and available")
                withContext(Dispatchers.Main) {
                    callback.onSuccess(EmployerCodeValidationResult(
                        isValid = true,
                        errorMessage = null,
                        companyInfo = companyRegistry
                    ))
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error validating employer code: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback.onError("Failed to validate employer code: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Fetches the employer (company) name for a user by their auth UID.
     * Looks up the user's connection_code in users_registry, then gets company_name
     * from dmp_company_user_registry.
     *
     * @param userId The user's auth UID
     * @param callback Callback with company_name, or null if not found
     */
    fun fetchEmployerNameForUser(userId: String, callback: DatabaseCallback<String?>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Fetching employer name for user: $userId")
                val users = supabase.from(USERS_TABLE).select().decodeList<UserData>()
                val user = users.find { it.uid == userId }
                val connectionCode = user?.connection_code ?: run {
                    Log.d(TAG, "No user or connection_code for uid: $userId")
                    withContext(Dispatchers.Main) { callback.onSuccess(null) }
                    return@launch
                }
                val registry = supabase.from(COMPANY_USER_REGISTRY_TABLE)
                    .select()
                    .decodeList<CompanyUserRegistry>()
                    .find { it.connection_code == connectionCode }
                val companyName = registry?.company_name
                Log.d(TAG, "Employer for user $userId (code $connectionCode): $companyName")
                withContext(Dispatchers.Main) { callback.onSuccess(companyName) }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching employer for user: ${e.message}", e)
                withContext(Dispatchers.Main) { callback.onError("Failed to fetch employer: ${e.message}") }
            }
        }
    }
    
    // ================================
    // BIKE DATA MANAGEMENT METHODS
    // ================================
    
    /**
     * Syncs bike distance data to Supabase using upsert strategy.
     * 
     * @param userUid User's UID from Supabase Auth
     * @param bikeReports List of bike reports to check and potentially insert
     * @param callback Callback to handle the result (returns count of inserted records)
     */
    fun syncBikeData(userUid: String, bikeReports: List<BikeDataReport>, callback: DatabaseCallback<Int>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Syncing bike data for user: $userUid, reports: ${bikeReports.size}")
                
                var insertedCount = 0
                
                // Get all existing bike data for this user to check in bulk
                val existingRecords = try {
                    supabase.from(BIKE_TABLE)
                        .select()
                        .decodeList<BikeData>()
                        .filter { it.uid == userUid }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not fetch existing bike records, proceeding with upsert: ${e.message}")
                    emptyList()
                }
                
                Log.d(TAG, "Found ${existingRecords.size} existing bike records for user")
                
                bikeReports.forEach { report ->
                    val existingRecord = existingRecords.find { it.date == report.date }
                    val newValue = report.meters.toInt()
                    
                    if (existingRecord == null) {
                        // Record doesn't exist, try to insert it with upsert logic
                        Log.d(TAG, "Attempting to upsert bike data: ${report.date} - ${report.meters}m")
                        
                        val success = upsertBikeData(userUid, report.date, report.meters)
                        if (success) {
                            insertedCount++
                        }
                    } else if (existingRecord.m_per_day < newValue) {
                        // Current DB value is less than new app value, update it
                        Log.d(TAG, "Updating bike data for ${report.date}: ${existingRecord.m_per_day}m -> ${newValue}m")
                        
                        val success = updateBikeData(userUid, report.date, report.meters)
                        if (success) {
                            insertedCount++
                        }
                    } else {
                        // Log.d(TAG, "Bike data for ${report.date} is up to date (DB: ${existingRecord.m_per_day}m >= App: ${newValue}m)")
                    }
                }
                
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Bike data sync completed. Inserted $insertedCount new records.")
                    callback.onSuccess(insertedCount)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing bike data: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback.onError("Failed to sync bike data: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Safely inserts bike data using upsert strategy to handle duplicates.
     * 
     * @param userUid User's UID from Supabase Auth
     * @param date Date string in "yyyy-MM-dd" format
     * @param meters Distance in meters for the day
     * @return True if the record was successfully inserted, false if it already existed
     */
    private suspend fun upsertBikeData(userUid: String, date: String, meters: Float): Boolean {
        return try {
            val newBikeData = CreateBikeData(
                uid = userUid,
                date = date,
                m_per_day = meters.toInt()
            )
            
            supabase.from(BIKE_TABLE)
                .insert(newBikeData)
            
            Log.d(TAG, "Successfully inserted bike data: $date - ${meters}m")
            true
            
        } catch (e: Exception) {
            when {
                e.message?.contains("duplicate key") == true -> {
                    // Log.d(TAG, "Bike data already exists for $date (race condition), continuing")
                    false
                }
                e.message?.contains("violates unique constraint") == true -> {
                    // Log.d(TAG, "Bike data already exists for $date (unique constraint), continuing")
                    false
                }
                else -> {
                    Log.e(TAG, "Unexpected error inserting bike data for $date: ${e.message}")
                    false
                }
            }
        }
    }
    
    // ================================
    // SWIM DATA MANAGEMENT METHODS
    // ================================
    
    /**
     * Syncs swimming distance data to Supabase using upsert strategy.
     * 
     * @param userUid User's UID from Supabase Auth
     * @param swimReports List of swim reports to check and potentially insert
     * @param callback Callback to handle the result (returns count of inserted records)
     */
    fun syncSwimData(userUid: String, swimReports: List<SwimDataReport>, callback: DatabaseCallback<Int>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Syncing swim data for user: $userUid, reports: ${swimReports.size}")
                
                var insertedCount = 0
                
                // Get all existing swim data for this user to check in bulk
                val existingRecords = try {
                    supabase.from(SWIM_TABLE)
                        .select()
                        .decodeList<SwimData>()
                        .filter { it.uid == userUid }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not fetch existing swim records, proceeding with upsert: ${e.message}")
                    emptyList()
                }
                
                Log.d(TAG, "Found ${existingRecords.size} existing swim records for user")
                
                swimReports.forEach { report ->
                    val existingRecord = existingRecords.find { it.date == report.date }
                    val newValue = report.meters.toInt()
                    
                    if (existingRecord == null) {
                        // Record doesn't exist, try to insert it with upsert logic
                        Log.d(TAG, "Attempting to upsert swim data: ${report.date} - ${report.meters}m")
                        
                        val success = upsertSwimData(userUid, report.date, report.meters)
                        if (success) {
                            insertedCount++
                        }
                    } else if (existingRecord.m_per_day < newValue) {
                        // Current DB value is less than new app value, update it
                        Log.d(TAG, "Updating swim data for ${report.date}: ${existingRecord.m_per_day}m -> ${newValue}m")
                        
                        val success = updateSwimData(userUid, report.date, report.meters)
                        if (success) {
                            insertedCount++
                        }
                    } else {
                        // Log.d(TAG, "Swim data for ${report.date} is up to date (DB: ${existingRecord.m_per_day}m >= App: ${newValue}m)")
                    }
                }
                
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Swim data sync completed. Inserted $insertedCount new records.")
                    callback.onSuccess(insertedCount)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing swim data: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback.onError("Failed to sync swim data: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Updates existing bike data with new value.
     * 
     * @param userUid User's UID from Supabase Auth
     * @param date Date string in "yyyy-MM-dd" format
     * @param meters New distance in meters for the day
     * @return True if the record was successfully updated
     */
    private suspend fun updateBikeData(userUid: String, date: String, meters: Float): Boolean {
        return try {
            val updateData = CreateBikeData(
                uid = userUid,
                date = date,
                m_per_day = meters.toInt()
            )
            
            supabase.from(BIKE_TABLE)
                .update(updateData) {
                    filter {
                        eq("uid", userUid)
                        eq("date", date)
                    }
                }
            
            Log.d(TAG, "Successfully updated bike data: $date - ${meters}m")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error updating bike data for $date: ${e.message}")
            false
        }
    }
    
    /**
     * Updates existing swim data with new value.
     * 
     * @param userUid User's UID from Supabase Auth
     * @param date Date string in "yyyy-MM-dd" format
     * @param meters New distance in meters for the day
     * @return True if the record was successfully updated
     */
    private suspend fun updateSwimData(userUid: String, date: String, meters: Float): Boolean {
        return try {
            val updateData = CreateSwimData(
                uid = userUid,
                date = date,
                m_per_day = meters.toInt()
            )
            
            supabase.from(SWIM_TABLE)
                .update(updateData) {
                    filter {
                        eq("uid", userUid)
                        eq("date", date)
                    }
                }
            
            Log.d(TAG, "Successfully updated swim data: $date - ${meters}m")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error updating swim data for $date: ${e.message}")
            false
        }
    }

    /**
     * Safely inserts swim data using upsert strategy to handle duplicates.
     * 
     * @param userUid User's UID from Supabase Auth
     * @param date Date string in "yyyy-MM-dd" format
     * @param meters Distance in meters for the day
     * @return True if the record was successfully inserted, false if it already existed
     */
    private suspend fun upsertSwimData(userUid: String, date: String, meters: Float): Boolean {
        return try {
            val newSwimData = CreateSwimData(
                uid = userUid,
                date = date,
                m_per_day = meters.toInt()
            )
            
            supabase.from(SWIM_TABLE)
                .insert(newSwimData)
            
            Log.d(TAG, "Successfully inserted swim data: $date - ${meters}m")
            true
            
        } catch (e: Exception) {
            when {
                e.message?.contains("duplicate key") == true -> {
                    // Log.d(TAG, "Swim data already exists for $date (race condition), continuing")
                    false
                }
                e.message?.contains("violates unique constraint") == true -> {
                    // Log.d(TAG, "Swim data already exists for $date (unique constraint), continuing")
                    false
                }
                else -> {
                    Log.e(TAG, "Unexpected error inserting swim data for $date: ${e.message}")
                    false
                }
            }
        }
    }
}

/**
 * Data class for company user registry from dmp_company_user_registry table.
 */
@Serializable
data class CompanyUserRegistry(
    val user_corp_email: String,
    val corpuid: String,
    val company_name: String,
    val connection_code: Long,
    val user_name: String,
    val user_surname: String,
    val effective_from: String?,
    val effective_to: String?
)

/**
 * Data class for employer code validation result.
 */
    @Serializable
    data class EmployerCodeValidationResult(
        val isValid: Boolean,
        val errorMessage: String?,
        val companyInfo: CompanyUserRegistry?
    )

/**
 * Data class for step data from Supabase raw_steps table.
 */
@Serializable
data class StepData(
    val uid: String,
    val date: String,
    val steps: Int
)

/**
 * Data class for creating new step data records.
 */
@Serializable
data class CreateStepData(
    val uid: String,
    val date: String,
    val steps: Int
)

/**
 * Data class for step data reports used by the worker.
 */
@Serializable
data class StepDataReport(
    val date: String,
    val steps: Int
)

/**
 * Data class for bike distance data from Supabase raw_bike table.
 */
@Serializable
data class BikeData(
    val uid: String,
    val date: String,
    val m_per_day: Int
)

/**
 * Data class for creating new bike distance records.
 */
@Serializable
data class CreateBikeData(
    val uid: String,
    val date: String,
    val m_per_day: Int
)

/**
 * Data class for bike distance reports used by the worker.
 */
@Serializable
data class BikeDataReport(
    val date: String,
    val meters: Float
)

/**
 * Data class for swimming distance data from Supabase raw_swim table.
 */
@Serializable
data class SwimData(
    val uid: String,
    val date: String,
    val m_per_day: Int
)

/**
 * Data class for creating new swimming distance records.
 */
@Serializable
data class CreateSwimData(
    val uid: String,
    val date: String,
    val m_per_day: Int
)

/**
 * Data class for swimming distance reports used by the worker.
 */
@Serializable
data class SwimDataReport(
    val date: String,
    val meters: Float
)

/**
 * Data class for token record from Supabase public.token_record2 table.
 */
@Serializable
data class TokenRecord(
    val uid: String,
    val corpuid: String?,
    val month: String,
    val reimbursable_tokens: Double,
    val nonreimbursable_tokens: Double,
    val token_limit: Double?,
    val swim_to_token: Double?,
    val bike_to_token: Double?,
    val steps_to_token: Double?
)
