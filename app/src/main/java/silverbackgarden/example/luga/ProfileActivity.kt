package silverbackgarden.example.luga

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ProfileActivity : AppCompatActivity() {

    private lateinit var emailTextView: TextView
    private lateinit var employerTextView: TextView
    private lateinit var nameTextView: TextView
    private lateinit var surnameTextView: TextView

    private val sharedPref by lazy {
        (applicationContext as? Acteamity)?.sharedPref
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        emailTextView = findViewById(R.id.email_textview)
        employerTextView = findViewById(R.id.employer_textview)

        val email = sharedPref?.getString("email", "")
        val employerName = sharedPref?.getString("employer_name", "")
        val name = sharedPref?.getString("name", null)
        val surname = sharedPref?.getString("surname", null)

        val editProfileButton: Button = findViewById(R.id.edit_profile_button)
        editProfileButton.setOnClickListener {
            val intent = Intent(this, ProfileEditActivity::class.java)
            startActivity(intent)
        }

        val closeButton: Button = findViewById(R.id.close_button)
        closeButton.setOnClickListener {
            val intent = Intent(this, CentralActivity::class.java)
            startActivity(intent)
        }

        val deleteAccountButton: Button = findViewById(R.id.delete_account_button)
        deleteAccountButton.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Delete Account")
            builder.setMessage("Are you sure you want to delete your account?")

            builder.setPositiveButton("Yes") { dialog, _ ->
                val editor = sharedPref?.edit()
                editor?.remove("email")?.apply()
                editor?.remove("password")?.apply()
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                dialog.dismiss()
            }

            builder.setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }

            val dialog: AlertDialog = builder.create()
            dialog.show()
        }

        emailTextView.text = "Email: $email"
        employerTextView.text = "Employer: $employerName"
        nameTextView = findViewById(R.id.name_textview)
        surnameTextView = findViewById(R.id.surname_textview)
        if (name?.isNotEmpty() == true) {
            nameTextView.text = "Name: $name"
        }
        if (surname?.isNotEmpty() == true) {
            surnameTextView.text = "Surname: $surname"
        }
    }
}

