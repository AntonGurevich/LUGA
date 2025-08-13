package silverbackgarden.example.luga

/**
 * Data class representing a training session in the app.
 * 
 * This class is used to store and manage training session data,
 * including the session identifier, name, and duration. It serves
 * as a data model for the training database and UI components.
 * 
 * @property id Unique identifier for the training session (auto-generated)
 * @property name Name or description of the training session
 * @property duration Duration of the training session in minutes
 */
data class Training(val id: Int, val name: String, val duration: Int)

