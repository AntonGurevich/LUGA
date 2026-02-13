package silverbackgarden.example.luga

import kotlinx.serialization.Serializable

/**
 * Data class representing a user in the users_registry table in Supabase.
 * 
 * This class matches the Supabase database schema with the following fields:
 * - uid: Primary key (uuid, default auth.uid())
 * - email: Unique email address (varchar)
 * - connection_code: Unique connection code (int8)
 * - registration_date: Registration date (date)
 * 
 * The class uses Kotlinx Serialization for easy conversion to/from JSON
 * when communicating with Supabase.
 */
@Serializable
data class UserData(
    val uid: String? = null,  // Primary key, UUID from auth.uid()
    val email: String,
    val connection_code: Long,
    val registration_date: String
)

/**
 * Data class for creating a new user in the users_registry table.
 * 
 * This class is used when inserting new users. The uid field should be set
 * to the Supabase Auth user ID to ensure the foreign key constraint is satisfied.
 */
@Serializable
data class CreateUserData(
    val uid: String? = null,  // User ID from Supabase Auth (required for foreign key)
    val email: String,
    val connection_code: Long,
    val registration_date: String
)

/**
 * Data class for checking if a user exists with specific email and connection code.
 * 
 * This class is used for duplicate checking before registration.
 */
@Serializable
data class UserExistsCheck(
    val email: String,
    val connection_code: Long
)

/**
 * Response data class for user existence check.
 * 
 * This class represents the response when checking if a user already exists.
 */
@Serializable
data class UserExistsResponse(
    val exists: Boolean,
    val user: UserData? = null
)


