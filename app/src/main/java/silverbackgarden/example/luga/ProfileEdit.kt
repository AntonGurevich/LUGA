package silverbackgarden.example.luga

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class ProfileEditActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var nameEditText: EditText
    private lateinit var surnameEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var changeEmployerButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_edit)

        sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        nameEditText = findViewById(R.id.edit_name_edittext)
        surnameEditText = findViewById(R.id.edit_surname_edittext)
        saveButton = findViewById(R.id.save_button)
        changeEmployerButton = findViewById(R.id.change_employer_button)

        // Retrieve the saved name and surname from SharedPreferences
        val name = sharedPreferences.getString("name", "")
        val surname = sharedPreferences.getString("surname", "")

        // Set the EditText fields with the saved values
        nameEditText.setText(name)
        surnameEditText.setText(surname)

        changeEmployerButton.setOnClickListener {
            showChangeEmployerDialog()
        }

        saveButton.setOnClickListener {
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

    private fun showChangeEmployerDialog() {
        val options = arrayOf("Provide new employer code", "Remove employer", "Cancel")

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Change Employer")
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> {
                    // TODO: Handle providing new employer code
                }
                1 -> {

                    val editor = sharedPreferences.edit()
                    editor.putString("employer_name", null)
                    editor.apply()
                }
                2 -> {
                    // Cancel, do nothing
                }
            }
        }
        builder.show()
    }
}
