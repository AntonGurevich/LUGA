package silverbackgarden.example.luga.ui.dashboard

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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

// AndroidX and support library imports
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager

// Local app imports
import silverbackgarden.example.luga.AuthManager
import silverbackgarden.example.luga.R
import silverbackgarden.example.luga.StepCountWorker
import silverbackgarden.example.luga.BikeData
import silverbackgarden.example.luga.StepData
import silverbackgarden.example.luga.SupabaseUserManager
import silverbackgarden.example.luga.SwimData
import silverbackgarden.example.luga.TokenCalculation
import silverbackgarden.example.luga.TokenRecord
import silverbackgarden.example.luga.health.HealthConnectAvailability
import silverbackgarden.example.luga.ui.CompanyRulesCache
import silverbackgarden.example.luga.ui.MainTabActivity

// Third-party UI library for circular progress bars
import com.mikhaellopez.circularprogressbar.CircularProgressBar

// Java utility imports for date formatting, collections
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

/**
 * Dashboard tab: step counting, token calculation, and Health Connect integration.
 * Migrated from the former CentralActivity (now hosted as a tab inside MainTabActivity).
 * Implements SensorEventListener to receive step sensor updates from the device.
 */
class DashboardFragment : Fragment(), SensorEventListener {

    // UI Elements - Circular progress bars for visual representation
    private var stepsProgBar: CircularProgressBar? = null      // Shows daily step progress
    private var cyclingProgBar: CircularProgressBar? = null    // Shows cycling distance progress
    private var swimmingProgBar: CircularProgressBar? = null   // Shows swimming distance progress

    // UI Elements - Text views for displaying numerical values
    private var tSteps: TextView? = null      // Displays current step count
    private var tCycling: TextView? = null    // Displays cycling distance
    private var tSwimming: TextView? = null   // Displays swimming distance
    private var tEToken: TextView? = null     // Displays exchangeable (reimbursable) token count
    private var tNEToken: TextView? = null    // Displays non-exchangeable token count

    // UI Elements - Token wallet card (Phase 4 redesign)
    private var tokenGaugeFill: View? = null
    private var tokenGaugePercentText: TextView? = null
    private var tvPaceProjection: TextView? = null
    private var nudgeBanner: TextView? = null

    // UI Elements - Header (Phase 4 redesign)
    private var tvGreeting: TextView? = null
    private var tvDate: TextView? = null

    // Permission Indicator UI Elements - Visual status indicators for app permissions
    private var activityRecognitionIcon: ImageView? = null      // Icon for activity recognition permission
    private var activityRecognitionStatus: TextView? = null     // Status text for activity recognition
    private var bodySensorsIcon: ImageView? = null             // Icon for body sensors permission
    private var bodySensorsStatus: TextView? = null            // Status text for body sensors
    private var healthConnectAvailableIcon: ImageView? = null   // Icon for Health Connect availability
    private var healthConnectAvailableStatus: TextView? = null  // Status text for Health Connect availability
    private var healthConnectPermissionIcon: ImageView? = null  // Icon for Health Connect permission grant
    private var healthConnectPermissionStatus: TextView? = null // Status text for Health Connect permission grant

    // Sensor management variables
    private var sensorManager: SensorManager? = null           // Manages device sensors
    private var stepCountSensor: Sensor? = null               // Step counter sensor (cumulative)
    private var stepDetectorSensor: Sensor? = null            // Step detector sensor (per-step)
    private var initialStepCount: Int = -1                    // Baseline step count for calculation (fallback)
    private var currentStepCount: Int = 0                     // Current step count since app start

    private var deviceSensorSteps: Int = 0                    // Steps detected by device sensors since app start

    // Health Connect integration
    private val healthConnectPermissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    )
    private val healthConnectPermissionLauncher =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            if (!isAdded) return@registerForActivityResult
            if (granted.containsAll(healthConnectPermissions)) {
                Log.d(TAG, "Health Connect permissions granted")
                onHealthConnectPermissionsGranted()
            } else {
                Log.w(TAG, "Health Connect permissions not fully granted: $granted")
                setupDeviceSensorsOnly()
            }
            updatePermissionIndicators()
        }

    // Token data management
    private val supabaseUserManager = SupabaseUserManager()
    private lateinit var authManager: AuthManager
    private var currentTokenData: TokenRecord? = null
    private var serverDataLoaded = false

    companion object {
        // Request codes for permission results
        private const val REQUEST_ACTIVITY_RECOGNITION_PERMISSION = 1002 // Activity recognition permission

        // App configuration constants
        private const val MONTHLY_TOKEN_EXCHANGE_LIMIT = 30             // Maximum tokens that can be exchanged per month
        private const val DAILY_STEP_GOAL = 10000                       // Daily step target for token calculation
        private const val DAILY_CYCLING_GOAL = 15000                    // Daily cycling target for token calculation (15km)
        private const val DAILY_SWIMMING_GOAL = 1000                    // Daily swimming target for token calculation (1km)

        // Logging and preferences constants
        private const val TAG = "DashboardFragment"                     // Log tag for debugging
        private const val PREFS_NAME = "AppPrefs"                       // Shared preferences file name
        private const val KEY_INITIAL_STEP_COUNT = "initialStepCount"   // Key for storing initial step count
        private const val KEY_LAST_STEP_RESET_DATE = "lastStepResetDate" // Key for storing last reset date

        private const val RING_ANIMATION_DURATION_MS = 350L             // Snappier fill animation for activity rings
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    /**
     * Called after the fragment's view is created. Initializes the UI, loads saved state,
     * and sets up the initial app configuration including permissions and sensors.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "=== onViewCreated called ===")

        // Load persisted step counter state from shared preferences
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
        val activityRecognitionGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        val bodySensorsGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "onViewCreated - Current permissions - ACTIVITY_RECOGNITION: $activityRecognitionGranted, BODY_SENSORS: $bodySensorsGranted")

        // Log current first run status and permission request history
        val firstRunStatus = isFirstRun()
        val hasRequestedBefore = hasRequestedPermissionsBefore()
        Log.d(TAG, "onViewCreated - First run status: $firstRunStatus, Has requested before: $hasRequestedBefore")

        // Initialize AuthManager with proper context
        authManager = AuthManager(requireContext())

        // Initialize UI elements first before proceeding with other setup
        Log.d(TAG, "Calling initializeUI()")
        initializeUI()

        // Validate that critical UI elements were successfully initialized.
        if (stepsProgBar == null || cyclingProgBar == null || swimmingProgBar == null ||
            tokenGaugeFill == null ||
            tSteps == null || tCycling == null || tSwimming == null ||
            tEToken == null) {
            Log.e(TAG, "Critical UI elements failed to initialize")
            Toast.makeText(requireContext(), "Error: Required UI elements not found. Please check the app layout.", Toast.LENGTH_LONG).show()
            return
        }

        // Check and request necessary permissions before initializing sensors
        Log.d(TAG, "Calling checkAndRequestPermissions()")
        checkAndRequestPermissions()

        // Header greeting + date (Phase 4)
        updateGreetingHeader()

        // Load token data from server
        loadTokenData()
    }

    /**
     * Populates the "Hello, {name}" greeting and the date subtitle in the header.
     */
    private fun updateGreetingHeader() {
        val prefs = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val name = prefs.getString("name", null)
        tvGreeting?.text = if (name.isNullOrBlank()) "Hello, there" else "Hello, $name"
        tvDate?.text = SimpleDateFormat("EEEE · MMM d", Locale.getDefault()).format(Date()).uppercase(Locale.getDefault())
        view?.findViewById<TextView>(R.id.tvWalletMonth)?.text =
            SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
    }

    /**
     * Toggles the visibility of sensor status indicators.
     * (Debug view — reachable via the header bell now that the ActionBar menu is gone.)
     */
    private fun toggleSensorStatusVisibility() {
        Log.d(TAG, "=== toggleSensorStatusVisibility called ===")

        val permissionIndicators = requireView().findViewById<View>(R.id.permissionIndicators)
        if (permissionIndicators != null) {
            val isVisible = permissionIndicators.visibility == View.VISIBLE
            permissionIndicators.visibility = if (isVisible) View.GONE else View.VISIBLE

            val message = if (isVisible) "Sensor indicators hidden" else "Sensor indicators shown"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Sensor indicators visibility changed to: ${!isVisible}")
        } else {
            Log.e(TAG, "Permission indicators layout not found")
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "=== onStart called ===")

        val prefs = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val needsRefresh = prefs.getBoolean("token_data_needs_refresh", false)
        if (needsRefresh) {
            Log.d(TAG, "Token data refresh flag detected in onStart - refreshing token data after data sync")
            prefs.edit().putBoolean("token_data_needs_refresh", false).apply()
            Handler(Looper.getMainLooper()).postDelayed({
                Log.d(TAG, "Refreshing token data after data sync completion (from onStart)")
                loadTokenData()
            }, 1500)
        }
    }

    /**
     * Initializes all UI elements by finding them in the layout and setting up
     * their event listeners.
     */
    private fun initializeUI() {
        Log.d(TAG, "=== initializeUI called ===")

        try {
            stepsProgBar = requireView().findViewById(R.id.circularProgressBarSteps)
            if (stepsProgBar == null) {
                Log.e(TAG, "circularProgressBarSteps not found in layout")
                return
            }

            cyclingProgBar = requireView().findViewById(R.id.circularProgressBarCycling)
            if (cyclingProgBar == null) {
                Log.e(TAG, "circularProgressBarCycling not found in layout")
                return
            }

            swimmingProgBar = requireView().findViewById(R.id.circularProgressBarSwimming)
            if (swimmingProgBar == null) {
                Log.e(TAG, "circularProgressBarSwimming not found in layout")
                return
            }

            tokenGaugeFill = requireView().findViewById(R.id.tokenGaugeFill)
            if (tokenGaugeFill == null) {
                Log.e(TAG, "tokenGaugeFill not found in layout")
                return
            }
            tokenGaugePercentText = requireView().findViewById(R.id.tokenGaugePercentText)
            tvPaceProjection = requireView().findViewById(R.id.tvPaceProjection)
            nudgeBanner = requireView().findViewById(R.id.nudgeBanner)
            tvGreeting = requireView().findViewById(R.id.tvGreeting)
            tvDate = requireView().findViewById(R.id.tvDate)

            tSteps = requireView().findViewById(R.id.tvStepsProgress)
            if (tSteps == null) {
                Log.e(TAG, "tvStepsProgress not found in layout")
                return
            }

            tCycling = requireView().findViewById(R.id.tvCyclingProgress)
            if (tCycling == null) {
                Log.e(TAG, "tvCyclingProgress not found in layout")
                return
            }

            tSwimming = requireView().findViewById(R.id.tvSwimmingProgress)
            if (tSwimming == null) {
                Log.e(TAG, "tvSwimmingProgress not found in layout")
                return
            }

            tEToken = requireView().findViewById(R.id.tvExTokensBal)
            if (tEToken == null) {
                Log.e(TAG, "tvExTokensBal not found in layout")
                return
            }

            tNEToken = requireView().findViewById(R.id.tvNonExTokensBal)
            if (tNEToken == null) {
                Log.w(TAG, "tvNonExTokensBal not found in layout (non-ex block optional)")
            }

            activityRecognitionIcon = requireView().findViewById(R.id.activityRecognitionIcon)
            activityRecognitionStatus = requireView().findViewById(R.id.activityRecognitionStatus)
            bodySensorsIcon = requireView().findViewById(R.id.bodySensorsIcon)
            bodySensorsStatus = requireView().findViewById(R.id.bodySensorsStatus)
            healthConnectAvailableIcon = requireView().findViewById(R.id.healthConnectAvailableIcon)
            healthConnectAvailableStatus = requireView().findViewById(R.id.healthConnectAvailableStatus)
            healthConnectPermissionIcon = requireView().findViewById(R.id.healthConnectPermissionIcon)
            healthConnectPermissionStatus = requireView().findViewById(R.id.healthConnectPermissionStatus)

            if (activityRecognitionIcon == null || activityRecognitionStatus == null ||
                bodySensorsIcon == null || bodySensorsStatus == null ||
                healthConnectAvailableIcon == null || healthConnectAvailableStatus == null ||
                healthConnectPermissionIcon == null || healthConnectPermissionStatus == null) {
                Log.w(TAG, "Some permission indicators not found in layout")
            }

            val permissionIndicators = requireView().findViewById<View>(R.id.permissionIndicators)
            if (permissionIndicators != null) {
                permissionIndicators.visibility = View.GONE
                Log.d(TAG, "Sensor indicators hidden by default")
            }

            // Phase 5: rings now open the Stats tab (real content) instead of the
            // legacy StepDataViewActivity chart screen.
            stepsProgBar?.setOnClickListener { (activity as? MainTabActivity)?.switchToStatsTab() }
            cyclingProgBar?.setOnClickListener { (activity as? MainTabActivity)?.switchToStatsTab() }
            swimmingProgBar?.setOnClickListener { (activity as? MainTabActivity)?.switchToStatsTab() }
            requireView().findViewById<View>(R.id.btnViewStats)?.setOnClickListener {
                (activity as? MainTabActivity)?.switchToStatsTab()
            }
            requireView().findViewById<View>(R.id.btnTokensEarnings)?.setOnClickListener {
                (activity as? MainTabActivity)?.switchToTokensTab()
            }
            // Bell doubles as the sensor-status debug toggle (formerly an ActionBar menu item).
            requireView().findViewById<View>(R.id.headerBell)?.setOnClickListener {
                toggleSensorStatusVisibility()
            }

            stepsProgBar?.setOnLongClickListener {
                Log.d(TAG, "Step counter long pressed, showing monthly progress and forcing permission request")
                showMonthlyProgress()
                forceRequestPermissions()
                true
            }

            var stepTapCount = 0
            var lastStepTapTime = 0L
            stepsProgBar?.setOnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastStepTapTime < 1000) {
                        stepTapCount++
                        if (stepTapCount == 2) {
                            Log.d(TAG, "Step counter double tap detected, resetting step counter")
                            resetStepCounter()
                            Toast.makeText(requireContext(), "Step counter reset", Toast.LENGTH_SHORT).show()
                            stepTapCount = 0
                        }
                    } else {
                        stepTapCount = 1
                    }
                    lastStepTapTime = currentTime
                }
                false
            }

            // Phase 6: wallet card now opens the Tokens tab (real content) instead of
            // the legacy StepDataViewActivity chart screen.
            requireView().findViewById<View>(R.id.tokenWalletCard)?.setOnClickListener {
                (activity as? MainTabActivity)?.switchToTokensTab()
            }

            if (activityRecognitionIcon != null && activityRecognitionStatus != null &&
                bodySensorsIcon != null && bodySensorsStatus != null &&
                healthConnectAvailableIcon != null && healthConnectAvailableStatus != null &&
                healthConnectPermissionIcon != null && healthConnectPermissionStatus != null) {
                updatePermissionIndicators()
            }

            Log.d(TAG, "=== initializeUI completed successfully ===")

        } catch (e: Exception) {
            Log.e(TAG, "Error in initializeUI: ${e.message}", e)
            Toast.makeText(requireContext(), "Error initializing UI: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Checks the current permission status and requests necessary permissions.
     */
    private fun checkAndRequestPermissions() {
        Log.d(TAG, "=== Starting permission check ===")

        val currentApiVersion = android.os.Build.VERSION.SDK_INT
        Log.d(TAG, "Current API version: $currentApiVersion")

        if (currentApiVersion < android.os.Build.VERSION_CODES.M) {
            Log.d(TAG, "Running on pre-Marshmallow, permissions granted at install time")
            Toast.makeText(requireContext(), "Permissions granted at install time", Toast.LENGTH_SHORT).show()
            setupHealthConnectAndSensors()
            return
        }

        val firstRunStatus = isFirstRun()
        Log.d(TAG, "First run status: $firstRunStatus")
        if (firstRunStatus) {
            Log.d(TAG, "First run detected, showing welcome dialog")
            showWelcomeDialog()
            return
        }

        val activityRecognitionGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        val bodySensorsGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED

        Log.d(TAG, "ACTIVITY_RECOGNITION permission status: $activityRecognitionGranted")
        Log.d(TAG, "BODY_SENSORS permission status: $bodySensorsGranted")

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

            val shouldShowRationale = permissionsToRequest.any { shouldShowPermissionRationale(it) }

            if (shouldShowRationale) {
                Log.d(TAG, "Showing permission rationale dialog")
                showPermissionRationaleDialog(permissionsToRequest.toTypedArray())
            } else {
                Log.d(TAG, "Directly requesting permissions")
                Toast.makeText(requireContext(), "Requesting permissions: ${permissionsToRequest.joinToString()}", Toast.LENGTH_SHORT).show()
                markPermissionsRequested()
                markFirstRunComplete()
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    permissionsToRequest.toTypedArray(),
                    REQUEST_ACTIVITY_RECOGNITION_PERMISSION
                )
            }
        } else {
            Log.d(TAG, "All permissions already granted, proceeding with setup")
            Toast.makeText(requireContext(), "All permissions granted, setting up step counting", Toast.LENGTH_SHORT).show()
            setupHealthConnectAndSensors()
        }
    }

    private fun isFirstRun(): Boolean {
        val sharedPref = requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val result = sharedPref.getBoolean("isFirstRun", true)
        Log.d(TAG, "isFirstRun() called, returning: $result")
        return result
    }

    private fun markFirstRunComplete() {
        val sharedPref = requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        sharedPref.edit().putBoolean("isFirstRun", false).apply()
        Log.d(TAG, "=== markFirstRunComplete called - isFirstRun set to false ===")
    }

    private fun showWelcomeDialog() {
        Log.d(TAG, "=== showWelcomeDialog called ===")
        val message = "Welcome to Acteamity!\n\n" +
                "This app needs the following permissions to count your steps:\n\n" +
                "• Physical Activity: To detect when you're walking\n" +
                "• Body Sensors: To access step counting sensors\n\n" +
                "These permissions are essential for the app to function properly."

        Log.d(TAG, "Showing welcome dialog with message: $message")

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Welcome!")
            .setMessage(message)
            .setPositiveButton("Grant Permissions") { _, _ ->
                Log.d(TAG, "User clicked 'Grant Permissions' in welcome dialog")
                proceedWithPermissionRequest()
            }
            .setCancelable(false)
            .show()
    }

    private fun proceedWithPermissionRequest() {
        Log.d(TAG, "=== proceedWithPermissionRequest called ===")
        val activityRecognitionGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        val bodySensorsGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED

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
            Toast.makeText(requireContext(), "First run: Requesting permissions: ${permissionsToRequest.joinToString()}", Toast.LENGTH_SHORT).show()
            markPermissionsRequested()
            markFirstRunComplete()
            ActivityCompat.requestPermissions(
                requireActivity(),
                permissionsToRequest.toTypedArray(),
                REQUEST_ACTIVITY_RECOGNITION_PERMISSION
            )
        } else {
            markFirstRunComplete()
            setupHealthConnectAndSensors()
        }
    }

    private fun hasRequestedPermissionsBefore(): Boolean {
        val sharedPref = requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val result = sharedPref.getBoolean("hasRequestedPermissions", false)
        Log.d(TAG, "hasRequestedPermissionsBefore() called, returning: $result")
        return result
    }

    private fun markPermissionsRequested() {
        val sharedPref = requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        sharedPref.edit().putBoolean("hasRequestedPermissions", true).apply()
        Log.d(TAG, "=== markPermissionsRequested called - hasRequestedPermissions set to true ===")
    }

    private fun forceRequestPermissions() {
        Log.d(TAG, "=== Force requesting permissions ===")

        val permissionsToRequest = arrayOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.BODY_SENSORS
        )

        Log.d(TAG, "Force requesting permissions: ${permissionsToRequest.joinToString()}")
        Toast.makeText(requireContext(), "Force requesting all permissions", Toast.LENGTH_SHORT).show()

        markPermissionsRequested()
        markFirstRunComplete()
        ActivityCompat.requestPermissions(
            requireActivity(),
            permissionsToRequest,
            REQUEST_ACTIVITY_RECOGNITION_PERMISSION
        )
    }

    private fun shouldShowPermissionRationale(permission: String): Boolean {
        val result = ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), permission)
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

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Permissions Required")
            .setMessage(message)
            .setPositiveButton("Grant Permissions") { _, _ ->
                Log.d(TAG, "User clicked 'Grant Permissions' in rationale dialog")
                markPermissionsRequested()
                markFirstRunComplete()
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    permissions,
                    REQUEST_ACTIVITY_RECOGNITION_PERMISSION
                )
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                Log.d(TAG, "User clicked 'Cancel' in rationale dialog")
                dialog.dismiss()
                Toast.makeText(requireContext(), "App cannot function without these permissions", Toast.LENGTH_LONG).show()
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

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Permissions Permanently Denied")
            .setMessage(message)
            .setPositiveButton("Go to Settings") { _, _ ->
                Log.d(TAG, "User clicked 'Go to Settings' in permanently denied dialog")
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = android.net.Uri.fromParts("package", requireContext().packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Close") { dialog, _ ->
                Log.d(TAG, "User clicked 'Close' in permanently denied dialog")
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Gates on Health Connect availability + permission grant, then proceeds to real-time
     * device sensors and the background sync worker. Replaces the old Google Fit /
     * Google Sign-In flow — Health Connect uses on-device permissions, no OAuth sign-in.
     */
    private fun setupHealthConnectAndSensors() {
        Log.d(TAG, "=== setupHealthConnectAndSensors called ===")

        when (HealthConnectAvailability.check(requireContext())) {
            HealthConnectAvailability.Status.AVAILABLE -> checkHealthConnectPermissionsAndProceed()
            HealthConnectAvailability.Status.NOT_INSTALLED, HealthConnectAvailability.Status.UPDATE_REQUIRED -> {
                Log.w(TAG, "Health Connect not available; falling back to device sensors only")
                setupDeviceSensorsOnly()
            }
        }
        updatePermissionIndicators()
    }

    private fun checkHealthConnectPermissionsAndProceed() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val client = HealthConnectClient.getOrCreate(requireContext())
                val granted = client.permissionController.getGrantedPermissions()
                if (!isAdded) return@launch
                if (granted.containsAll(healthConnectPermissions)) {
                    Log.d(TAG, "Health Connect permissions already granted")
                    onHealthConnectPermissionsGranted()
                } else {
                    Log.d(TAG, "Requesting Health Connect permissions")
                    healthConnectPermissionLauncher.launch(healthConnectPermissions)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking Health Connect permissions", e)
                if (isAdded) setupDeviceSensorsOnly()
            }
        }
    }

    private fun onHealthConnectPermissionsGranted() {
        setupDeviceSensors()
        scheduleStepCountWorker()
        readStepCount()
        updatePermissionIndicators()
    }

    /**
     * Sets up device sensors for real-time step detection.
     */
    private fun setupDeviceSensors() {
        Log.d(TAG, "=== setupDeviceSensors called ===")

        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCountSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepDetectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        if (stepCountSensor != null || stepDetectorSensor != null) {
            Log.d(TAG, "Device sensors available, registering listeners")

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
     * Sets up device sensors only when Health Connect is not available/authorized.
     */
    private fun setupDeviceSensorsOnly() {
        Log.d(TAG, "=== setupDeviceSensorsOnly called ===")

        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCountSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepDetectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        if (stepCountSensor == null && stepDetectorSensor == null) {
            Log.w(TAG, "No step sensors available on this device")
            Toast.makeText(requireContext(), "No step sensors available on this device", Toast.LENGTH_SHORT).show()
        } else {
            setupDeviceSensors()
        }
    }

    /**
     * Schedules the periodic (2-hourly) background sync worker that reads from Health
     * Connect and pushes step/cycling/swimming data to Supabase.
     */
    private fun scheduleStepCountWorker() {
        Log.d(TAG, "=== scheduleStepCountWorker called ===")

        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val stepCountWorkRequest = PeriodicWorkRequestBuilder<StepCountWorker>(2, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            "step_sync_work",
            ExistingPeriodicWorkPolicy.UPDATE,
            stepCountWorkRequest
        )
        Log.d(TAG, "StepCountWorker initialized and enqueued with unique policy")

        // Also sync immediately on every Dashboard open so the server always has fresh
        // data when the user is looking at the app, rather than waiting up to 2 hours
        // for the next periodic window. KEEP avoids stacking runs if one is already
        // queued or in flight (e.g. rapid tab switches / reopens).
        val immediateWorkRequest = OneTimeWorkRequestBuilder<StepCountWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(requireContext()).enqueueUniqueWork(
            "step_sync_now",
            ExistingWorkPolicy.KEEP,
            immediateWorkRequest
        )
        Log.d(TAG, "Immediate StepCountWorker sync enqueued for Dashboard open")
    }

    /**
     * Loads token data from the server for the current user and month.
     */
    private fun loadTokenData() {
        Log.d(TAG, "Loading token data from server")

        if (!authManager.isLoggedIn()) {
            Log.w(TAG, "User not logged in, skipping token data load")
            return
        }

        val userId = authManager.getCurrentUserId()
        if (userId.isNullOrEmpty()) {
            Log.w(TAG, "No user ID available, skipping token data load")
            return
        }

        Log.d(TAG, "Fetching token data for user: $userId")

        // Refresh the session-scoped company rules cache (Phase 3); consumed by
        // Dashboard/Stats/Tokens once their real UI lands in later phases.
        CompanyRulesCache.load(supabaseUserManager, userId)

        supabaseUserManager.fetchTokenData(userId, object : SupabaseUserManager.DatabaseCallback<TokenRecord> {
            override fun onSuccess(result: TokenRecord) {
                if (!isAdded) return
                Log.d(TAG, "Token data loaded successfully: $result")
                currentTokenData = result
                updateUIWithTokenData(result)
            }

            override fun onError(error: String) {
                if (!isAdded) return
                Log.e(TAG, "Failed to load token data: $error")
                showTokenDataError(error)
            }
        })
    }

    /**
     * Refreshes token data from the server.
     */
    fun refreshTokenData() {
        Log.d(TAG, "Manually refreshing token data")
        loadTokenData()
    }

    /**
     * Shows error message when token data cannot be loaded from server.
     */
    private fun showTokenDataError(error: String) {
        Log.e(TAG, "Token data fetch error: $error")

        if (!error.contains("No token data", ignoreCase = true)) {
            Toast.makeText(requireContext(), "Unable to load token data: $error", Toast.LENGTH_LONG).show()
        }

        tEToken?.text = "0"
        tNEToken?.text = "0"
        tSteps?.text = "0/10000"
        tSwimming?.text = "0m/2000m"
        tCycling?.text = "0m/15000m"

        updateTokenGauge(0.0, 30.0)
        tvPaceProjection?.visibility = View.GONE
        nudgeBanner?.visibility = View.GONE

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
            progressMax = 15000f
        }

        serverDataLoaded = false
        currentTokenData = null
        Log.d(TAG, "SERVER DATA FLAG RESET: serverDataLoaded = false")
    }

    /**
     * Drives the token wallet gauge bar + percent text, using iOS's 3-tier color
     * logic (mint under 60%, yellow 60-85%, coral 85%+ of [tokenLimit]).
     */
    private fun updateTokenGauge(reimbursableTokens: Double, tokenLimit: Double) {
        val gauge = tokenGaugeFill ?: return
        val safeLimit = if (tokenLimit > 0) tokenLimit else 30.0
        val frac = (reimbursableTokens / safeLimit).coerceIn(0.0, 1.0)

        val colorRes = when {
            frac >= 0.85 -> R.color.act_error
            frac >= 0.6 -> R.color.act_warning
            else -> R.color.act_mint_500
        }
        (gauge.background as? android.graphics.drawable.GradientDrawable)
            ?.setColor(ContextCompat.getColor(requireContext(), colorRes))

        val track = gauge.parent as? View
        fun applyWidth() {
            val totalWidth = track?.width ?: 0
            if (totalWidth <= 0) return
            val params = gauge.layoutParams
            params.width = (totalWidth * frac).toInt()
            gauge.layoutParams = params
        }
        if ((track?.width ?: 0) > 0) applyWidth() else track?.post { applyWidth() }

        tokenGaugePercentText?.text = "${(frac * 100).toInt()}%"
        view?.findViewById<TextView>(R.id.tvGaugeLabel)?.text =
            if (reimbursableTokens >= safeLimit) "Monthly limit reached"
            else "${reimbursableTokens.toInt()} of ${safeLimit.toInt()} tokens this month"
    }

    /**
     * Shows "At this pace · ~X tokens by month end" once there's a non-zero
     * reimbursable balance to project from, mirroring iOS's paceProjection row.
     */
    private fun updatePaceProjection(reimbursableTokens: Double) {
        val cal = Calendar.getInstance()
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        if (dayOfMonth <= 0 || reimbursableTokens <= 0.0) {
            tvPaceProjection?.visibility = View.GONE
            return
        }
        val projected = (reimbursableTokens / dayOfMonth) * daysInMonth
        tvPaceProjection?.text = "At this pace · ~${Math.round(projected)} tokens by month end"
        tvPaceProjection?.visibility = View.VISIBLE
    }

    /**
     * Shows a nudge banner when the closest-to-completion tracked activity is
     * 70-100% of the way to its next token, mirroring iOS's nudge banner.
     */
    private fun updateNudgeBanner(tokenData: TokenRecord) {
        data class Candidate(val frac: Double, val remainder: Int, val unit: String, val activityLabel: String)

        val stepsProgress = tokenData.steps_to_token ?: 0.0
        val bikeProgress = tokenData.bike_to_token ?: 0.0
        val swimProgress = tokenData.swim_to_token ?: 0.0

        val candidates = listOf(
            Candidate(stepsProgress / DAILY_STEP_GOAL, (DAILY_STEP_GOAL - stepsProgress).toInt(), "steps", "walking"),
            Candidate(bikeProgress / DAILY_CYCLING_GOAL, (DAILY_CYCLING_GOAL - bikeProgress).toInt(), "m", "cycling"),
            Candidate(swimProgress / DAILY_SWIMMING_GOAL, (DAILY_SWIMMING_GOAL - swimProgress).toInt(), "m", "swimming")
        )

        val best = candidates.filter { it.frac in 0.7..0.999 && it.remainder > 0 }.maxByOrNull { it.frac }
        if (best == null) {
            nudgeBanner?.visibility = View.GONE
            return
        }
        nudgeBanner?.text = "You're close! Just ${best.remainder} ${best.unit} more ${best.activityLabel} earns you a token."
        nudgeBanner?.visibility = View.VISIBLE
    }

    /**
     * Updates the UI with token data from the server.
     */
    private fun updateUIWithTokenData(tokenData: TokenRecord) {
        Log.d(TAG, "Updating UI with token data: $tokenData")

        try {
            tEToken?.text = tokenData.reimbursable_tokens.toInt().toString()
            tNEToken?.text = tokenData.nonreimbursable_tokens.toInt().toString()

            // Real token_limit from company_rules (Phase 3) takes precedence over the
            // per-record default, matching iOS's companyRules?.tokenLimit fallback chain.
            val tokenLimit = CompanyRulesCache.current?.tokenLimit ?: tokenData.token_limit ?: 30.0
            updateTokenGauge(tokenData.reimbursable_tokens, tokenLimit)
            updatePaceProjection(tokenData.reimbursable_tokens)
            updateNudgeBanner(tokenData)

            val isDefaultEmptyServerRecord =
                tokenData.corpuid == null &&
                tokenData.reimbursable_tokens == 0.0 &&
                tokenData.nonreimbursable_tokens == 0.0 &&
                (tokenData.steps_to_token ?: 0.0) == 0.0 &&
                (tokenData.swim_to_token ?: 0.0) == 0.0 &&
                (tokenData.bike_to_token ?: 0.0) == 0.0
            if (isDefaultEmptyServerRecord) {
                Log.d(TAG, "Server returned default empty token record; keeping local step UI")
                serverDataLoaded = false
                updateStepCountDisplay()
                return
            }

            // - steps_to_token: steps progress towards next token (0-10000)
            // - swim_to_token: swimming meters progress towards next token (0-2000)
            // - bike_to_token: cycling meters progress towards next token (0-15000)
            val swimProgress = (tokenData.swim_to_token ?: 0.0).toFloat()
            val bikeProgress = (tokenData.bike_to_token ?: 0.0).toFloat()
            val stepsProgress = (tokenData.steps_to_token ?: 0.0).toFloat()

            swimmingProgBar?.progressMax = 2000f
            swimmingProgBar?.setProgressWithAnimation(swimProgress, RING_ANIMATION_DURATION_MS)
            tSwimming?.text = "${swimProgress.toInt()}m/2000m"

            cyclingProgBar?.progressMax = 15000f
            cyclingProgBar?.setProgressWithAnimation(bikeProgress, RING_ANIMATION_DURATION_MS)
            tCycling?.text = "${bikeProgress.toInt()}m/15000m"

            stepsProgBar?.progressMax = 10000f
            stepsProgBar?.setProgressWithAnimation(stepsProgress, RING_ANIMATION_DURATION_MS)
            tSteps?.text = "${stepsProgress.toInt()}/10000"

            Log.d(TAG, "Server data applied - Steps: ${stepsProgress.toInt()}, Swim: ${swimProgress.toInt()}m, Bike: ${bikeProgress.toInt()}m")

            serverDataLoaded = true
            Log.d(TAG, "SERVER DATA FLAG SET: serverDataLoaded = true")

        } catch (e: Exception) {
            Log.e(TAG, "Error updating UI with token data: ${e.message}", e)
            Toast.makeText(requireContext(), "Error updating token display", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Shows monthly step progress information for debugging purposes.
     * Cycling/swimming totals aren't tracked locally anymore — they come from the server
     * via [loadTokenData] / [updateUIWithTokenData].
     */
    private fun showMonthlyProgress() {
        val message = """
            Monthly Progress:
            Device Sensor Steps: ${deviceSensorSteps} steps
            Total Monthly Steps: ${currentStepCount} steps
        """.trimIndent()

        Log.d(TAG, message)
        Toast.makeText(requireContext(), "Monthly steps: ${currentStepCount}", Toast.LENGTH_LONG).show()
    }

    /**
     * Updates the token display with recalculated values including cycling and swimming.
     */
    private fun updateTokenDisplay() {
        Log.d(TAG, "=== updateTokenDisplay called ===")

        val tokenCalc = calculateTokens(currentStepCount)

        updateTokenGauge(tokenCalc.exchangeableTokens.toDouble(), MONTHLY_TOKEN_EXCHANGE_LIMIT.toDouble())

        tEToken?.text = "${tokenCalc.exchangeableTokens}/$MONTHLY_TOKEN_EXCHANGE_LIMIT"
        tNEToken?.text = "${tokenCalc.nonExchangeableTokens}/${60 - MONTHLY_TOKEN_EXCHANGE_LIMIT}"

        Log.d(TAG, "Token display updated - Exchangeable: ${tokenCalc.exchangeableTokens}, Non-exchangeable: ${tokenCalc.nonExchangeableTokens}")
    }

    private fun showPermissionGuidance() {
        Log.d(TAG, "=== showPermissionGuidance called ===")
        val message = "To use step counting features:\n" +
                "1. Go to Settings > Apps > Acteamity > Permissions\n" +
                "2. Enable 'Physical Activity' and 'Body Sensors'\n" +
                "3. Grant Health Connect access to steps, distance, and exercise"
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun showPermissionSettingsDialog() {
        Log.d(TAG, "=== showPermissionSettingsDialog called ===")

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Permissions Required")
            .setMessage("This app needs permissions to count your steps and access fitness data. Please enable them in settings.")
            .setPositiveButton("Go to Settings") { _, _ ->
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = android.net.Uri.fromParts("package", requireContext().packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun hasRequiredPermissions(): Boolean {
        val activityRecognitionGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        val bodySensorsGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
        return activityRecognitionGranted && bodySensorsGranted
    }

    private fun handlePermissionChange() {
        Log.d(TAG, "=== handlePermissionChange called ===")
        val hasPermissions = hasRequiredPermissions()
        Log.d(TAG, "Handling permission change. Has required permissions: $hasPermissions")

        if (hasPermissions) {
            Log.d(TAG, "Permissions granted, enabling features")
            setupHealthConnectAndSensors()
        } else {
            Log.w(TAG, "Permissions revoked, disabling features")
            sensorManager?.unregisterListener(this)
            Toast.makeText(requireContext(), "Permissions revoked. Step counting disabled.", Toast.LENGTH_LONG).show()
            currentStepCount = 0
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "=== onResume called ===")

        val hasPermissions = hasRequiredPermissions()
        Log.d(TAG, "onResume - hasRequiredPermissions: $hasPermissions")

        if (!hasPermissions) {
            Log.w(TAG, "Required permissions not granted in onResume")
            Toast.makeText(requireContext(), "Required permissions not granted. Please enable them in settings.", Toast.LENGTH_LONG).show()
            return
        }

        // Note: this also covers the former CentralActivity.onRestart() logic
        // (Fragments have no onRestart callback; handlePermissionChange() re-runs
        // setupHealthConnectAndSensors() when returning from background, same as before).
        handlePermissionChange()

        val prefs = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val needsRefresh = prefs.getBoolean("token_data_needs_refresh", false)
        if (needsRefresh) {
            Log.d(TAG, "Token data refresh flag detected - refreshing token data after data sync")
            prefs.edit().putBoolean("token_data_needs_refresh", false).apply()
            Handler(Looper.getMainLooper()).postDelayed({
                Log.d(TAG, "Refreshing token data after data sync completion")
                loadTokenData()
            }, 1500)
        } else {
            loadTokenData()
        }

        updatePermissionIndicators()

        stepCountSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        stepDetectorSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "=== onPause called ===")
        sensorManager?.unregisterListener(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "=== onDestroyView called ===")
        sensorManager?.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used in current implementation
    }

    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_STEP_COUNTER -> handleStepCounterEvent(event)
            Sensor.TYPE_STEP_DETECTOR -> handleStepDetectorEvent(event)
            else -> {}
        }
    }

    private fun handleStepCounterEvent(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val totalSteps = event.values[0].toInt()

            if (initialStepCount == -1) {
                initialStepCount = totalSteps
                requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_INITIAL_STEP_COUNT, initialStepCount)
                    .apply()
            }

            currentStepCount = totalSteps - initialStepCount
        }
    }

    private fun handleStepDetectorEvent(event: SensorEvent) {
        if (event.values[0] == 1.0f) {
            currentStepCount++
            updateUIWithStepCount(currentStepCount)
        }
    }

    private fun readStepCount() {
        Log.d(TAG, "readStepCount called - currentStepCount: $currentStepCount")
        updateUIWithStepCount(currentStepCount)
    }

    private fun resetStepCounter() {
        initialStepCount = -1
        currentStepCount = 0

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_INITIAL_STEP_COUNT, initialStepCount)
            .putString(KEY_LAST_STEP_RESET_DATE, today)
            .apply()

        updateUIWithStepCount(0)
        Log.d(TAG, "Step counter reset")
    }

    private fun getStepDataSource(): String {
        return when {
            stepCountSensor != null -> "Device Step Counter"
            stepDetectorSensor != null -> "Device Step Detector"
            else -> "Unavailable"
        }
    }

    /**
     * Calculates token rewards based on steps for the local/pre-server-sync display.
     * Cycling/swimming tokens aren't computed locally — they come from the server via
     * [loadTokenData], which is the source of truth once it loads.
     * - Every 10,000 steps = 1 token
     * - First 30 tokens per month are exchangeable
     * - Remaining tokens (up to 60 total) are non-exchangeable
     */
    private fun calculateTokens(stepCount: Int): TokenCalculation {
        val todaySteps = stepCount % DAILY_STEP_GOAL
        val stepTokens = stepCount / DAILY_STEP_GOAL

        val totalTokens = stepTokens

        val exchangeableTokens = minOf(totalTokens, MONTHLY_TOKEN_EXCHANGE_LIMIT)
        val remainingTokens = maxOf(0, totalTokens - exchangeableTokens)
        val nonExchangeableTokens = minOf(remainingTokens, 60 - MONTHLY_TOKEN_EXCHANGE_LIMIT)

        return TokenCalculation(
            steps = todaySteps,
            exchangeableTokens = exchangeableTokens,
            nonExchangeableTokens = nonExchangeableTokens,
            monthlyExchangeLimit = MONTHLY_TOKEN_EXCHANGE_LIMIT,
            dailyStepGoal = DAILY_STEP_GOAL
        )
    }

    /**
     * Updates the step count display. This method is called when step data changes
     * and updates all UI elements including progress bars and text views.
     */
    private fun updateStepCountDisplay() {
        val dataSource = getStepDataSource()
        Toast.makeText(requireContext(), "Steps: $currentStepCount (via $dataSource)", Toast.LENGTH_SHORT).show()

        val tokenCalculation = calculateTokens(currentStepCount)

        stepsProgBar?.progressMax = DAILY_STEP_GOAL.toFloat()
        stepsProgBar?.setProgressWithAnimation(tokenCalculation.steps.toFloat(), RING_ANIMATION_DURATION_MS)
        updateTokenGauge(tokenCalculation.exchangeableTokens.toDouble(), MONTHLY_TOKEN_EXCHANGE_LIMIT.toDouble())

        tSteps?.text = "${tokenCalculation.steps}/$DAILY_STEP_GOAL"
        tEToken?.text = "${tokenCalculation.exchangeableTokens}/$MONTHLY_TOKEN_EXCHANGE_LIMIT"
        tNEToken?.text = "${tokenCalculation.nonExchangeableTokens}/${60 - MONTHLY_TOKEN_EXCHANGE_LIMIT}"
    }

    /**
     * Updates the UI with a specific step count value, unless server data has
     * already been loaded (server data takes precedence over local calculation).
     */
    private fun updateUIWithStepCount(stepCount: Int) {
        if (serverDataLoaded) {
            Log.d(TAG, "Skipping local UI update because server data is loaded")
            return
        }

        currentStepCount = stepCount
        updateStepCountDisplay()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        Log.d(TAG, "=== onRequestPermissionsResult called ===")

        when (requestCode) {
            REQUEST_ACTIVITY_RECOGNITION_PERMISSION -> {
                var allPermissionsGranted = true
                val permanentlyDenied = mutableListOf<String>()

                for (i in permissions.indices) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        allPermissionsGranted = false

                        if (!ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), permissions[i])) {
                            permanentlyDenied.add(permissions[i])
                        }

                        when (permissions[i]) {
                            Manifest.permission.ACTIVITY_RECOGNITION -> {
                                Toast.makeText(requireContext(), "Activity recognition permission denied. Step counting may not work properly.", Toast.LENGTH_LONG).show()
                            }
                            Manifest.permission.BODY_SENSORS -> {
                                Toast.makeText(requireContext(), "Body sensors permission denied. Some sensor features may not work.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                if (allPermissionsGranted) {
                    Toast.makeText(requireContext(), "All permissions granted", Toast.LENGTH_SHORT).show()
                    markFirstRunComplete()
                    setupHealthConnectAndSensors()
                } else {
                    Toast.makeText(requireContext(), "Some permissions denied. App will work with limited functionality.", Toast.LENGTH_LONG).show()
                    showPermissionSettingsDialog()
                    markFirstRunComplete()
                    setupHealthConnectAndSensors()
                }

                if (permanentlyDenied.isNotEmpty()) {
                    handlePermanentlyDeniedPermissions(permanentlyDenied)
                }

                updatePermissionIndicators()
            }
            else -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(requireContext(), "Permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Updates the visual permission indicators to reflect the current status
     * of all required permissions, including Health Connect availability + grant state.
     */
    private fun updatePermissionIndicators() {
        val currentActivityRecognitionIcon = activityRecognitionIcon
        val currentActivityRecognitionStatus = activityRecognitionStatus
        val currentBodySensorsIcon = bodySensorsIcon
        val currentBodySensorsStatus = bodySensorsStatus
        val currentHealthConnectAvailableIcon = healthConnectAvailableIcon
        val currentHealthConnectAvailableStatus = healthConnectAvailableStatus
        val currentHealthConnectPermissionIcon = healthConnectPermissionIcon
        val currentHealthConnectPermissionStatus = healthConnectPermissionStatus

        if (currentActivityRecognitionIcon == null || currentActivityRecognitionStatus == null ||
            currentBodySensorsIcon == null || currentBodySensorsStatus == null ||
            currentHealthConnectAvailableIcon == null || currentHealthConnectAvailableStatus == null ||
            currentHealthConnectPermissionIcon == null || currentHealthConnectPermissionStatus == null) {
            return
        }

        val activityRecognitionGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        updatePermissionIndicator(currentActivityRecognitionIcon, currentActivityRecognitionStatus, activityRecognitionGranted, "Granted", "Pending")

        val bodySensorsGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
        updatePermissionIndicator(currentBodySensorsIcon, currentBodySensorsStatus, bodySensorsGranted, "Granted", "Pending")

        val healthConnectAvailable = HealthConnectAvailability.check(requireContext()) == HealthConnectAvailability.Status.AVAILABLE
        updatePermissionIndicator(currentHealthConnectAvailableIcon, currentHealthConnectAvailableStatus, healthConnectAvailable, "Available", "Unavailable")

        if (!healthConnectAvailable) {
            updatePermissionIndicator(currentHealthConnectPermissionIcon, currentHealthConnectPermissionStatus, false, "Granted", "Pending")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val client = HealthConnectClient.getOrCreate(requireContext())
                val granted = client.permissionController.getGrantedPermissions()
                if (!isAdded) return@launch
                updatePermissionIndicator(
                    currentHealthConnectPermissionIcon,
                    currentHealthConnectPermissionStatus,
                    granted.containsAll(healthConnectPermissions),
                    "Granted",
                    "Pending"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error checking Health Connect permission status", e)
            }
        }
    }

    private fun updatePermissionIndicator(icon: ImageView?, statusText: TextView?, isGranted: Boolean, grantedText: String, pendingText: String) {
        if (icon == null || statusText == null) return

        try {
            if (isGranted) {
                icon.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.holo_green_light))
                statusText.text = grantedText
                statusText.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_light))
            } else {
                icon.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.holo_red_light))
                statusText.text = pendingText
                statusText.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_light))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating permission indicator: ${e.message}", e)
        }
    }
}
