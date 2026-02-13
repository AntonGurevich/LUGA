package silverbackgarden.example.luga

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Activity for changing the user's password.
 *
 * Verifies the current password via sign-in, then updates to the new password
 * using Supabase Auth. Email is taken from AuthManager (logged-in user).
 */
class ProfileEditActivity : AppCompatActivity() {

    private lateinit var currentPasswordEditText: EditText
    private lateinit var newPasswordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var changePasswordButton: Button
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_edit)

        currentPasswordEditText = findViewById(R.id.current_password_edittext)
        newPasswordEditText = findViewById(R.id.new_password_edittext)
        confirmPasswordEditText = findViewById(R.id.confirm_password_edittext)
        changePasswordButton = findViewById(R.id.change_password_button)
        authManager = AuthManager(this)

        changePasswordButton.setOnClickListener { handlePasswordChange() }
    }

    private fun handlePasswordChange() {
        val currentPassword = currentPasswordEditText.text.toString()
        val newPassword = newPasswordEditText.text.toString()
        val confirmPassword = confirmPasswordEditText.text.toString()

        if (currentPassword.isEmpty()) {
            Toast.makeText(this, "Please enter your current password", Toast.LENGTH_SHORT).show()
            return
        }
        if (newPassword.isEmpty()) {
            Toast.makeText(this, "Please enter a new password", Toast.LENGTH_SHORT).show()
            return
        }
        if (newPassword.length < 6) {
            Toast.makeText(this, "New password must be at least 6 characters long", Toast.LENGTH_SHORT).show()
            return
        }
        if (newPassword != confirmPassword) {
            Toast.makeText(this, "New passwords do not match", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentPassword == newPassword) {
            Toast.makeText(this, "New password must be different from current password", Toast.LENGTH_SHORT).show()
            return
        }

        val email = authManager.getCurrentUserEmail()
        if (email == null) {
            Toast.makeText(this, "Error: Email not found. Please log in again.", Toast.LENGTH_LONG).show()
            return
        }

        changePasswordButton.isEnabled = false
        changePasswordButton.text = "Changing..."

        authManager.signIn(email, currentPassword, object : AuthManager.AuthCallback {
            override fun onSuccess(user: io.github.jan.supabase.gotrue.user.UserInfo?) {
                if (user != null) {
                    authManager.updatePassword(newPassword, object : AuthManager.AuthCallback {
                        override fun onSuccess(user: io.github.jan.supabase.gotrue.user.UserInfo?) {
                            changePasswordButton.isEnabled = true
                            changePasswordButton.text = "Change Password"
                            currentPasswordEditText.text.clear()
                            newPasswordEditText.text.clear()
                            confirmPasswordEditText.text.clear()
                            Toast.makeText(this@ProfileEditActivity, "Password changed successfully!", Toast.LENGTH_LONG).show()
                            Log.d("ProfileEdit", "Password updated successfully")
                        }
                        override fun onError(error: String) {
                            changePasswordButton.isEnabled = true
                            changePasswordButton.text = "Change Password"
                            Toast.makeText(this@ProfileEditActivity, "Failed to change password: $error", Toast.LENGTH_LONG).show()
                            Log.e("ProfileEdit", "Password update error: $error")
                        }
                    })
                } else {
                    changePasswordButton.isEnabled = true
                    changePasswordButton.text = "Change Password"
                    Toast.makeText(this@ProfileEditActivity, "Current password verification failed", Toast.LENGTH_LONG).show()
                }
            }
            override fun onError(error: String) {
                changePasswordButton.isEnabled = true
                changePasswordButton.text = "Change Password"
                Toast.makeText(this@ProfileEditActivity, "Current password is incorrect", Toast.LENGTH_LONG).show()
                Log.e("ProfileEdit", "Current password verification error: $error")
            }
        })
    }
}
