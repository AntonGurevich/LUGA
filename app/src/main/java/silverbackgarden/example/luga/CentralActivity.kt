package silverbackgarden.example.luga

// Android system imports for permissions, sensors, and UI
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

// AndroidX and support library imports
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager

// Local app imports
import silverbackgarden.example.luga.StepDataViewActivity
import silverbackgarden.example.luga.TokenCalculation
import silverbackgarden.example.luga.SupabaseUserManager
import silverbackgarden.example.luga.AuthManager
import io.github.jan.supabase.gotrue.user.UserInfo

// Google Sign-In and Fitness API imports
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field

import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.common.api.Scope

// Third-party UI library for circular progress bars
import com.mikhaellopez.circularprogressbar.CircularProgressBar

// Java utility imports for date formatting, collections, and security
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.security.MessageDigest
import java.math.BigInteger

/**
 * Main activity for the Acteamity app that handles step counting, token calculation,
 * and Google Fit integration. This activity manages permissions, sensor data,
 * and provides the primary user interface for viewing step counts and token balances.
 * 
 * Implements SensorEventListener to receive step sensor updates from the device.
 */
class CentralActivity : AppCompatActivity(), SensorEventListener {

    // Profile button is now handled in the ActionBar menu

         // UI Elements - Circular progress bars for visual representation
     private var stepsProgBar: CircularProgressBar? = null      // Shows daily step progress
     private var cyclingProgBar: CircularProgressBar? = null    // Shows cycling distance progress
     private var swimmingProgBar: CircularProgressBar? = null   // Shows swimming distance progress
     private var eTokenProgBar: CircularProgressBar? = null     // Shows exchangeable token balance
     private var neTokenProgBar: CircularProgressBar? = null    // Shows non-exchangeable token balance
    
         // UI Elements - Text views for displaying numerical values
     private var tSteps: TextView? = null      // Displays current step count
     private var tCycling: TextView? = null    // Displays cycling distance
     private var tSwimming: TextView? = null   // Displays swimming distance
     private var tEToken: TextView? = null     // Displays exchangeable token count
     private var tNEToken: TextView? = null    // Displays non-exchangeable token count
    
    // Permission Indicator UI Elements - Visual status indicators for app permissions
    private var activityRecognitionIcon: ImageView? = null      // Icon for activity recognition permission
    private var activityRecognitionStatus: TextView? = null     // Status text for activity recognition
    private var bodySensorsIcon: ImageView? = null             // Icon for body sensors permission
    private var bodySensorsStatus: TextView? = null            // Status text for body sensors
    private var googleFitIcon: ImageView? = null               // Icon for Google Fit access
    private var googleFitStatus: TextView? = null              // Status text for Google Fit
    private var googleAccountIcon: ImageView? = null           // Icon for Google account status
    private var googleAccountStatus: TextView? = null          // Status text for Google account

    // Sensor management variables
    private var sensorManager: SensorManager? = null           // Manages device sensors
    private var stepCountSensor: Sensor? = null               // Step counter sensor (cumulative)
    private var stepDetectorSensor: Sensor? = null            // Step detector sensor (per-step)
    private var initialStepCount: Int = -1                    // Baseline step count for calculation (fallback)
    private var currentStepCount: Int = 0                     // Current step count since app start

    // Google Fit integration variables
    private var fitnessOptions: FitnessOptions? = null        // Google Fit API configuration
    private var googleSignInAttempted = false                 // Prevents infinite sign-in loops
    
         // New variables for Google Fit monthly baseline approach
     private var googleFitMonthlyBaseline: Int = 0             // Steps from Google Fit since month start (for UI)
     private var deviceSensorSteps: Int = 0                    // Steps detected by device sensors since app start
     private var lastGoogleFitUpdate: Long = 0                 // Last time Google Fit data was fetched
     
    // Activity tracking variables
    private var monthlyCyclingDistance: Float = 0f            // Total cycling distance this month
    private var monthlySwimmingDistance: Float = 0f           // Total swimming distance this month
    private var monthlyCyclingSessions: Int = 0               // Number of cycling sessions this month
    private var monthlySwimmingSessions: Int = 0              // Number of swimming sessions this month

    // Token data management
    private val supabaseUserManager = SupabaseUserManager()
    private lateinit var authManager: AuthManager
    private var currentTokenData: TokenRecord? = null
    private var serverDataLoaded = false

    companion object {
        // Request codes for permission and activity results
        private const val REQUEST_PERMISSIONS_REQUEST_CODE = 1001        // Google Fit permissions
        private const val REQUEST_ACTIVITY_RECOGNITION_PERMISSION = 1002 // Activity recognition permission
        private const val REQUEST_GOOGLE_SIGN_IN = 1003                  // Google Sign-In result
        
                 // App configuration constants
         private const val MONTHLY_TOKEN_EXCHANGE_LIMIT = 30             // Maximum tokens that can be exchanged per month
         private const val DAILY_STEP_GOAL = 10000                       // Daily step target for token calculation
         private const val DAILY_CYCLING_GOAL = 10000                    // Daily cycling target for token calculation (10km)
         private const val DAILY_SWIMMING_GOAL = 1000                    // Daily swimming target for token calculation (1km)
         
         // Google Fit activity type constants
         private const val ACTIVITY_CYCLING = "CYCLING"
         private const val ACTIVITY_SWIMMING = "SWIMMING"
        
        // Logging and preferences constants
        private const val TAG = "CentralActivity"                       // Log tag for debugging
        private const val PREFS_NAME = "AppPrefs"                       // Shared preferences file name
        private const val KEY_INITIAL_STEP_COUNT = "initialStepCount"   // Key for storing initial step count
        private const val KEY_LAST_STEP_RESET_DATE = "lastStepResetDate" // Key for storing last reset date
        
        // Google Fit update constants
        private const val GOOGLE_FIT_UPDATE_INTERVAL = 300000L          // 5 minutes in milliseconds
        private const val API_SNAPSHOT_DAYS = 3                         // Days to send to API
    }

    /**
     * Called when the activity is first created. Initializes the UI, loads saved state,
     * and sets up the initial app configuration including permissions and sensors.
     * 
     * @param savedInstanceState Bundle containing the activity's previously saved state
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "=== onCreate called ===")
        
        // Set the layout for this activity
        setContentView(R.layout.activity_central_test)
        
        // Reset Google Sign-In flag on app start to allow fresh attempts
        // This prevents the app from getting stuck in a sign-in loop
        googleSignInAttempted = false
        Log.d(TAG, "onCreate - Google Sign-In flag reset to false")
        
        // Load persisted step counter state from shared preferences
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        initialStepCount = prefs.getInt(KEY_INITIAL_STEP_COUNT, -1)
        val lastResetDate = prefs.getString(KEY_LAST_STEP_RESET_DATE, "")
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        // Reset step counter if it's a new day to ensure daily tracking
        if (lastResetDate != today) {
            Log.d(TAG, "New day detected, resetting step counter")
            initialStepCount = -1
            currentStepCount = 0
            prefs.edit()
                .putInt(KEY_INITIAL_STEP_COUNT, initialStepCount)
                .putString(KEY_LAST_STEP_RESET_DATE, today)
                .apply()
        }

        // Log current permission status for debugging purposes
        val activityRecognitionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        val bodySensorsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "onCreate - Current permissions - ACTIVITY_RECOGNITION: $activityRecognitionGranted, BODY_SENSORS: $bodySensorsGranted")
        
        // Log Google account status and details for debugging
        val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(this)
        if (lastSignedInAccount != null) {
            Log.d(TAG, "onCreate - Google account status: SIGNED IN")
            Log.d(TAG, "onCreate - Account email: ${lastSignedInAccount.email}")
            Log.d(TAG, "onCreate - Account ID: ${lastSignedInAccount.id}")
            Log.d(TAG, "onCreate - Account display name: ${lastSignedInAccount.displayName}")
            Log.d(TAG, "onCreate - Account ID token: ${if (lastSignedInAccount.idToken != null) "Present" else "Missing"}")
            Log.d(TAG, "onCreate - Account granted scopes: ${lastSignedInAccount.grantedScopes}")
        } else {
            Log.d(TAG, "onCreate - Google account status: NOT SIGNED IN")
        }
        Log.d(TAG, "onCreate - Google Sign-In attempted flag: $googleSignInAttempted")
        
        // Log app signature for debugging Google Sign-In issues
        // This helps verify the SHA-1 fingerprint matches Google Cloud Console
        logAppSignature()
        
        // Log current first run status and permission request history
        val firstRunStatus = isFirstRun()
        val hasRequestedBefore = hasRequestedPermissionsBefore()
        Log.d(TAG, "onCreate - First run status: $firstRunStatus, Has requested before: $hasRequestedBefore")
        
        // Initialize AuthManager with proper context
        authManager = AuthManager(this)
        
        // Initialize UI elements first before proceeding with other setup
        Log.d(TAG, "Calling initializeUI()")
        initializeUI()
        
                 // Validate that critical UI elements were successfully initialized
         // If any are missing, show error and finish the activity to prevent crashes
         // Note: neTokenProgBar and tNEToken are optional (non-exchangeable token circle was removed)
         if (stepsProgBar == null || cyclingProgBar == null || swimmingProgBar == null ||
             eTokenProgBar == null ||
             tSteps == null || tCycling == null || tSwimming == null ||
             tEToken == null) {
             Log.e(TAG, "Critical UI elements failed to initialize - showing error and finishing activity")
             Toast.makeText(this, "Error: Required UI elements not found. Please check the app layout.", Toast.LENGTH_LONG).show()
             finish()
             return
         }
        
        // Check and request necessary permissions before initializing sensors
        // This ensures the app has the required access to function properly
        Log.d(TAG, "Calling checkAndRequestPermissions()")
        checkAndRequestPermissions()
        
        // Check monthly data validity after permissions are granted
        checkMonthlyDataValidity()
        
        // Load token data from server
        loadTokenData()
    }

    /**
     * Creates the options menu for the ActionBar.
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Log.d(TAG, "=== onCreateOptionsMenu called ===")
        menuInflater.inflate(R.menu.central_activity_menu, menu)
        return true
    }

    /**
     * Handles menu item selections.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(TAG, "=== onOptionsItemSelected called ===")
        return when (item.itemId) {
            R.id.action_profile -> {
                Log.d(TAG, "Profile menu item selected")
                val intent = Intent(this, ProfileActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.action_sensor_status -> {
                Log.d(TAG, "Sensor status toggle selected")
                toggleSensorStatusVisibility()
                // Update the menu item check state
                item.isChecked = !item.isChecked
                true
            }
            R.id.action_logout -> {
                Log.d(TAG, "Logout menu item selected")
                performLogout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Toggles the visibility of sensor status indicators.
     */
    private fun toggleSensorStatusVisibility() {
        Log.d(TAG, "=== toggleSensorStatusVisibility called ===")
        
        // Find the permission indicators layout
        val permissionIndicators = findViewById<View>(R.id.permissionIndicators)
        if (permissionIndicators != null) {
            val isVisible = permissionIndicators.visibility == View.VISIBLE
            permissionIndicators.visibility = if (isVisible) View.GONE else View.VISIBLE
            
            val message = if (isVisible) "Sensor indicators hidden" else "Sensor indicators shown"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Sensor indicators visibility changed to: ${!isVisible}")
        } else {
            Log.e(TAG, "Permission indicators layout not found")
        }
    }

    /**
     * Performs user logout by clearing session and returning to login screen.
     */
    private fun performLogout() {
        Log.d(TAG, "=== performLogout called ===")
        
        // Show confirmation dialog
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ ->
                Log.d(TAG, "User confirmed logout")
                
                // Sign out from Supabase and clear local session
                authManager.signOut(object : AuthManager.AuthCallback {
                    override fun onSuccess(user: UserInfo?) {
                        Log.d(TAG, "Logout successful, navigating to login screen")
                        // Navigate to login screen
                        val intent = Intent(this@CentralActivity, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                    
                    override fun onError(error: String) {
                        Log.e(TAG, "Logout error: $error")
                        // Even if sign out fails, navigate to login screen
                        // The session will be checked on login screen
                        val intent = Intent(this@CentralActivity, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                })
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                Log.d(TAG, "Logout cancelled by user")
                dialog.dismiss()
            }
            .show()
    }
    
    override fun onStart() {
        super.onStart()
        Log.d(TAG, "=== onStart called ===")
        
        // Check if token data needs refresh (after data sync) - check here too in case activity is already running
        val prefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val needsRefresh = prefs.getBoolean("token_data_needs_refresh", false)
        if (needsRefresh) {
            Log.d(TAG, "Token data refresh flag detected in onStart - refreshing token data after data sync")
            prefs.edit().putBoolean("token_data_needs_refresh", false).apply()
            // Small delay to ensure server has processed the new data and token calculations are updated
            Handler(Looper.getMainLooper()).postDelayed({
                Log.d(TAG, "Refreshing token data after data sync completion (from onStart)")
                loadTokenData()
            }, 1500) // 1.5 second delay to allow server to process new data and recalculate tokens
        }
    }
    
    /**
     * Initializes all UI elements by finding them in the layout and setting up
     * their event listeners. This method performs null checks to ensure all
     * required UI components are available before proceeding.
     */
    private fun initializeUI() {
        Log.d(TAG, "=== initializeUI called ===")
        
        try {
            // Initialize progress bars with null checks to prevent crashes
            // Each progress bar represents a different metric (steps, cycling, swimming, exchangeable tokens, non-exchangeable tokens)
            stepsProgBar = findViewById(R.id.circularProgressBarSteps)
            if (stepsProgBar == null) {
                Log.e(TAG, "circularProgressBarSteps not found in layout")
                return
            }
            
            cyclingProgBar = findViewById(R.id.circularProgressBarCycling)
            if (cyclingProgBar == null) {
                Log.e(TAG, "circularProgressBarCycling not found in layout")
                return
            }
            
            swimmingProgBar = findViewById(R.id.circularProgressBarSwimming)
            if (swimmingProgBar == null) {
                Log.e(TAG, "circularProgressBarSwimming not found in layout")
                return
            }
            
            eTokenProgBar = findViewById(R.id.circularProgressBarExTokens)
            if (eTokenProgBar == null) {
                Log.e(TAG, "circularProgressBarExTokens not found in layout")
                return
            }
            // neTokenProgBar / circularProgressBarNonExTokens removed from layout; stay null, all usages are neTokenProgBar?.

            // Initialize text views with null checks to prevent crashes
            // These display the numerical values for steps, cycling, swimming, and tokens
            tSteps = findViewById(R.id.tvStepsProgress)
            if (tSteps == null) {
                Log.e(TAG, "tvStepsProgress not found in layout")
                return
            }
            
            tCycling = findViewById(R.id.tvCyclingProgress)
            if (tCycling == null) {
                Log.e(TAG, "tvCyclingProgress not found in layout")
                return
            }
            
            tSwimming = findViewById(R.id.tvSwimmingProgress)
            if (tSwimming == null) {
                Log.e(TAG, "tvSwimmingProgress not found in layout")
                return
            }
            
            tEToken = findViewById(R.id.tvExTokensBal)
            if (tEToken == null) {
                Log.e(TAG, "tvExTokensBal not found in layout")
                return
            }

            tNEToken = findViewById(R.id.tvNonExTokensBal)
            if (tNEToken == null) {
                Log.w(TAG, "tvNonExTokensBal not found in layout (non-ex block optional)")
            }

            // Initialize permission indicators with null checks
            // These provide visual feedback about the status of app permissions
            activityRecognitionIcon = findViewById(R.id.activityRecognitionIcon)
            activityRecognitionStatus = findViewById(R.id.activityRecognitionStatus)
            bodySensorsIcon = findViewById(R.id.bodySensorsIcon)
            bodySensorsStatus = findViewById(R.id.bodySensorsStatus)
            googleFitIcon = findViewById(R.id.googleFitIcon)
            googleFitStatus = findViewById(R.id.googleFitStatus)
            googleAccountIcon = findViewById(R.id.googleAccountIcon)
            googleAccountStatus = findViewById(R.id.googleAccountStatus)

            // Check if all permission indicators were found
            if (activityRecognitionIcon == null || activityRecognitionStatus == null ||
                bodySensorsIcon == null || bodySensorsStatus == null ||
                googleFitIcon == null || googleFitStatus == null ||
                googleAccountIcon == null || googleAccountStatus == null) {
                Log.w(TAG, "Some permission indicators not found in layout")
            }

            // Hide sensor indicators by default
            val permissionIndicators = findViewById<View>(R.id.permissionIndicators)
            if (permissionIndicators != null) {
                permissionIndicators.visibility = View.GONE
                Log.d(TAG, "Sensor indicators hidden by default")
            }

            // Profile button is now handled in the ActionBar menu
            
                         // Set up progress bar click listeners for navigation and functionality
             stepsProgBar?.setOnClickListener {
                 val intent = Intent(this, StepDataViewActivity::class.java)
                 startActivity(intent)
             }
             
             // Set up cycling progress bar click listener
             cyclingProgBar?.setOnClickListener {
                 Toast.makeText(this, "Cycling: ${String.format("%.0f", monthlyCyclingDistance)}m this month", Toast.LENGTH_SHORT).show()
             }
             
             // Set up swimming progress bar click listener
             swimmingProgBar?.setOnClickListener {
                 Toast.makeText(this, "Swimming: ${String.format("%.0f", monthlySwimmingDistance)}m this month", Toast.LENGTH_SHORT).show()
             }
            
            // Add long press to step counter to force request permissions and show monthly progress
            // This provides users with a way to re-request permissions if needed
            stepsProgBar?.setOnLongClickListener {
                Log.d(TAG, "Step counter long pressed, showing monthly progress and forcing permission request")
                showMonthlyProgress()
                forceRequestPermissions()
                true
            }
            
            // Add double tap to step counter to reset step count
            // This allows users to manually reset their daily step tracking
            var stepTapCount = 0
            var lastStepTapTime = 0L
            stepsProgBar?.setOnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastStepTapTime < 1000) { // Tap within 1000ms
                        stepTapCount++
                        if (stepTapCount == 2) {
                            // Double tap - reset step counter
                            Log.d(TAG, "Step counter double tap detected, resetting step counter")
                            resetStepCounter()
                            Toast.makeText(this, "Step counter reset", Toast.LENGTH_SHORT).show()
                            stepTapCount = 0
                        }
                    } else {
                        stepTapCount = 1
                    }
                    lastStepTapTime = currentTime
                }
                false
            }

            // Set up click listeners for token progress bars
            // Currently shows placeholder messages for future MVP features
            eTokenProgBar?.setOnClickListener {
                Toast.makeText(this, "Detailed Exchangeable Tokens Data capability is not supported in MVP yet", Toast.LENGTH_SHORT).show()
            }

            neTokenProgBar?.setOnClickListener {
                Toast.makeText(this, "Detailed non-Exchangeable Tokens Data capability is not supported in MVP yet", Toast.LENGTH_SHORT).show()
            }
            
            // Update permission indicators only if all required views exist
            // This prevents crashes if the layout is incomplete
            if (activityRecognitionIcon != null && activityRecognitionStatus != null &&
                bodySensorsIcon != null && bodySensorsStatus != null &&
                googleFitIcon != null && googleFitStatus != null &&
                googleAccountIcon != null && googleAccountStatus != null) {
                updatePermissionIndicators()
            }
            
            // Note: WorkManager initialization for step counting will be done later
            // in setupGoogleFitAndSensors() when we know the user has proper permissions
            
            Log.d(TAG, "=== initializeUI completed successfully ===")
            
        } catch (e: Exception) {
            // Handle any errors during UI initialization gracefully
            Log.e(TAG, "Error in initializeUI: ${e.message}", e)
            Toast.makeText(this, "Error initializing UI: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Checks the current permission status and requests necessary permissions
     * for the app to function properly. This method handles the permission
     * flow for both first-time users and returning users.
     */
    private fun checkAndRequestPermissions() {
        Log.d(TAG, "=== Starting permission check ===")
        
        // Check Android version - runtime permissions are only needed for API 23+ (Marshmallow)
        // On older versions, permissions are granted at install time
        val currentApiVersion = android.os.Build.VERSION.SDK_INT
        Log.d(TAG, "Current API version: $currentApiVersion")
        
        if (currentApiVersion < android.os.Build.VERSION_CODES.M) {
            Log.d(TAG, "Running on pre-Marshmallow, permissions granted at install time")
            Toast.makeText(this, "Permissions granted at install time", Toast.LENGTH_SHORT).show()
            setupGoogleFitAndSensors()
            return
        }
        
        // Check if this is the first run of the app
        // First-time users get a welcome dialog explaining why permissions are needed
        val firstRunStatus = isFirstRun()
        Log.d(TAG, "First run status: $firstRunStatus")
        if (firstRunStatus) {
            Log.d(TAG, "First run detected, showing welcome dialog")
            showWelcomeDialog()
            return
        }
        
        // Always check current permission status first to determine what needs to be requested
        val activityRecognitionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        val bodySensorsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
        
        Log.d(TAG, "ACTIVITY_RECOGNITION permission status: $activityRecognitionGranted")
        Log.d(TAG, "BODY_SENSORS permission status: $bodySensorsGranted")
        
        // Build a list of permissions that need to be requested
        // Both ACTIVITY_RECOGNITION and BODY_SENSORS are required for step counting
        val permissionsToRequest = mutableListOf<String>()
        
        if (!activityRecognitionGranted) {
            permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
            Log.d(TAG, "ACTIVITY_RECOGNITION permission not granted, will request")
        } else {
            Log.d(TAG, "ACTIVITY_RECOGNITION permission already granted")
        }
        
        if (!bodySensorsGranted) {
            permissionsToRequest.add(Manifest.permission.BODY_SENSORS)
            Log.d(TAG, "BODY_SENSORS permission not granted, will request")
        } else {
            Log.d(TAG, "BODY_SENSORS permission already granted")
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: ${permissionsToRequest.joinToString()}")
            
            // Check if we should show rationale for any permission
            // This happens when the user has previously denied the permission
            val shouldShowRationale = permissionsToRequest.any { shouldShowPermissionRationale(it) }
            
            if (shouldShowRationale) {
                Log.d(TAG, "Showing permission rationale dialog")
                showPermissionRationaleDialog(permissionsToRequest.toTypedArray())
            } else {
                Log.d(TAG, "Directly requesting permissions")
                Toast.makeText(this, "Requesting permissions: ${permissionsToRequest.joinToString()}", Toast.LENGTH_SHORT).show()
                markPermissionsRequested()
                markFirstRunComplete() // Mark first run as complete after requesting permissions
                ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toTypedArray(),
                    REQUEST_ACTIVITY_RECOGNITION_PERMISSION
                )
            }
        } else {
            // All permissions already granted, proceed with Google Fit setup
            Log.d(TAG, "All permissions already granted, proceeding with setup")
            Toast.makeText(this, "All permissions granted, setting up step counting", Toast.LENGTH_SHORT).show()
            setupGoogleFitAndSensors()
        }
    }
    
    /**
     * Checks if this is the first time the app is being run.
     * Used to determine whether to show the welcome dialog.
     * 
     * @return true if this is the first run, false otherwise
     */
    private fun isFirstRun(): Boolean {
        val sharedPref = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val result = sharedPref.getBoolean("isFirstRun", true)
        Log.d(TAG, "isFirstRun() called, returning: $result")
        return result
    }
    
    /**
     * Marks the first run as complete by setting the flag to false.
     * This prevents the welcome dialog from showing again.
     */
    private fun markFirstRunComplete() {
        val sharedPref = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        sharedPref.edit().putBoolean("isFirstRun", false).apply()
        Log.d(TAG, "=== markFirstRunComplete called - isFirstRun set to false ===")
    }
    
    /**
     * Shows a welcome dialog for first-time users explaining why the app
     * needs certain permissions to function properly.
     */
    private fun showWelcomeDialog() {
        Log.d(TAG, "=== showWelcomeDialog called ===")
        val message = "Welcome to Acteamity!\n\n" +
                     "This app needs the following permissions to count your steps:\n\n" +
                     "• Physical Activity: To detect when you're walking\n" +
                     "• Body Sensors: To access step counting sensors\n\n" +
                     "These permissions are essential for the app to function properly."
        
        Log.d(TAG, "Showing welcome dialog with message: $message")
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Welcome!")
            .setMessage(message)
            .setPositiveButton("Grant Permissions") { _, _ ->
                Log.d(TAG, "User clicked 'Grant Permissions' in welcome dialog")
                // Mark as not first run and proceed with permission request
                proceedWithPermissionRequest()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun proceedWithPermissionRequest() {
        Log.d(TAG, "=== proceedWithPermissionRequest called ===")
        // Check current permission status and request if needed
        val activityRecognitionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        val bodySensorsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
        
        Log.d(TAG, "First run permission check - ACTIVITY_RECOGNITION: $activityRecognitionGranted, BODY_SENSORS: $bodySensorsGranted")
        
        val permissionsToRequest = mutableListOf<String>()
        
        if (!activityRecognitionGranted) {
            permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
            Log.d(TAG, "Will request ACTIVITY_RECOGNITION permission")
        }
        
        if (!bodySensorsGranted) {
            permissionsToRequest.add(Manifest.permission.BODY_SENSORS)
            Log.d(TAG, "Will request BODY_SENSORS permission")
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "First run: Requesting permissions: ${permissionsToRequest.joinToString()}")
            Toast.makeText(this, "First run: Requesting permissions: ${permissionsToRequest.joinToString()}", Toast.LENGTH_SHORT).show()
            markPermissionsRequested()
            markFirstRunComplete() // Mark first run as complete after requesting permissions
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                REQUEST_ACTIVITY_RECOGNITION_PERMISSION
            )
        } else {
            // All permissions already granted
            markFirstRunComplete() // Mark first run as complete even if permissions are already granted
            setupGoogleFitAndSensors()
        }
    }
    
    private fun hasRequestedPermissionsBefore(): Boolean {
        val sharedPref = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val result = sharedPref.getBoolean("hasRequestedPermissions", false)
        Log.d(TAG, "hasRequestedPermissionsBefore() called, returning: $result")
        return result
    }
    
    private fun markPermissionsRequested() {
        val sharedPref = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        sharedPref.edit().putBoolean("hasRequestedPermissions", true).apply()
        Log.d(TAG, "=== markPermissionsRequested called - hasRequestedPermissions set to true ===")
    }
    
    private fun forceRequestPermissions() {
        Log.d(TAG, "=== Force requesting permissions ===")
        
        // Force request both permissions regardless of current state
        val permissionsToRequest = arrayOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.BODY_SENSORS
        )
        
        Log.d(TAG, "Force requesting permissions: ${permissionsToRequest.joinToString()}")
        Toast.makeText(this, "Force requesting all permissions", Toast.LENGTH_SHORT).show()
        
        Log.d(TAG, "Calling markPermissionsRequested() and markFirstRunComplete()")
        markPermissionsRequested()
        markFirstRunComplete() // Mark first run as complete after force requesting permissions
        ActivityCompat.requestPermissions(
            this,
            permissionsToRequest,
            REQUEST_ACTIVITY_RECOGNITION_PERMISSION
        )
    }
    
    private fun shouldShowPermissionRationale(permission: String): Boolean {
        val result = ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
        Log.d(TAG, "shouldShowPermissionRationale($permission) called, returning: $result")
        return result
    }
    
    private fun showPermissionRationaleDialog(permissions: Array<String>) {
        Log.d(TAG, "=== showPermissionRationaleDialog called ===")
        Log.d(TAG, "Showing rationale for permissions: ${permissions.joinToString()}")
        
        val message = "This app needs the following permissions to count your steps:\n\n" +
                     "• Physical Activity: To detect when you're walking\n" +
                     "• Body Sensors: To access step counting sensors\n\n" +
                     "These permissions are essential for the app to function properly."
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage(message)
            .setPositiveButton("Grant Permissions") { _, _ ->
                Log.d(TAG, "User clicked 'Grant Permissions' in rationale dialog")
                markPermissionsRequested()
                markFirstRunComplete() // Mark first run as complete after requesting permissions
                ActivityCompat.requestPermissions(
                    this,
                    permissions,
                    REQUEST_ACTIVITY_RECOGNITION_PERMISSION
                )
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                Log.d(TAG, "User clicked 'Cancel' in rationale dialog")
                dialog.dismiss()
                Toast.makeText(this, "App cannot function without these permissions", Toast.LENGTH_LONG).show()
            }
            .show()
    }
    
    private fun handlePermanentlyDeniedPermissions(deniedPermissions: List<String>) {
        Log.d(TAG, "=== handlePermanentlyDeniedPermissions called ===")
        Log.d(TAG, "Handling permanently denied permissions: ${deniedPermissions.joinToString()}")
        
        val message = "The following permissions were permanently denied:\n\n" +
                     deniedPermissions.joinToString("\n") { "• $it" } +
                     "\n\nTo enable these permissions:\n" +
                     "1. Go to Settings > Apps > Acteamity > Permissions\n" +
                     "2. Enable the denied permissions\n" +
                     "3. Restart the app"
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Permissions Permanently Denied")
            .setMessage(message)
            .setPositiveButton("Go to Settings") { _, _ ->
                Log.d(TAG, "User clicked 'Go to Settings' in permanently denied dialog")
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = android.net.Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Close") { dialog, _ ->
                Log.d(TAG, "User clicked 'Close' in permanently denied dialog")
                dialog.dismiss()
            }
            .show()
    }
    
        private fun setupGoogleFitAndSensors() {
            Log.d(TAG, "=== setupGoogleFitAndSensors called ===")
            
            // First, try to get Google Fit data as primary source
            if (GoogleSignIn.getLastSignedInAccount(this) != null) {
                Log.d(TAG, "Google account available, fetching Google Fit data first")
                fetchGoogleFitDataAndSetupSensors()
            } else {
                Log.d(TAG, "No Google account, falling back to device sensors only")
                setupDeviceSensorsOnly()
            }
        }
    
    private fun setupGoogleFit() {
        Log.d(TAG, "=== setupGoogleFit called ===")
        
        fitnessOptions = FitnessOptions.builder()
            .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_READ)      // For cycling, swimming, etc.
            .addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ)        // Distance data
            .build()
            
        // Update permission indicators now that fitnessOptions is initialized
        updatePermissionIndicators()
            
        val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(this)
        Log.d(TAG, "Google account status: ${if (lastSignedInAccount != null) "Signed in" else "Not signed in"}")
        
        val currentFitnessOptions = fitnessOptions
        if (lastSignedInAccount != null && currentFitnessOptions != null && GoogleSignIn.hasPermissions(lastSignedInAccount, currentFitnessOptions)) {
            // Already have permissions, proceed
            Log.d(TAG, "Google Fit permissions already granted")
            
            // Initialize WorkManager for step counting since Google Fit is available
            val stepCountWorkRequest = PeriodicWorkRequestBuilder<StepCountWorker>(6, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "step_sync_work",
                ExistingPeriodicWorkPolicy.KEEP,
                stepCountWorkRequest
            )
            Log.d(TAG, "StepCountWorker initialized and enqueued with unique policy")
            
            // Also schedule an immediate one-time execution for testing
            val immediateWorkRequest = OneTimeWorkRequestBuilder<StepCountWorker>()
                .build()
            WorkManager.getInstance(this).enqueue(immediateWorkRequest)
            Log.d(TAG, "Immediate StepCountWorker execution scheduled for testing")
            
            readStepCount()
        } else {
            // Request Google Fit permissions
            try {
                if (lastSignedInAccount == null) {
                    // Check if we've already attempted Google Sign-In to prevent infinite loops
                    if (googleSignInAttempted) {
                        Log.w(TAG, "Google Sign-In already attempted, falling back to device sensors only")
                        Toast.makeText(this, "Google Sign-In cancelled. Using device sensors only.", Toast.LENGTH_LONG).show()
                        // Just read from device sensors without trying Google Fit
                        readStepCount()
                        return
                    }
                    
                    // No Google account signed in - initiate Google Sign-In
                    Log.w(TAG, "No Google account signed in, initiating Google Sign-In")
                    Log.d(TAG, "=== Google Sign-In Configuration Details ===")
                    Log.d(TAG, "OAuth Client ID: 465622083556-75gj6fqpims30lr2q1iqo5rd9dpkrc4f.apps.googleusercontent.com") // Web client ID
                    Log.d(TAG, "Requested scopes: https://www.googleapis.com/auth/fitness.activity.read")
                    Log.d(TAG, "Requested permissions: email, id, profile")
                    Log.d(TAG, "Google Sign-In flag set to: true")
                    
                    Toast.makeText(this, "Signing in to Google account...", Toast.LENGTH_SHORT).show()
                    googleSignInAttempted = true
                    
                    val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestId()
                        .requestProfile()
                        // Add comprehensive OAuth scopes for Google Fit
                        .requestScopes(Scope("https://www.googleapis.com/auth/fitness.activity.read"))
                        .requestScopes(Scope("https://www.googleapis.com/auth/fitness.body.read"))
                        .requestScopes(Scope("https://www.googleapis.com/auth/fitness.location.read"))
                        .requestScopes(Scope("https://www.googleapis.com/auth/fitness.nutrition.read"))
                        .requestIdToken("465622083556-75gj6fqpims30lr2q1iqo5rd9dpkrc4f.apps.googleusercontent.com") // Web client ID
                        .build()

                    Log.d(TAG, "GoogleSignInOptions built successfully")
                    Log.d(TAG, "Sign-in options: $signInOptions")
                    
                    val signInClient = GoogleSignIn.getClient(this, signInOptions)
                    Log.d(TAG, "GoogleSignIn client created successfully")
                    
                    val signInIntent = signInClient.signInIntent
                    Log.d(TAG, "Sign-in intent created, starting activity for result")
                    Log.d(TAG, "Intent action: ${signInIntent.action}")
                    Log.d(TAG, "Intent package: ${signInIntent.`package`}")
                    
                    startActivityForResult(signInIntent, REQUEST_GOOGLE_SIGN_IN)
                    Log.d(TAG, "Google Sign-In activity started with request code: $REQUEST_GOOGLE_SIGN_IN")
                    return
                }
                
                Log.d(TAG, "Requesting Google Fit permissions")
                fitnessOptions?.let { options ->
                    GoogleSignIn.requestPermissions(
                        this,
                        REQUEST_PERMISSIONS_REQUEST_CODE,
                        lastSignedInAccount,
                        options
                    )
                }
                Toast.makeText(this, "Google Fit permission requested", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request Google Fit permissions", e)
                Toast.makeText(this, "Failed to request Google Fit permissions: ${e.message}", Toast.LENGTH_SHORT).show()
                // Show guidance for manual permission setup
                showPermissionGuidance()
            }
        }
    }
    
    /**
     * Fetches Google Fit monthly data and sets up device sensors for real-time updates.
     * This method prioritizes Google Fit as the primary data source.
     */
    private fun fetchGoogleFitDataAndSetupSensors() {
        Log.d(TAG, "=== fetchGoogleFitDataAndSetupSensors called ===")
        
        // First setup Google Fit configuration and update indicators
        setupGoogleFit()
        
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account == null) {
            Log.w(TAG, "No Google account, falling back to device sensors")
            setupDeviceSensorsOnly()
            return
        }
        
        // Fetch Google Fit data from the beginning of the current month for UI baseline
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)        // Start from 1st day of month
        cal.set(Calendar.HOUR_OF_DAY, 0)         // Start at beginning of day
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val monthStartTime = cal.timeInMillis
        val endTime = System.currentTimeMillis()
        
        Log.d(TAG, "Fetching Google Fit monthly data for UI baseline: ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(monthStartTime))} to now")
        
        val monthlyReadRequest = DataReadRequest.Builder()
            .setTimeRange(monthStartTime, endTime, TimeUnit.MILLISECONDS)
            .read(DataType.TYPE_STEP_COUNT_DELTA)
            .build()
        
        Fitness.getHistoryClient(this, account)
            .readData(monthlyReadRequest)
            .addOnSuccessListener { response ->
                val dataSet = response.getDataSet(DataType.TYPE_STEP_COUNT_DELTA)
                val monthlySteps = dataSet.dataPoints.sumOf { it.getValue(Field.FIELD_STEPS).asInt() }
                
                Log.d(TAG, "Google Fit monthly baseline fetched: $monthlySteps steps since month start")
                
                // Set Google Fit monthly baseline for UI
                googleFitMonthlyBaseline = monthlySteps
                deviceSensorSteps = 0
                currentStepCount = monthlySteps
                lastGoogleFitUpdate = System.currentTimeMillis()
                
                // Save monthly baseline to shared preferences for persistence
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putInt("google_fit_monthly_baseline", monthlySteps)
                    .putLong("google_fit_last_update", lastGoogleFitUpdate)
                    .putString("google_fit_month", SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date()))
                    .apply()
                
                // Update UI with monthly baseline - DISABLED to prevent overriding server data
                // updateUIWithStepCount(currentStepCount)
                
                                 // Now setup device sensors for real-time updates
                 setupDeviceSensors()
                 
                 // Fetch cycling and swimming activity data
                 fetchActivityData(account)
                 
                 // Start periodic Google Fit updates (both monthly baseline and API snapshots)
                 startPeriodicGoogleFitUpdates()
                 
                 // Show user the monthly baseline
                 Toast.makeText(this, "Loaded $monthlySteps steps from Google Fit (month to date)", Toast.LENGTH_LONG).show()
                
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch Google Fit monthly data, falling back to device sensors", e)
                setupDeviceSensorsOnly()
            }
    }
    
    /**
     * Sets up device sensors for real-time step detection.
     * This is called after Google Fit data is loaded.
     */
    private fun setupDeviceSensors() {
        Log.d(TAG, "=== setupDeviceSensors called ===")
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCountSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepDetectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        
        if (stepCountSensor != null || stepDetectorSensor != null) {
            Log.d(TAG, "Device sensors available, registering listeners")
            
            // Register sensor listeners
            stepCountSensor?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            stepDetectorSensor?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        } else {
            Log.w(TAG, "No device sensors available")
        }
    }
    
    /**
     * Sets up device sensors only when Google Fit is not available.
     * This is the fallback method for devices without Google Fit access.
     */
    private fun setupDeviceSensorsOnly() {
        Log.d(TAG, "=== setupDeviceSensorsOnly called ===")
        
        // Fallback to original device sensor logic
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCountSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepDetectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        
        if (stepCountSensor == null && stepDetectorSensor == null) {
            Log.w(TAG, "No step sensors available on this device")
            Toast.makeText(this, "No step sensors available on this device", Toast.LENGTH_SHORT).show()
        } else {
            setupDeviceSensors()
        }
    }
    
    /**
     * Starts periodic Google Fit updates for both monthly baseline and API snapshots.
     */
    private fun startPeriodicGoogleFitUpdates() {
        Log.d(TAG, "=== startPeriodicGoogleFitUpdates called ===")
        
        // Start background worker for periodic Google Fit updates
        val stepCountWorkRequest = PeriodicWorkRequestBuilder<StepCountWorker>(6, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "step_sync_work",
            ExistingPeriodicWorkPolicy.KEEP,
            stepCountWorkRequest
        )
        Log.d(TAG, "StepCountWorker initialized and enqueued with unique policy")
        
        // Also schedule an immediate one-time execution for testing
        val immediateWorkRequest = OneTimeWorkRequestBuilder<StepCountWorker>()
            .build()
        WorkManager.getInstance(this).enqueue(immediateWorkRequest)
        Log.d(TAG, "Immediate StepCountWorker execution scheduled for testing")
    }
    
    /**
     * Manually triggers the StepCountWorker for immediate testing.
     * This is useful for debugging and verifying worker functionality.
     */
    private fun triggerWorkerManually() {
        Log.d(TAG, "=== Manual Worker Trigger ===")
        val manualWorkRequest = OneTimeWorkRequestBuilder<StepCountWorker>()
            .build()
        WorkManager.getInstance(this).enqueue(manualWorkRequest)
        Log.d(TAG, "Manual StepCountWorker execution triggered")
        Toast.makeText(this, "Worker triggered manually - check logs", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Checks if we need to refresh monthly data (e.g., new month).
     * This ensures data consistency across month boundaries.
     */
    private fun checkMonthlyDataValidity() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedMonth = prefs.getString("google_fit_month", "")
        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        
        if (savedMonth != currentMonth) {
            Log.d(TAG, "New month detected ($currentMonth), refreshing Google Fit data")
            // New month, need to fetch fresh data
            fetchGoogleFitDataAndSetupSensors()
        } else {
            // Same month, check if we have recent data
            val lastUpdate = prefs.getLong("google_fit_last_update", 0)
            val currentTime = System.currentTimeMillis()
            
            if (currentTime - lastUpdate > GOOGLE_FIT_UPDATE_INTERVAL) {
                Log.d(TAG, "Google Fit data is stale, refreshing")
                fetchGoogleFitDataAndSetupSensors()
            } else {
                // Data is fresh, restore from preferences
                val monthlyBaseline = prefs.getInt("google_fit_monthly_baseline", 0)
                if (monthlyBaseline > 0) {
                    Log.d(TAG, "Restoring monthly baseline from preferences: $monthlyBaseline steps")
                    googleFitMonthlyBaseline = monthlyBaseline
                    currentStepCount = monthlyBaseline + deviceSensorSteps
                    // updateUIWithStepCount(currentStepCount) // DISABLED to prevent overriding server data
                }
            }
        }
    }
    
    /**
     * Loads token data from the server for the current user and month.
     * Updates the UI with token information including reimbursable/non-reimbursable tokens,
     * token limits, and progress towards next tokens for steps, swim, and bike activities.
     */
    private fun loadTokenData() {
        Log.d(TAG, "Loading token data from server")
        
        // Check if user is logged in
        if (!authManager.isLoggedIn()) {
            Log.w(TAG, "User not logged in, skipping token data load")
            return
        }
        
        // Get current user ID
        val userId = authManager.getCurrentUserId()
        if (userId.isNullOrEmpty()) {
            Log.w(TAG, "No user ID available, skipping token data load")
            return
        }
        
        Log.d(TAG, "Fetching token data for user: $userId")
        
        // Fetch token data from Supabase
        supabaseUserManager.fetchTokenData(userId, object : SupabaseUserManager.DatabaseCallback<TokenRecord> {
            override fun onSuccess(result: TokenRecord) {
                Log.d(TAG, "Token data loaded successfully: $result")
                currentTokenData = result
                updateUIWithTokenData(result)
            }
            
            override fun onError(error: String) {
                Log.e(TAG, "Failed to load token data: $error")
                showTokenDataError(error)
            }
        })
    }
    
    /**
     * Refreshes token data from the server.
     * This can be called manually to update token information.
     */
    fun refreshTokenData() {
        Log.d(TAG, "Manually refreshing token data")
        loadTokenData()
    }
    
    /**
     * Shows error message when token data cannot be loaded from server.
     * This is only called for actual errors (network issues, etc.), not for missing data.
     * Missing data (new users) is handled by returning a default TokenRecord with zeros.
     * 
     * @param error The error message from the server
     */
    private fun showTokenDataError(error: String) {
        Log.e(TAG, "Token data fetch error: $error")
        
        // Only show toast for actual errors (not for missing data which is normal for new users)
        // Check if it's a real error vs just missing data
        if (!error.contains("No token data", ignoreCase = true)) {
            Toast.makeText(this, "Unable to load token data: $error", Toast.LENGTH_LONG).show()
        }
        
        // Show default/empty state instead of "Error" - this is more user-friendly
        // New users will see zeros until they sync data
        tEToken?.text = "0"
        tNEToken?.text = "0"
        tSteps?.text = "0/10000"
        tSwimming?.text = "0m/2000m"
        tCycling?.text = "0m/12000m"
        
        // Reset progress bars to empty state with correct maximums
        eTokenProgBar?.apply {
            progress = 0f
            progressMax = 30f // Default token limit
        }
        neTokenProgBar?.apply {
            progress = 0f
            progressMax = 30f // Default token limit
        }
        stepsProgBar?.apply {
            progress = 0f
            progressMax = 10000f
        }
        swimmingProgBar?.apply {
            progress = 0f
            progressMax = 2000f
        }
        cyclingProgBar?.apply {
            progress = 0f
            progressMax = 12000f
        }
        
        // Mark that server data failed to load
        serverDataLoaded = false
        currentTokenData = null
        Log.d(TAG, "❌ SERVER DATA FLAG RESET: serverDataLoaded = false")
        
        Log.d(TAG, "UI reset to default empty state")
    }
    
    /**
     * Updates the UI with token data from the server.
     * 
     * @param tokenData The token data received from the server
     */
    private fun updateUIWithTokenData(tokenData: TokenRecord) {
        Log.d(TAG, "Updating UI with token data: $tokenData")
        
        // Log detailed token data for debugging
        Log.d(TAG, "Token data details - reimbursable: ${tokenData.reimbursable_tokens}, nonreimbursable: ${tokenData.nonreimbursable_tokens}")
        Log.d(TAG, "Token data details - token_limit: ${tokenData.token_limit}, steps: ${tokenData.steps_to_token}, swim: ${tokenData.swim_to_token}, bike: ${tokenData.bike_to_token}")
        
        try {
            // Update reimbursable tokens (exchangeable tokens)
            tEToken?.text = tokenData.reimbursable_tokens.toInt().toString()
            eTokenProgBar?.apply {
                progress = tokenData.reimbursable_tokens.toFloat()
                progressMax = (tokenData.token_limit ?: 30.0).toFloat()
            }
            
            // Update non-reimbursable tokens
            tNEToken?.text = tokenData.nonreimbursable_tokens.toInt().toString()
            neTokenProgBar?.apply {
                progress = tokenData.nonreimbursable_tokens.toFloat()
                progressMax = (tokenData.token_limit ?: 30.0).toFloat()
            }
            
            // Update progress towards next tokens
            // Note: Server data represents progress towards next token (remainder of division)
            // - steps_to_token: steps progress towards next token (0-10000)
            // - swim_to_token: swimming meters progress towards next token (0-2000)
            // - bike_to_token: cycling meters progress towards next token (0-12000)
            val swimProgress = (tokenData.swim_to_token ?: 0.0).toFloat()
            val bikeProgress = (tokenData.bike_to_token ?: 0.0).toFloat()
            val stepsProgress = (tokenData.steps_to_token ?: 0.0).toFloat()
            
            // Update swimming progress bar and text (max 2000m)
            swimmingProgBar?.apply {
                progress = swimProgress
                progressMax = 2000f
            }
            tSwimming?.text = "${swimProgress.toInt()}m/2000m"
            
            // Update cycling progress bar and text (max 12000m)
            cyclingProgBar?.apply {
                progress = bikeProgress
                progressMax = 12000f
            }
            tCycling?.text = "${bikeProgress.toInt()}m/12000m"
            
            // Update steps progress bar and text (max 10000 steps)
            stepsProgBar?.apply {
                progress = stepsProgress
                progressMax = 10000f
            }
            tSteps?.text = "${stepsProgress.toInt()}/10000"
            
            Log.d(TAG, "Server data applied - Steps: ${stepsProgress.toInt()}, Swim: ${swimProgress.toInt()}m, Bike: ${bikeProgress.toInt()}m")
            
            // Mark that server data has been loaded to prevent local data from overriding
            serverDataLoaded = true
            Log.d(TAG, "✅ SERVER DATA FLAG SET: serverDataLoaded = true")
            
            Log.d(TAG, "UI updated with token data successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating UI with token data: ${e.message}", e)
            Toast.makeText(this, "Error updating token display", Toast.LENGTH_SHORT).show()
        }
    }
    
         /**
      * Shows monthly progress information for debugging purposes.
      * This method displays the current monthly baseline and device sensor additions.
      */
     private fun showMonthlyProgress() {
         val message = """
             Monthly Progress:
             Google Fit Baseline: ${googleFitMonthlyBaseline} steps
             Device Sensor Additions: ${deviceSensorSteps} steps
             Total Monthly Steps: ${currentStepCount} steps
             Cycling Distance: ${String.format("%.0f", monthlyCyclingDistance)}m
             Swimming Distance: ${String.format("%.0f", monthlySwimmingDistance)}m
             Last Update: ${if (lastGoogleFitUpdate > 0) SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastGoogleFitUpdate)) else "Never"}
         """.trimIndent()
         
         Log.d(TAG, message)
         Toast.makeText(this, "Monthly: ${googleFitMonthlyBaseline} + ${deviceSensorSteps} = ${currentStepCount} steps", Toast.LENGTH_LONG).show()
     }
     
     /**
      * Data class representing activity data from Google Fit.
      * Contains information about cycling, swimming, and other activities.
      */
     data class ActivityData(
         val type: String,           // "CYCLING", "SWIMMING", etc.
         val distance: Float,        // Distance in meters
         val duration: Long,         // Duration in milliseconds
         val startTime: Long,        // Start timestamp
         val endTime: Long           // End timestamp
     )
    
         /**
      * Refreshes Google Fit data periodically to keep the monthly baseline up to date.
      * This method is called by the background worker to maintain data freshness.
      */
     private fun refreshGoogleFitData() {
         Log.d(TAG, "=== refreshGoogleFitData called ===")
         
         val account = GoogleSignIn.getLastSignedInAccount(this)
         if (account != null && googleFitMonthlyBaseline > 0) {
             // Only refresh if we have an account and existing baseline
             fetchGoogleFitDataAndSetupSensors()
         }
     }
     
     /**
      * Fetches cycling and swimming activity data from Google Fit.
      * This method retrieves activity segments and distance data for the current month.
      */
     private fun fetchActivityData(account: GoogleSignInAccount) {
         Log.d(TAG, "=== fetchActivityData called ===")
         
         val cal = Calendar.getInstance()
         cal.set(Calendar.DAY_OF_MONTH, 1)
         cal.set(Calendar.HOUR_OF_DAY, 0)
         cal.set(Calendar.MINUTE, 0)
         cal.set(Calendar.SECOND, 0)
         cal.set(Calendar.MILLISECOND, 0)
         val monthStartTime = cal.timeInMillis
         val endTime = System.currentTimeMillis()
         
         val readRequest = DataReadRequest.Builder()
             .setTimeRange(monthStartTime, endTime, TimeUnit.MILLISECONDS)
             .read(DataType.TYPE_ACTIVITY_SEGMENT)
             .read(DataType.TYPE_DISTANCE_DELTA)
             .build()
         
         Fitness.getHistoryClient(this, account)
             .readData(readRequest)
             .addOnSuccessListener { response ->
                 // Process cycling data
                 val cyclingData = response.getDataSet(DataType.TYPE_ACTIVITY_SEGMENT)
                     .dataPoints.filter { dataPoint ->
                         try {
                             val activity = dataPoint.getValue(Field.FIELD_ACTIVITY).asString()
                             activity == ACTIVITY_CYCLING
                         } catch (e: Exception) {
                             false
                         }
                     }
                 
                 // Process swimming data
                 val swimmingData = response.getDataSet(DataType.TYPE_ACTIVITY_SEGMENT)
                     .dataPoints.filter { dataPoint ->
                         try {
                             val activity = dataPoint.getValue(Field.FIELD_ACTIVITY).asString()
                             activity == ACTIVITY_SWIMMING
                         } catch (e: Exception) {
                             false
                         }
                     }
                 
                 // Calculate totals
                 monthlyCyclingDistance = cyclingData.sumOf { 
                     try {
                         it.getValue(Field.FIELD_DISTANCE).asFloat().toDouble()
                     } catch (e: Exception) {
                         0.0
                     }
                 }.toFloat()
                 
                 monthlySwimmingDistance = swimmingData.sumOf { 
                     try {
                         it.getValue(Field.FIELD_DISTANCE).asFloat().toDouble()
                     } catch (e: Exception) {
                         0.0
                     }
                 }.toFloat()
                 
                 monthlyCyclingSessions = cyclingData.size
                 monthlySwimmingSessions = swimmingData.size
                 
                 Log.d(TAG, "Activity data fetched - Cycling: ${monthlyCyclingDistance}m (${monthlyCyclingSessions} sessions), Swimming: ${monthlySwimmingDistance}m (${monthlySwimmingSessions} sessions)")
                 
                // Update UI with new activity data - DISABLED to prevent overriding server data
                // updateActivityProgress()
                // updateTokenDisplay()
                 
             }
             .addOnFailureListener { e ->
                 Log.e(TAG, "Failed to fetch activity data", e)
                 
                 // Handle specific OAuth consent error
                 if (e.message?.contains("5000") == true || e.message?.contains("OAuth consent") == true) {
                     Log.e(TAG, "OAuth consent required - initiating explicit Google Sign-In")
                     runOnUiThread {
                         Toast.makeText(this, "OAuth consent required. Please sign in to Google.", Toast.LENGTH_LONG).show()
                         
                         // Show dialog with OAuth consent options
                         AlertDialog.Builder(this)
                             .setTitle("Google Fit OAuth Consent Required")
                             .setMessage("To access your fitness data, you need to grant OAuth consent:\n\n" +
                                     "1. Sign in to your Google account\n" +
                                     "2. Grant permission for fitness data access\n" +
                                     "3. The app will then access your Google Fit data")
                             .setPositiveButton("Sign In to Google") { _, _ ->
                                 initiateExplicitGoogleSignIn()
                             }
                             .setNegativeButton("Use Device Sensors Only") { _, _ ->
                                 setupDeviceSensorsOnly()
                             }
                             .setNeutralButton("Cancel") { _, _ -> }
                             .show()
                     }
                 } else {
                     runOnUiThread {
                         Toast.makeText(this, "Failed to fetch activity data: ${e.message}", Toast.LENGTH_SHORT).show()
                     }
                 }
             }
     }
     
     /**
      * Updates the cycling and swimming progress bars and text displays.
      * Shows progress toward the next token for each activity type.
      */
     private fun updateActivityProgress() {
         Log.d(TAG, "=== updateActivityProgress called ===")
         
         // Update cycling progress (0-10,000m)
         val cyclingProgress = (monthlyCyclingDistance % DAILY_CYCLING_GOAL).toFloat()
         cyclingProgBar?.progress = cyclingProgress
         
         // Update swimming progress (0-1,000m)
         val swimmingProgress = (monthlySwimmingDistance % DAILY_SWIMMING_GOAL).toFloat()
         swimmingProgBar?.progress = swimmingProgress
         
         // Update text displays
         tCycling?.text = "${String.format("%.0f", monthlyCyclingDistance)}m"
         tSwimming?.text = "${String.format("%.0f", monthlySwimmingDistance)}m"
         
         Log.d(TAG, "Activity progress updated - Cycling: ${cyclingProgress}/${DAILY_CYCLING_GOAL}, Swimming: ${swimmingProgress}/${DAILY_SWIMMING_GOAL}")
     }
     
     /**
      * Updates the token display with recalculated values including cycling and swimming.
      * This method is called after activity data is updated.
      */
     private fun updateTokenDisplay() {
         Log.d(TAG, "=== updateTokenDisplay called ===")
         
         // Recalculate tokens with new activity data
         val tokenCalc = calculateTokens(currentStepCount)
         
         // Update token progress bars
         eTokenProgBar?.setProgressWithAnimation(tokenCalc.exchangeableTokens.toFloat(), 1000)
         neTokenProgBar?.setProgressWithAnimation(tokenCalc.nonExchangeableTokens.toFloat(), 1000)
         
         // Update token text displays
         tEToken?.text = "${tokenCalc.exchangeableTokens}/$MONTHLY_TOKEN_EXCHANGE_LIMIT"
         tNEToken?.text = "${tokenCalc.nonExchangeableTokens}/${60 - MONTHLY_TOKEN_EXCHANGE_LIMIT}"
         
         Log.d(TAG, "Token display updated - Exchangeable: ${tokenCalc.exchangeableTokens}, Non-exchangeable: ${tokenCalc.nonExchangeableTokens}")
     }
    
    private fun showPermissionGuidance() {
        Log.d(TAG, "=== showPermissionGuidance called ===")
        val message = "To use step counting features:\n" +
                     "1. Go to Settings > Apps > Acteamity > Permissions\n" +
                     "2. Enable 'Physical Activity' and 'Body Sensors'\n" +
                     "3. Make sure Google Fit is installed and signed in"
        Log.d(TAG, "Showing permission guidance toast: $message")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun manualGoogleSignIn() {
        Log.d(TAG, "=== manualGoogleSignIn called ===")
        Log.d(TAG, "=== Manual Google Sign-In Configuration Details ===")
        Log.d(TAG, "OAuth Client ID: 465622083556-75gj6fqpims30lr2q1iqo5rd9dpkrc4f.apps.googleusercontent.com") // Web client ID
        Log.d(TAG, "Requested scopes: https://www.googleapis.com/auth/fitness.activity.read")
        Log.d(TAG, "Requested permissions: email, id, profile")
        Log.d(TAG, "Current Google Sign-In flag: $googleSignInAttempted")
        
        Toast.makeText(this, "Initiating Google Sign-In...", Toast.LENGTH_SHORT).show()
        
        try {
            val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestId()
                .requestProfile()
                // Add comprehensive OAuth scopes for Google Fit
                .requestScopes(Scope("https://www.googleapis.com/auth/fitness.activity.read"))
                .requestScopes(Scope("https://www.googleapis.com/auth/fitness.body.read"))
                .requestScopes(Scope("https://www.googleapis.com/auth/fitness.location.read"))
                .requestScopes(Scope("https://www.googleapis.com/auth/fitness.nutrition.read"))
                .requestIdToken("465622083556-75gj6fqpims30lr2q1iqo5rd9dpkrc4f.apps.googleusercontent.com") // Web client ID
                .build()

            Log.d(TAG, "Manual GoogleSignInOptions built successfully")
            Log.d(TAG, "Manual sign-in options: $signInOptions")
            
            val signInClient = GoogleSignIn.getClient(this, signInOptions)
            Log.d(TAG, "Manual GoogleSignIn client created successfully")
            
            val signInIntent = signInClient.signInIntent
            Log.d(TAG, "Manual sign-in intent created, starting activity for result")
            Log.d(TAG, "Manual intent action: ${signInIntent.action}")
            Log.d(TAG, "Manual intent package: ${signInIntent.`package`}")
            
            startActivityForResult(signInIntent, REQUEST_GOOGLE_SIGN_IN)
            Log.d(TAG, "Manual Google Sign-In activity started with request code: $REQUEST_GOOGLE_SIGN_IN")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Manual Google Sign-In FAILED with Exception")
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            Log.e(TAG, "Exception stack trace:", e)
            Toast.makeText(this, "Failed to start Google Sign-In: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Initiates explicit Google Sign-In with comprehensive OAuth scopes for Google Fit.
     * This method is called when OAuth consent is required.
     */
    private fun initiateExplicitGoogleSignIn() {
        Log.d(TAG, "=== initiateExplicitGoogleSignIn called ===")
        Log.d(TAG, "=== Explicit OAuth Google Sign-In Configuration ===")
        Log.d(TAG, "OAuth Client ID: 465622083556-75gj6fqpims30lr2q1iqo5rd9dpkrc4f.apps.googleusercontent.com")
        Log.d(TAG, "Requested OAuth scopes:")
        Log.d(TAG, "  - https://www.googleapis.com/auth/fitness.activity.read")
        Log.d(TAG, "  - https://www.googleapis.com/auth/fitness.body.read")
        Log.d(TAG, "  - https://www.googleapis.com/auth/fitness.location.read")
        Log.d(TAG, "  - https://www.googleapis.com/auth/fitness.nutrition.read")
        
        Toast.makeText(this, "Initiating Google OAuth Sign-In...", Toast.LENGTH_SHORT).show()
        
        try {
            // Reset the sign-in attempt flag to allow new attempts
            googleSignInAttempted = false
            
            val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestId()
                .requestProfile()
                // Comprehensive OAuth scopes for Google Fit
                .requestScopes(Scope("https://www.googleapis.com/auth/fitness.activity.read"))
                .requestScopes(Scope("https://www.googleapis.com/auth/fitness.body.read"))
                .requestScopes(Scope("https://www.googleapis.com/auth/fitness.location.read"))
                .requestScopes(Scope("https://www.googleapis.com/auth/fitness.nutrition.read"))
                .requestIdToken("465622083556-75gj6fqpims30lr2q1iqo5rd9dpkrc4f.apps.googleusercontent.com")
                .build()

            Log.d(TAG, "Explicit OAuth GoogleSignInOptions built successfully")
            
            val signInClient = GoogleSignIn.getClient(this, signInOptions)
            Log.d(TAG, "Explicit OAuth GoogleSignIn client created successfully")
            
            val signInIntent = signInClient.signInIntent
            Log.d(TAG, "Explicit OAuth sign-in intent created, starting activity for result")
            
            startActivityForResult(signInIntent, REQUEST_GOOGLE_SIGN_IN)
            Log.d(TAG, "Explicit OAuth Google Sign-In activity started")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Explicit OAuth Google Sign-In FAILED", e)
            Toast.makeText(this, "Failed to start OAuth Sign-In: ${e.message}", Toast.LENGTH_SHORT).show()
            
            // Fallback to device sensors
            AlertDialog.Builder(this)
                .setTitle("OAuth Sign-In Failed")
                .setMessage("Unable to start Google OAuth Sign-In. Would you like to use device sensors instead?")
                .setPositiveButton("Use Device Sensors") { _, _ ->
                    setupDeviceSensorsOnly()
                }
                .setNegativeButton("Cancel") { _, _ -> }
                .show()
        }
    }
    
    private fun showPermissionStatus() {
        Log.d(TAG, "=== showPermissionStatus called ===")
        val activityRecognitionStatus = if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) "✓ Granted" else "✗ Denied"
        val bodySensorsStatus = if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED) "✓ Granted" else "✗ Denied"
        val hasRequestedBefore = if (hasRequestedPermissionsBefore()) "Yes" else "No"
        val isFirstRunFlag = if (isFirstRun()) "Yes" else "No"
        
        // Check Google account status
        val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(this)
        val googleAccountStatus = if (lastSignedInAccount != null) "✓ Signed in (${lastSignedInAccount.email})" else "✗ Not signed in"
        
        Log.d(TAG, "Permission status - ACTIVITY_RECOGNITION: $activityRecognitionStatus, BODY_SENSORS: $bodySensorsStatus")
        Log.d(TAG, "Debug info - hasRequestedBefore: $hasRequestedBefore, isFirstRunFlag: $isFirstRunFlag")
        Log.d(TAG, "Google account status: $googleAccountStatus")
        
        val message = "Permission Status:\n" +
                     "Activity Recognition: $activityRecognitionStatus\n" +
                     "Body Sensors: $bodySensorsStatus\n" +
                     "Google Account: $googleAccountStatus\n\n" +
                     "Debug Info:\n" +
                     "Has Requested Before: $hasRequestedBefore\n" +
                     "First Run Flag: $isFirstRunFlag\n" +
                     "Google Sign-In Attempted: $googleSignInAttempted\n\n" +
                     "Profile Button Controls:\n" +
                     "• Long press: Show this status\n" +
                     "• Double tap: Clear preferences + reset Google Sign-In\n" +
                     "• Triple tap: Manual Google Sign-In\n" +
                     "• Quadruple tap: Reset Google Sign-In flag only"
        
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Current Permissions")
            .setMessage(message)
            .setPositiveButton("Go to Settings") { _, _ ->
                showPermissionSettingsDialog()
            }
            .setNegativeButton("Close") { dialog, _ ->
                dialog.dismiss()
            }
        
        // Add Google Sign-In button if not signed in
        if (lastSignedInAccount == null) {
            builder.setNeutralButton("Sign in to Google") { _, _ ->
                Log.d(TAG, "User clicked 'Sign in to Google' button")
                manualGoogleSignIn()
            }
        }
        
        // Add reset Google Sign-In flag button
        builder.setNegativeButton("Reset Google Sign-In") { _, _ ->
            Log.d(TAG, "User clicked 'Reset Google Sign-In' button")
            resetGoogleSignInFlag()
            Toast.makeText(this, "Google Sign-In flag reset. You can now try signing in again.", Toast.LENGTH_SHORT).show()
        }
        
        builder.show()
    }
    
    private fun showPermissionSettingsDialog() {
        Log.d(TAG, "=== showPermissionSettingsDialog called ===")
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("This app needs permissions to count your steps and access fitness data. Please enable them in settings.")
            .setPositiveButton("Go to Settings") { _, _ ->
                Log.d(TAG, "User clicked 'Go to Settings' in settings dialog")
                // Open app settings
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = android.net.Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                Log.d(TAG, "User clicked 'Cancel' in settings dialog")
                dialog.dismiss()
            }
            .show()
    }
    
    private fun hasRequiredPermissions(): Boolean {
        val activityRecognitionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        val bodySensorsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
        val result = activityRecognitionGranted && bodySensorsGranted
        
        Log.d(TAG, "hasRequiredPermissions() called - ACTIVITY_RECOGNITION: $activityRecognitionGranted, BODY_SENSORS: $bodySensorsGranted, result: $result")
        
        return result
    }
    
    private fun handlePermissionChange() {
        Log.d(TAG, "=== handlePermissionChange called ===")
        val hasPermissions = hasRequiredPermissions()
        Log.d(TAG, "Handling permission change. Has required permissions: $hasPermissions")
        
        if (hasPermissions) {
            // Permissions granted, enable features
            Log.d(TAG, "Permissions granted, enabling features")
            Log.d(TAG, "Calling setupGoogleFitAndSensors()")
            setupGoogleFitAndSensors()
        } else {
            // Permissions revoked, disable features
            Log.w(TAG, "Permissions revoked, disabling features")
            sensorManager?.unregisterListener(this)
            Toast.makeText(this, "Permissions revoked. Step counting disabled.", Toast.LENGTH_LONG).show()
            // Clear step data
            currentStepCount = 0
            // updateUIWithStepCount(0) // DISABLED to prevent overriding server data
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "=== onResume called ===")
        
        // Check if permissions are still valid
        val hasPermissions = hasRequiredPermissions()
        Log.d(TAG, "onResume - hasRequiredPermissions: $hasPermissions")
        
        if (!hasPermissions) {
            Log.w(TAG, "Required permissions not granted in onResume")
            Toast.makeText(this, "Required permissions not granted. Please enable them in settings.", Toast.LENGTH_LONG).show()
            return
        }
        
        // Handle any permission changes
        Log.d(TAG, "Calling handlePermissionChange()")
        handlePermissionChange()
        
        // Check if token data needs refresh (after data sync)
        val prefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val needsRefresh = prefs.getBoolean("token_data_needs_refresh", false)
        if (needsRefresh) {
            Log.d(TAG, "Token data refresh flag detected - refreshing token data after data sync")
            prefs.edit().putBoolean("token_data_needs_refresh", false).apply()
            // Small delay to ensure server has processed the new data and token calculations are updated
            Handler(Looper.getMainLooper()).postDelayed({
                Log.d(TAG, "Refreshing token data after data sync completion")
                loadTokenData()
            }, 1500) // 1.5 second delay to allow server to process new data and recalculate tokens
        } else {
            // Normal refresh when returning to activity
            loadTokenData()
        }
        
        // Update permission indicators when resuming
        updatePermissionIndicators()
        
        // Check monthly data validity when resuming
        checkMonthlyDataValidity()
        
        stepCountSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        stepDetectorSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }
    
    override fun onRestart() {
        super.onRestart()
        Log.d(TAG, "=== onRestart called ===")
        
        // Check if permissions have changed while app was in background
        val hasPermissions = hasRequiredPermissions()
        Log.d(TAG, "onRestart - hasRequiredPermissions: $hasPermissions")
        
        if (hasPermissions) {
            Log.d(TAG, "Permissions available, re-initializing")
            Log.d(TAG, "Calling setupGoogleFitAndSensors()")
            setupGoogleFitAndSensors()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "=== onPause called ===")
        sensorManager?.unregisterListener(this)
    }
    
    override fun onStop() {
        super.onStop()
        Log.d(TAG, "=== onStop called ===")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "=== onDestroy called ===")
        sensorManager?.unregisterListener(this)
    }

    /**
     * Called when the accuracy of a sensor changes.
     * Currently not used but required by SensorEventListener interface.
     * 
     * @param sensor The sensor whose accuracy changed
     * @param accuracy The new accuracy value
     */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "=== onAccuracyChanged called ===")
        Log.d(TAG, "Sensor accuracy changed: sensor=${sensor?.name}, accuracy=$accuracy")
        // Not used in current implementation
    }

    /**
     * Called when sensor data changes. This is the main method for receiving
     * step sensor updates from the device. Routes different sensor types to
     * appropriate handling methods.
     * 
     * @param event The sensor event containing the new data
     */
    override fun onSensorChanged(event: SensorEvent?) {
        Log.d(TAG, "=== onSensorChanged called ===")
        Log.d(TAG, "Sensor event: type=${event?.sensor?.type}, values=${event?.values?.joinToString()}")
        
        when (event?.sensor?.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                Log.d(TAG, "Handling STEP_COUNTER sensor event")
                handleStepCounterEvent(event)
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                Log.d(TAG, "Handling STEP_DETECTOR sensor event")
                handleStepDetectorEvent(event)
            }
            else -> {
                Log.d(TAG, "Unknown sensor type: ${event?.sensor?.type}")
            }
        }
    }
    
    /**
     * Handles events from the step counter sensor. This sensor provides cumulative
     * step counts since device boot, so we need to calculate the difference from
     * when the app started tracking.
     * 
     * @param event The sensor event containing step data
     */
    private fun handleStepCounterEvent(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val totalSteps = event.values[0].toInt()
            
            Log.d(TAG, "🔴 STEP_COUNTER EVENT - Raw sensor value: ${event.values[0]}")
            
            if (googleFitMonthlyBaseline > 0) {
                // We have Google Fit monthly baseline, use device sensor for real-time updates
                val previousDeviceSteps = deviceSensorSteps
                deviceSensorSteps = totalSteps
                
                // Only update if we have a meaningful change
                if (deviceSensorSteps > previousDeviceSteps) {
                    val newSteps = deviceSensorSteps - previousDeviceSteps
                    currentStepCount += newSteps
                    
                    Log.d(TAG, "🔴 STEP_COUNTER EVENT - Device steps: $newSteps, Monthly total: $currentStepCount")
                    
                    runOnUiThread {
                        // updateStepCountDisplay() // DISABLED to prevent overriding server data
                    }
                }
            } else {
                // Fallback to original logic if no Google Fit baseline
                if (initialStepCount == -1) {
                    initialStepCount = totalSteps
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putInt(KEY_INITIAL_STEP_COUNT, initialStepCount)
                        .apply()
                    Log.d(TAG, "🔴 STEP_COUNTER EVENT - Initial step count set to: $initialStepCount")
                }
                
                val previousStepCount = currentStepCount
                currentStepCount = totalSteps - initialStepCount
                
                if (currentStepCount != previousStepCount) {
                    runOnUiThread {
                        // updateStepCountDisplay() // DISABLED to prevent overriding server data
                    }
                }
            }
        }
    }
    
    /**
     * Handles events from the step detector sensor. This sensor provides
     * individual step detection (1.0 for each step) rather than cumulative counts.
     * 
     * @param event The sensor event containing step data
     */
    private fun handleStepDetectorEvent(event: SensorEvent) {
        Log.d(TAG, "🔴 STEP_DETECTOR EVENT - Called with value: ${event.values[0]}")
        
        if (event.values[0] == 1.0f) {
            if (googleFitMonthlyBaseline > 0) {
                // We have Google Fit monthly baseline, increment device sensor count
                deviceSensorSteps++
                currentStepCount++
                
                Log.d(TAG, "🟢 STEP_DETECTOR EVENT - Step detected! Monthly total: $currentStepCount")
                
                runOnUiThread {
                    // updateStepCountDisplay() // DISABLED to prevent overriding server data
                }
            } else {
                // Fallback to original logic
                val previousStepCount = currentStepCount
                currentStepCount++
                // updateUIWithStepCount(currentStepCount) // DISABLED to prevent overriding server data
            }
        } else {
            Log.d(TAG, "🟡 STEP_DETECTOR EVENT - Ignored value: ${event.values[0]} (not 1.0)")
        }
    }

    private fun readStepCount() {
        // Simply update the UI with current step count
        // The logic for data source selection is handled by setupGoogleFitAndSensors
        Log.d(TAG, "readStepCount called - currentStepCount: $currentStepCount")
        // updateUIWithStepCount(currentStepCount) // DISABLED to prevent overriding server data
    }
    
    private fun readStepCountFromGoogleFit() {
        // Prevent re-triggering Google Sign-In if it was previously cancelled
        if (googleSignInAttempted && GoogleSignIn.getLastSignedInAccount(this) == null) {
            Log.d(TAG, "Google Sign-In was previously cancelled, skipping Google Fit API call")
            return
        }

        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account == null) {
            Log.d(TAG, "No Google account signed in, cannot read from Google Fit")
            return
        }

        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        val startTime = cal.timeInMillis
        val endTime = System.currentTimeMillis()
        
        Log.d(TAG, "readStepCountFromGoogleFit - time range: $startTime to $endTime")

        val readRequest = DataReadRequest.Builder()
            .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
            .read(DataType.TYPE_STEP_COUNT_DELTA)
            .build()

        Fitness.getHistoryClient(this, account)
            .readData(readRequest)
            .addOnSuccessListener { response ->
                Log.d(TAG, "Google Fit data read successful")
                val dataSet = response.getDataSet(DataType.TYPE_STEP_COUNT_DELTA)
                val totalSteps: Int = if (dataSet.isEmpty) {
                    Log.d(TAG, "No step data from Google Fit")
                    0
                } else {
                    var total = 0
                    for (dataPoint in dataSet.dataPoints) {
                        val steps = dataPoint.getValue(Field.FIELD_STEPS).asInt()
                        total += steps
                    }
                    Log.d(TAG, "Total steps from Google Fit: $total")
                    total
                }
                // updateUIWithStepCount(totalSteps) // DISABLED to prevent overriding server data
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to read step count from Google Fit", e)
                Toast.makeText(this@CentralActivity, "Failed to read step count: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun resetStepCounter() {
        initialStepCount = -1
        currentStepCount = 0
        
        // Update persisted state
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_INITIAL_STEP_COUNT, initialStepCount)
            .putString(KEY_LAST_STEP_RESET_DATE, today)
            .apply()
        
        // updateUIWithStepCount(0) // DISABLED to prevent overriding server data
        Log.d(TAG, "Step counter reset")
    }
    
    private fun getStepDataSource(): String {
        val result = when {
            googleFitMonthlyBaseline > 0 -> "Google Fit (Monthly) + Device Sensors"
            stepCountSensor != null -> "Device Step Counter"
            stepDetectorSensor != null -> "Device Step Detector"
            else -> "Google Fit (Fallback)"
        }
        Log.d(TAG, "getStepDataSource() called, returning: $result")
        return result
    }

    /**
     * Calculates token rewards based on steps, cycling, and swimming.
     * The token system works as follows:
     * - Every 10,000 steps = 1 token
     * - Every 10,000m cycling = 1 token
     * - Every 1,000m swimming = 1 token
     * - First 30 tokens per month are exchangeable
     * - Remaining tokens (up to 60 total) are non-exchangeable
     * 
     * @param stepCount The current step count to calculate tokens for
     * @return TokenCalculation object containing all token and activity information
     */
    private fun calculateTokens(stepCount: Int): TokenCalculation {
        Log.d(TAG, "=== calculateTokens called with stepCount: $stepCount ===")
        
        // Calculate steps remaining for today's goal (modulo operation)
        val todaySteps = stepCount % DAILY_STEP_GOAL
        // Calculate total tokens earned from steps (integer division)
        val stepTokens = stepCount / DAILY_STEP_GOAL
        
        // Calculate tokens from cycling (10,000m = 1 token)
        val cyclingTokens = (monthlyCyclingDistance / DAILY_CYCLING_GOAL).toInt()
        
        // Calculate tokens from swimming (1,000m = 1 token)
        val swimmingTokens = (monthlySwimmingDistance / DAILY_SWIMMING_GOAL).toInt()
        
        // Combine all tokens
        val totalTokens = stepTokens + cyclingTokens + swimmingTokens
        
        // Exchangeable tokens are limited by monthly cap
        val exchangeableTokens = minOf(totalTokens, MONTHLY_TOKEN_EXCHANGE_LIMIT)
        // Remaining tokens after exchangeable limit
        val remainingTokens = maxOf(0, totalTokens - exchangeableTokens)
        // Non-exchangeable tokens are limited by remaining monthly capacity
        val nonExchangeableTokens = minOf(remainingTokens, 60 - MONTHLY_TOKEN_EXCHANGE_LIMIT)
        
        Log.d(TAG, "calculateTokens - todaySteps: $todaySteps, stepTokens: $stepTokens, cyclingTokens: $cyclingTokens, swimmingTokens: $swimmingTokens, totalTokens: $totalTokens, exchangeableTokens: $exchangeableTokens, nonExchangeableTokens: $nonExchangeableTokens")
        
        return TokenCalculation(
            steps = todaySteps,
            exchangeableTokens = exchangeableTokens,
            nonExchangeableTokens = nonExchangeableTokens,
            monthlyExchangeLimit = MONTHLY_TOKEN_EXCHANGE_LIMIT,
            dailyStepGoal = DAILY_STEP_GOAL
        )
    }

    /**
     * Updates the step count display with detailed logging for debugging.
     * This method is called when step data changes and updates all UI elements
     * including progress bars and text views with animated transitions.
     */
    private fun updateStepCountDisplay() {
        Log.d(TAG, "�� UI UPDATE - updateStepCountDisplay called with currentStepCount: $currentStepCount")
        
        val dataSource = getStepDataSource()
        Log.d(TAG, "🟢 UI UPDATE - Data source: $dataSource")
        
        Toast.makeText(this, "Steps: $currentStepCount (via $dataSource)", Toast.LENGTH_SHORT).show()

        val tokenCalculation = calculateTokens(currentStepCount)
        Log.d(TAG, "🟢 UI UPDATE - Token calculation: $tokenCalculation")

        // Log UI element states before update
        Log.d(TAG, "🟢 UI UPDATE - Progress bars: stepsProgBar=${stepsProgBar != null}, eTokenProgBar=${eTokenProgBar != null}, neTokenProgBar=${neTokenProgBar != null}")
        Log.d(TAG, "🟢 UI UPDATE - Text views: tSteps=${tSteps != null}, tEToken=${tEToken != null}, tNEToken=${tNEToken != null}")

        stepsProgBar?.progressMax = DAILY_STEP_GOAL.toFloat()
        eTokenProgBar?.progressMax = MONTHLY_TOKEN_EXCHANGE_LIMIT.toFloat()
        neTokenProgBar?.progressMax = (60 - MONTHLY_TOKEN_EXCHANGE_LIMIT).toFloat()
        
        Log.d(TAG, "🟢 UI UPDATE - Setting progress bars: steps=${tokenCalculation.steps}, eTokens=${tokenCalculation.exchangeableTokens}, neTokens=${tokenCalculation.nonExchangeableTokens}")
        
        stepsProgBar?.setProgressWithAnimation(tokenCalculation.steps.toFloat(), 1000)
        eTokenProgBar?.setProgressWithAnimation(tokenCalculation.exchangeableTokens.toFloat(), 1000)
        neTokenProgBar?.setProgressWithAnimation(tokenCalculation.nonExchangeableTokens.toFloat(), 1000)
        
        Log.d(TAG, "🟢 UI UPDATE - Setting text views")
        tSteps?.text = "${tokenCalculation.steps}/$DAILY_STEP_GOAL"
        tEToken?.text = "${tokenCalculation.exchangeableTokens}/$MONTHLY_TOKEN_EXCHANGE_LIMIT"
        tNEToken?.text = "${tokenCalculation.nonExchangeableTokens}/${60 - MONTHLY_TOKEN_EXCHANGE_LIMIT}"
        
        Log.d(TAG, "🟢 UI UPDATE - All UI elements updated successfully")
    }

    /**
     * Updates the UI with a specific step count value. This method is used
     * when setting step counts from external sources (like Google Fit) rather
     * than from sensor events.
     * 
     * @param stepCount The step count to display in the UI
     */
    private fun updateUIWithStepCount(stepCount: Int) {
        Log.d(TAG, "=== updateUIWithStepCount called with stepCount: $stepCount ===")
        Log.d(TAG, "🚫 DEVICE DATA UI UPDATE DISABLED: Preventing local data from overriding server data")
        
        // Always return early to prevent device data from overriding server data
        // Server data should be the single source of truth for UI display
        return
    }

    /**
     * Handles the result of permission requests. This method is called by the system
     * after the user responds to permission dialogs. It processes the results and
     * takes appropriate action based on whether permissions were granted or denied.
     * 
     * @param requestCode The request code passed to requestPermissions()
     * @param permissions The requested permissions
     * @param grantResults The grant results for the corresponding permissions
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        Log.d(TAG, "=== onRequestPermissionsResult called ===")
        Log.d(TAG, "Permission result: requestCode=$requestCode, permissions=${permissions.joinToString()}, grantResults=${grantResults.joinToString()}")
        
        when (requestCode) {
            REQUEST_ACTIVITY_RECOGNITION_PERMISSION -> {
                Log.d(TAG, "Handling REQUEST_ACTIVITY_RECOGNITION_PERMISSION result")
                var allPermissionsGranted = true
                val permanentlyDenied = mutableListOf<String>()
                
                for (i in permissions.indices) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        allPermissionsGranted = false
                        
                        // Check if permission is permanently denied
                        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i])) {
                            permanentlyDenied.add(permissions[i])
                        }
                        
                        when (permissions[i]) {
                            Manifest.permission.ACTIVITY_RECOGNITION -> {
                                Log.w(TAG, "ACTIVITY_RECOGNITION permission denied")
                                Toast.makeText(this, "Activity recognition permission denied. Step counting may not work properly.", Toast.LENGTH_LONG).show()
                            }
                            Manifest.permission.BODY_SENSORS -> {
                                Log.w(TAG, "BODY_SENSORS permission denied")
                                Toast.makeText(this, "Body sensors permission denied. Some sensor features may not work.", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Log.d(TAG, "${permissions[i]} permission granted")
                    }
                }
                
                Log.d(TAG, "Permission result summary - allPermissionsGranted: $allPermissionsGranted, permanentlyDenied: ${permanentlyDenied.joinToString()}")
                
                if (allPermissionsGranted) {
                    Log.d(TAG, "All permissions granted, proceeding with setup")
                    Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Calling markFirstRunComplete() and setupGoogleFitAndSensors()")
                    markFirstRunComplete() // Mark first run as complete when permissions are granted
                    setupGoogleFitAndSensors()
                } else {
                    // Some permissions denied, but still try to setup with available features
                    Log.w(TAG, "Some permissions denied, proceeding with limited functionality")
                    Toast.makeText(this, "Some permissions denied. App will work with limited functionality.", Toast.LENGTH_LONG).show()
                    
                    // Show dialog to help users enable permissions
                    showPermissionSettingsDialog()
                    Log.d(TAG, "Calling markFirstRunComplete() and setupGoogleFitAndSensors()")
                    markFirstRunComplete() // Mark first run as complete even when some permissions are denied
                    setupGoogleFitAndSensors()
                }
                
                // Handle permanently denied permissions
                if (permanentlyDenied.isNotEmpty()) {
                    Log.w(TAG, "Permanently denied permissions: ${permanentlyDenied.joinToString()}")
                    handlePermanentlyDeniedPermissions(permanentlyDenied)
                }
                
                // Update permission indicators after permission result
                updatePermissionIndicators()
            }
            else -> {
                // Handle other permission requests if any
                Log.d(TAG, "Handling other permission request result")
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Handles the result of activities started for result, particularly Google Sign-In.
     * This method processes the results of authentication attempts and sets up
     * Google Fit integration accordingly.
     * 
     * @param requestCode The request code passed to startActivityForResult()
     * @param resultCode The result code returned by the child activity
     * @param data Intent containing result data
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "=== onActivityResult called ===")
        Log.d(TAG, "Activity result: requestCode=$requestCode, resultCode=$resultCode")
        
        when (requestCode) {
            REQUEST_GOOGLE_SIGN_IN -> {
                Log.d(TAG, "=== Processing Google Sign-In result ===")
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                
                if (task.isSuccessful) {
                    try {
                        val account = task.getResult(ApiException::class.java)
                        Log.d(TAG, "✅ Google Sign-In SUCCESSFUL")
                        Log.d(TAG, "Account email: ${account.email}")
                        Log.d(TAG, "Account ID: ${account.id}")
                        Log.d(TAG, "Account display name: ${account.displayName}")
                        Log.d(TAG, "Account ID token: ${if (account.idToken != null) "Present" else "Missing"}")
                        Log.d(TAG, "Account granted scopes: ${account.grantedScopes}")
                        
                        googleSignInAttempted = false // Reset flag on success
                        setupGoogleFit()
                    } catch (e: ApiException) {
                        Log.e(TAG, "❌ Google Sign-In FAILED with ApiException")
                        Log.e(TAG, "Status code: ${e.statusCode}")
                        Log.e(TAG, "Status message: ${e.statusMessage}")
                        Log.e(TAG, "Error details: ${e.message}")
                        
                        // Log detailed status code meanings
                        when (e.statusCode) {
                            GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> {
                                Log.e(TAG, "🔴 SIGN_IN_CANCELLED: User cancelled the sign-in flow")
                            }
                            GoogleSignInStatusCodes.NETWORK_ERROR -> {
                                Log.e(TAG, "🔴 NETWORK_ERROR: Network error occurred during sign-in")
                            }
                            GoogleSignInStatusCodes.INVALID_ACCOUNT -> {
                                Log.e(TAG, "🔴 INVALID_ACCOUNT: Account is invalid")
                            }
                            GoogleSignInStatusCodes.SIGN_IN_REQUIRED -> {
                                Log.e(TAG, "🔴 SIGN_IN_REQUIRED: Sign-in is required")
                            }
                            GoogleSignInStatusCodes.SIGN_IN_FAILED -> {
                                Log.e(TAG, "🔴 SIGN_IN_FAILED: Sign-in failed")
                            }
                            GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS -> {
                                Log.e(TAG, "🔴 SIGN_IN_CURRENTLY_IN_PROGRESS: Sign-in already in progress")
                            }
                            GoogleSignInStatusCodes.DEVELOPER_ERROR -> {
                                Log.e(TAG, "🔴 DEVELOPER_ERROR: Developer configuration error")
                                Log.e(TAG, "This usually means:")
                                Log.e(TAG, "  - SHA-1 fingerprint mismatch")
                                Log.e(TAG, "  - OAuth client ID not configured")
                                Log.e(TAG, "  - Package name mismatch")
                                Log.e(TAG, "  - Google Cloud project not properly set up")
                            }
                            else -> {
                                Log.e(TAG, "🔴 UNKNOWN_ERROR: Unknown status code ${e.statusCode}")
                            }
                        }
                        
                        googleSignInAttempted = true
                        Toast.makeText(this, "Google Sign-In failed (${e.statusCode}). Using device sensors instead.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Log.e(TAG, "❌ Google Sign-In task FAILED")
                    Log.e(TAG, "Task exception: ${task.exception}")
                    Log.e(TAG, "Task is successful: ${task.isSuccessful}")
                    Log.e(TAG, "Task is complete: ${task.isComplete}")
                    
                    if (task.exception != null) {
                        Log.e(TAG, "Exception details: ${task.exception?.message}")
                        Log.e(TAG, "Exception type: ${task.exception?.javaClass?.simpleName}")
                    }
                    
                    googleSignInAttempted = true
                    Toast.makeText(this, "Google Sign-In failed. Using device sensors instead.", Toast.LENGTH_LONG).show()
                }
                
                // Update permission indicators after Google Sign-In result
                updatePermissionIndicators()
            }
            REQUEST_PERMISSIONS_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    Log.d(TAG, "Google Fit permissions granted")
                    
                    // Initialize WorkManager for step counting since Google Fit permissions are now granted
                    val stepCountWorkRequest = PeriodicWorkRequestBuilder<StepCountWorker>(6, TimeUnit.HOURS)
                        .build()
                    WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                        "step_sync_work",
                        ExistingPeriodicWorkPolicy.KEEP,
                        stepCountWorkRequest
                    )
                    Log.d(TAG, "StepCountWorker initialized and enqueued after permission grant with unique policy")
                    
                    // Also schedule an immediate one-time execution for testing
                    val immediateWorkRequest = OneTimeWorkRequestBuilder<StepCountWorker>()
                        .build()
                    WorkManager.getInstance(this).enqueue(immediateWorkRequest)
                    Log.d(TAG, "Immediate StepCountWorker execution scheduled after permission grant")
                    
                    setupGoogleFit()
                } else {
                    Log.d(TAG, "Google Fit permissions denied")
                    Toast.makeText(this, "Google Fit permissions denied. Using device sensors instead.", Toast.LENGTH_LONG).show()
                    // Don't call readStepCount() here - let sensor updates handle the UI
                }
                // Update permission indicators after Google Fit permission result
                updatePermissionIndicators()
            }
        }
    }

    /**
     * Resets the first run flag to true, which will cause the welcome dialog
     * to show again on the next app launch. Used for debugging and testing.
     */
    private fun resetFirstRunFlag() {
        val sharedPref = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        sharedPref.edit().putBoolean("isFirstRun", true).apply()
        Log.d(TAG, "=== resetFirstRunFlag called - isFirstRun set to true ===")
    }

    /**
     * Clears all stored preferences and resets in-memory state variables.
     * This is used by the profile button double-tap gesture for debugging.
     */
    private fun clearAllPreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        
        // Reset in-memory state to initial values
        initialStepCount = -1
        currentStepCount = 0
        googleSignInAttempted = false
        
        Log.d(TAG, "All preferences cleared and state reset")
        Toast.makeText(this, "All preferences cleared", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Resets the Google Sign-In attempted flag to false, allowing
     * the app to attempt Google Sign-In again. Used for debugging.
     */
    private fun resetGoogleSignInFlag() {
        googleSignInAttempted = false
        Log.d(TAG, "=== resetGoogleSignInFlag called - googleSignInAttempted set to false ===")
    }
    
        /**
         * Updates the visual permission indicators to reflect the current status
         * of all required permissions. This provides users with immediate feedback
         * about which permissions are granted and which are pending.
         */
        private fun updatePermissionIndicators() {
            Log.d(TAG, "=== updatePermissionIndicators called ===")

            // Create local variables for all permission indicator views to avoid smart cast issues
            // This prevents crashes when accessing nullable view references
            val currentActivityRecognitionIcon = activityRecognitionIcon
            val currentActivityRecognitionStatus = activityRecognitionStatus
            val currentBodySensorsIcon = bodySensorsIcon
            val currentBodySensorsStatus = bodySensorsStatus
            val currentGoogleFitIcon = googleFitIcon
            val currentGoogleFitStatus = googleFitStatus
            val currentGoogleAccountIcon = googleAccountIcon
            val currentGoogleAccountStatus = googleAccountStatus

            // Check if all permission indicators exist before updating
            // This prevents crashes if the layout is incomplete
            if (currentActivityRecognitionIcon == null || currentActivityRecognitionStatus == null ||
                currentBodySensorsIcon == null || currentBodySensorsStatus == null ||
                currentGoogleFitIcon == null || currentGoogleFitStatus == null ||
                currentGoogleAccountIcon == null || currentGoogleAccountStatus == null) {
                Log.w(TAG, "Cannot update permission indicators - some views are null")
                return
            }

        // Update Activity Recognition permission indicator
        val activityRecognitionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        updatePermissionIndicator(
            currentActivityRecognitionIcon,
            currentActivityRecognitionStatus,
            activityRecognitionGranted,
            "Granted",
            "Pending"
        )

        // Update Body Sensors permission indicator
        val bodySensorsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
        updatePermissionIndicator(
            currentBodySensorsIcon,
            currentBodySensorsStatus,
            bodySensorsGranted,
            "Granted",
            "Pending"
        )

        // Update Google Fit permission indicator
        val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(this)
        val currentFitnessOptions = fitnessOptions
        val googleFitGranted = if (lastSignedInAccount != null && currentFitnessOptions != null) {
            GoogleSignIn.hasPermissions(lastSignedInAccount, currentFitnessOptions)
        } else {
            false
        }
        updatePermissionIndicator(
            currentGoogleFitIcon,
            currentGoogleFitStatus,
            googleFitGranted,
            "Granted",
            "Pending"
        )

        // Update Google Account status indicator
        val googleAccountSignedIn = lastSignedInAccount != null
        updatePermissionIndicator(
            currentGoogleAccountIcon,
            currentGoogleAccountStatus,
            googleAccountSignedIn,
            "Signed In",
            "Not Signed In"
        )

        Log.d(TAG, "Permission indicators updated - Activity: $activityRecognitionGranted, Sensors: $bodySensorsGranted, Fit: $googleFitGranted, Account: $googleAccountSignedIn")
    }
    
    private fun updatePermissionIndicator(icon: ImageView?, statusText: TextView?, isGranted: Boolean, grantedText: String, pendingText: String) {
        // Add null checks to prevent crashes
        if (icon == null || statusText == null) {
            Log.w(TAG, "Cannot update permission indicator - icon or statusText is null")
            return
        }
        
        try {
            if (isGranted) {
                icon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_light))
                statusText.text = grantedText
                statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            } else {
                icon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_light))
                statusText.text = pendingText
                statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating permission indicator: ${e.message}", e)
        }
    }

    /**
     * Logs the app's SHA-1 signature for debugging Google Sign-In issues.
     * This signature must match the one configured in Google Cloud Console
     * for Google Sign-In to work properly.
     */
    private fun logAppSignature() {
        try {
            val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            val signatures = info.signatures
            if (signatures != null) {
                for (signature in signatures) {
                    val md = MessageDigest.getInstance("SHA-1")
                    md.update(signature.toByteArray())
                    val hash = String.format("%040x", BigInteger(1, md.digest()))
                    Log.d(TAG, "App Signature (SHA-1): $hash")
                }
            } else {
                Log.w(TAG, "App signatures array is null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app signature: ${e.message}", e)
        }
    }
}

