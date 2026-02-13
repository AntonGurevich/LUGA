package silverbackgarden.example.luga

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.github.jan.supabase.gotrue.user.UserInfo

/**
 * Profile activity that displays user information and provides profile management options.
 *
 * This activity shows the user's profile details including email, employer, name, and surname.
 * - Email: from the account the user logs in with (Supabase Auth).
 * - Employer: from dmp_company_user_registry via the user's connection_code (users_registry).
 * - Name and surname: from shared preferences.
 *
 * It provides navigation to profile editing, account deletion, and return to the main app.
 */
class ProfileActivity : AppCompatActivity() {

    private lateinit var emailTextView: TextView
    private lateinit var employerTextView: TextView
    private lateinit var nameTextView: TextView
    private lateinit var surnameTextView: TextView

    private lateinit var authManager: AuthManager
    private val supabaseUserManager = SupabaseUserManager()

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

        authManager = AuthManager(this)
        emailTextView = findViewById(R.id.email_textview)
        employerTextView = findViewById(R.id.employer_textview)

        // Email: from the account the user logs in with (Supabase Auth)
        val email = authManager.getCurrentUserEmail()
        emailTextView.text = "Email: ${email ?: "—"}"

        // Employer: from dmp_company_user_registry via connection_code
        employerTextView.text = "Employer: …"
        val userId = authManager.getCurrentUserId()
        if (!userId.isNullOrEmpty()) {
            supabaseUserManager.fetchEmployerNameForUser(userId, object : SupabaseUserManager.DatabaseCallback<String?> {
                override fun onSuccess(result: String?) {
                    employerTextView.text = "Employer: ${result ?: "—"}"
                }
                override fun onError(error: String) {
                    employerTextView.text = "Employer: —"
                }
            })
        } else {
            employerTextView.text = "Employer: —"
        }

        val name = sharedPref?.getString("name", null)
        val surname = sharedPref?.getString("surname", null)

        // Change Password: navigate to ProfileEdit (password-only)
        val changePasswordButton: Button = findViewById(R.id.change_password_button)
        changePasswordButton.setOnClickListener {
            startActivity(Intent(this, ProfileEditActivity::class.java))
        }

        // Change Employer
        val changeEmployerButton: Button = findViewById(R.id.change_employer_button)
        changeEmployerButton.setOnClickListener { showChangeEmployerDialog() }

        // Edit name / surname (small ✎ next to each)
        findViewById<TextView>(R.id.edit_name_button).setOnClickListener { showEditNameDialog() }
        findViewById<TextView>(R.id.edit_surname_button).setOnClickListener { showEditSurnameDialog() }

        // Close: return to main app
        val closeButton: Button = findViewById(R.id.close_button)
        closeButton.setOnClickListener {
            val intent = Intent(this, CentralActivity::class.java)
            startActivity(intent)
        }

        // Set up delete account link (small, bottom): first confirmation -> type DELETE -> GDPR deletion
        findViewById<TextView>(R.id.delete_account_link).setOnClickListener { showDeleteAccountDialogs() }

        nameTextView = findViewById(R.id.name_textview)
        surnameTextView = findViewById(R.id.surname_textview)
        updateNameSurnameDisplay(name, surname)
    }

    private fun updateNameSurnameDisplay(name: String?, surname: String?) {
        nameTextView.text = if (name.isNullOrEmpty()) "Name: —" else "Name: $name"
        surnameTextView.text = if (surname.isNullOrEmpty()) "Surname: —" else "Surname: $surname"
    }

    private fun showDeleteAccountDialogs() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_account_confirm_title))
            .setMessage(getString(R.string.delete_account_confirm_message))
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                showTypeDeleteConfirmDialog()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showTypeDeleteConfirmDialog() {
        val input = EditText(this).apply {
            hint = "DELETE"
            setSingleLine(true)
            setPadding(
                (48 * resources.displayMetrics.density).toInt(),
                (32 * resources.displayMetrics.density).toInt(),
                (48 * resources.displayMetrics.density).toInt(),
                (32 * resources.displayMetrics.density).toInt()
            )
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_type_confirm_title))
            .setMessage(getString(R.string.delete_type_confirm_message))
            .setView(input)
            .setPositiveButton(getString(R.string.delete_confirm_button)) { _, _ ->
                val typed = input.text.toString().trim()
                if (typed.equals("DELETE", ignoreCase = true)) {
                    performGdprDeletion()
                } else {
                    Toast.makeText(this, getString(R.string.delete_must_type_delete), Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performGdprDeletion() {
        authManager.requestGdprAccountDeletion(object : AuthManager.AuthCallback {
            override fun onSuccess(user: UserInfo?) {
                clearLocalUserData()
                val intent = Intent(this@ProfileActivity, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                if (isFinishing || isDestroyed) {
                    applicationContext.startActivity(intent)
                } else {
                    startActivity(intent)
                    finish()
                }
            }

            override fun onError(error: String) {
                if (isFinishing || isDestroyed) return
                Toast.makeText(this@ProfileActivity, error, Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun clearLocalUserData() {
        sharedPref?.edit()?.apply {
            remove("email")
            remove("password")
            remove("name")
            remove("surname")
            remove("employer_name")
            apply()
        }
    }

    private fun showChangeEmployerDialog() {
        val options = arrayOf("Provide new employer code", "Remove employer", "Cancel")
        AlertDialog.Builder(this)
            .setTitle("Change Employer")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { /* TODO: new employer code */ }
                    1 -> {
                        getSharedPreferences("MyPrefs", MODE_PRIVATE).edit()
                            .putString("employer_name", null).apply()
                    }
                    2 -> { }
                }
            }
            .show()
    }

    private fun showEditNameDialog() {
        val prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        val current = prefs.getString("name", "") ?: ""
        val input = EditText(this).apply {
            setText(current)
            hint = "Name"
            setSingleLine(true)
            setPadding((48 * resources.displayMetrics.density).toInt(), (32 * resources.displayMetrics.density).toInt(), (48 * resources.displayMetrics.density).toInt(), (32 * resources.displayMetrics.density).toInt())
        }
        AlertDialog.Builder(this)
            .setTitle("Edit Name")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val v = input.text.toString().trim()
                prefs.edit().putString("name", v).apply()
                updateNameSurnameDisplay(v, prefs.getString("surname", null))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditSurnameDialog() {
        val prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        val current = prefs.getString("surname", "") ?: ""
        val input = EditText(this).apply {
            setText(current)
            hint = "Surname"
            setSingleLine(true)
            setPadding((48 * resources.displayMetrics.density).toInt(), (32 * resources.displayMetrics.density).toInt(), (48 * resources.displayMetrics.density).toInt(), (32 * resources.displayMetrics.density).toInt())
        }
        AlertDialog.Builder(this)
            .setTitle("Edit Surname")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val v = input.text.toString().trim()
                prefs.edit().putString("surname", v).apply()
                updateNameSurnameDisplay(prefs.getString("name", null), v)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

