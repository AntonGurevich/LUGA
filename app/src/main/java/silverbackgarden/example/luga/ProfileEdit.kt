package silverbackgarden.example.luga

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Profile editing activity that allows users to modify their profile information.
 * 
 * This activity provides an interface for users to update their personal details
 * including name, surname, and employer information. It loads existing data from
 * shared preferences and saves changes back to persistent storage.
 * 
 * The activity includes functionality for:
 * - Editing name and surname
 * - Changing or removing employer association
 * - Saving changes to shared preferences
 */
class ProfileEditActivity : AppCompatActivity() {

    // Data storage and UI elements
    private lateinit var sharedPreferences: SharedPreferences  // For storing user data
    private lateinit var nameEditText: EditText               // First name input field
    private lateinit var surnameEditText: EditText            // Last name input field
    private lateinit var saveButton: Button                   // Save changes button
    private lateinit var changeEmployerButton: Button         // Employer management button

    /**
     * Called when the activity is first created.
     * Initializes the UI, loads existing profile data, and sets up
     * event listeners for user interactions.
     * 
     * @param savedInstanceState Bundle containing the activity's previously saved state
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_edit)

        // Initialize shared preferences and UI elements
        sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        nameEditText = findViewById(R.id.edit_name_edittext)
        surnameEditText = findViewById(R.id.edit_surname_edittext)
        saveButton = findViewById(R.id.save_button)
        changeEmployerButton = findViewById(R.id.change_employer_button)

        // Retrieve the saved name and surname from SharedPreferences
        val name = sharedPreferences.getString("name", "")
        val surname = sharedPreferences.getString("surname", "")

        // Pre-populate the EditText fields with existing values
        nameEditText.setText(name)
        surnameEditText.setText(surname)

        // Set up employer change button click listener
        changeEmployerButton.setOnClickListener {
            showChangeEmployerDialog()
        }

        // Set up save button click listener
        saveButton.setOnClickListener {
            // Get the updated values from input fields
            val newName = nameEditText.text.toString()
            val newSurname = surnameEditText.text.toString()

            // Save the updated name and surname in SharedPreferences
            val editor = sharedPreferences.edit()
            editor.putString("name", newName)
            editor.putString("surname", newSurname)
            editor.apply()

            // Finish the activity and return to the profile screen
            finish()
        }
    }

    /**
     * Shows a dialog with options for managing employer association.
     * 
     * This method presents users with three options:
     * 1. Provide a new employer code (currently not implemented)
     * 2. Remove current employer association
     * 3. Cancel the operation
     */
    private fun showChangeEmployerDialog() {
        // Define the available options for employer management
        val options = arrayOf("Provide new employer code", "Remove employer", "Cancel")

        // Create and configure the dialog
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Change Employer")
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> {
                    // TODO: Handle providing new employer code
                    // This functionality is not yet implemented in the MVP
                }
                1 -> {
                    // Remove employer association by setting employer_name to null
                    val editor = sharedPreferences.edit()
                    editor.putString("employer_name", null)
                    editor.apply()
                }
                2 -> {
                    // Cancel option - do nothing and dismiss dialog
                }
            }
        }
        builder.show()
    }
}
