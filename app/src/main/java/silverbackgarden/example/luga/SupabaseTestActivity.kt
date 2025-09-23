package silverbackgarden.example.luga

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Test activity for verifying Supabase database operations.
 * 
 * This activity provides a simple interface to test user registration,
 * duplicate checking, and data retrieval from the Supabase Users2 table.
 * 
 * Note: This is for testing purposes and should be removed in production.
 */
class SupabaseTestActivity : AppCompatActivity() {
    
    private lateinit var emailEditText: EditText
    private lateinit var connectionCodeEditText: EditText
    private lateinit var testButton: Button
    private lateinit var resultTextView: TextView
    private lateinit var supabaseUserManager: SupabaseUserManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_supabase_test)
        
        supabaseUserManager = SupabaseUserManager()
        
        bindViews()
        setupListeners()
    }
    
    private fun bindViews() {
        emailEditText = findViewById(R.id.test_email_edittext)
        connectionCodeEditText = findViewById(R.id.test_connection_code_edittext)
        testButton = findViewById(R.id.test_button)
        resultTextView = findViewById(R.id.test_result_textview)
    }
    
    private fun setupListeners() {
        testButton.setOnClickListener {
            testSupabaseOperations()
        }
    }
    
    private fun testSupabaseOperations() {
        val email = emailEditText.text.toString()
        val connectionCodeText = connectionCodeEditText.text.toString()
        
        if (email.isBlank() || connectionCodeText.isBlank()) {
            Toast.makeText(this, "Please enter email and connection code", Toast.LENGTH_SHORT).show()
            return
        }
        
        val connectionCode = connectionCodeText.toLongOrNull()
        if (connectionCode == null) {
            Toast.makeText(this, "Please enter a valid connection code", Toast.LENGTH_SHORT).show()
            return
        }
        
        resultTextView.text = "Testing Supabase operations..."
        
        // Test 1: Check if user exists
        supabaseUserManager.checkUserExists(email, connectionCode, object : SupabaseUserManager.DatabaseCallback<UserExistsResponse> {
            override fun onSuccess(result: UserExistsResponse) {
                val resultText = if (result.exists) {
                    "User EXISTS with email: $email and connection code: $connectionCode\n"
                } else {
                    "User does NOT exist with email: $email and connection code: $connectionCode\n"
                }
                
                resultTextView.text = resultText + "\nTesting complete!"
                
                // Test 2: Get all users
                supabaseUserManager.getAllUsers(object : SupabaseUserManager.DatabaseCallback<List<UserData>> {
                    override fun onSuccess(result: List<UserData>) {
                        val allUsersText = "\nTotal users in database: ${result.size}\n"
                        val usersList = result.joinToString("\n") { "ID: ${it.UID}, Email: ${it.email}, Code: ${it.connection_code}" }
                        resultTextView.text = resultText + allUsersText + usersList
                    }
                    
                    override fun onError(error: String) {
                        resultTextView.text = resultText + "\nError getting all users: $error"
                    }
                })
            }
            
            override fun onError(error: String) {
                resultTextView.text = "Error checking user existence: $error"
            }
        })
    }
}

