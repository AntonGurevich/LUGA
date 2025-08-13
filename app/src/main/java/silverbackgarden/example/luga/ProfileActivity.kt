package silverbackgarden.example.luga

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Profile activity that displays user information and provides profile management options.
 * 
 * This activity shows the user's profile details including email, employer, name, and surname.
 * It provides navigation to profile editing, account deletion functionality, and a way to
 * return to the main app interface.
 * 
 * The activity retrieves user data from shared preferences and displays it in a user-friendly format.
 */
class ProfileActivity : AppCompatActivity() {

    // UI Elements for displaying user information
    private lateinit var emailTextView: TextView      // Displays user's email address
    private lateinit var employerTextView: TextView  // Displays user's employer name
    private lateinit var nameTextView: TextView      // Displays user's first name
    private lateinit var surnameTextView: TextView   // Displays user's last name

    /**
     * Shared preferences instance for accessing stored user data.
     * Uses lazy initialization to access the application's shared preferences.
     */
    private val sharedPref by lazy {
        (applicationContext as? Acteamity)?.sharedPref
    }

    /**
     * Called when the activity is first created.
     * Initializes the UI, loads user data from shared preferences,
     * and sets up navigation and action buttons.
     * 
     * @param savedInstanceState Bundle containing the activity's previously saved state
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // Initialize UI elements for displaying user information
        emailTextView = findViewById(R.id.email_textview)
        employerTextView = findViewById(R.id.employer_textview)

        // Retrieve user data from shared preferences
        val email = sharedPref?.getString("email", "")
        val employerName = sharedPref?.getString("employer_name", "")
        val name = sharedPref?.getString("name", null)
        val surname = sharedPref?.getString("surname", null)

        // Set up edit profile button to navigate to profile editing
        val editProfileButton: Button = findViewById(R.id.edit_profile_button)
        editProfileButton.setOnClickListener {
            val intent = Intent(this, ProfileEditActivity::class.java)
            startActivity(intent)
        }

        // Set up close button to return to main app interface
        val closeButton: Button = findViewById(R.id.close_button)
        closeButton.setOnClickListener {
            val intent = Intent(this, CentralActivity::class.java)
            startActivity(intent)
        }

        // Set up delete account button with confirmation dialog
        val deleteAccountButton: Button = findViewById(R.id.delete_account_button)
        deleteAccountButton.setOnClickListener {
            // Create confirmation dialog for account deletion
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Delete Account")
            builder.setMessage("Are you sure you want to delete your account?")

            // Handle user confirmation of account deletion
            builder.setPositiveButton("Yes") { dialog, _ ->
                // Remove user credentials from shared preferences
                val editor = sharedPref?.edit()
                editor?.remove("email")?.apply()
                editor?.remove("password")?.apply()
                
                // Navigate back to main activity (splash screen)
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                dialog.dismiss()
            }

            // Handle user cancellation of account deletion
            builder.setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }

            // Display the confirmation dialog
            val dialog: AlertDialog = builder.create()
            dialog.show()
        }

        // Display user information in the UI
        emailTextView.text = "Email: $email"
        employerTextView.text = "Employer: $employerName"
        
        // Initialize and display name and surname if available
        nameTextView = findViewById(R.id.name_textview)
        surnameTextView = findViewById(R.id.surname_textview)
        
        // Only display name if it's not null or empty
        if (name?.isNotEmpty() == true) {
            nameTextView.text = "Name: $name"
        }
        
        // Only display surname if it's not null or empty
        if (surname?.isNotEmpty() == true) {
            surnameTextView.text = "Surname: $surname"
        }
    }
}

