package silverbackgarden.example.luga

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import silverbackgarden.example.luga.R

class ProfileActivity : AppCompatActivity() {

    private lateinit var emailTextView: TextView
    private lateinit var employerTextView: TextView
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var nameTextView: TextView
    private lateinit var surnameTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        emailTextView = findViewById(R.id.email_textview)
        employerTextView = findViewById(R.id.employer_textview)
        sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)

        val email = sharedPreferences.getString("email", "")
        val employerName = sharedPreferences.getString("employer_name", "")

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

        emailTextView.text = "Email: $email"
        employerTextView.text = "Employer: $employerName"
        nameTextView = findViewById(R.id.name_textview)
        surnameTextView = findViewById(R.id.surname_textview)

        val name = sharedPreferences.getString("name", null)
        val surname = sharedPreferences.getString("surname", null)

        if (name?.isNotEmpty() == true) {
            nameTextView.text = "Name: $name"
        }

        if (surname?.isNotEmpty() == true) {
            surnameTextView.text = "Surname: $surname"
        }
    }
}

