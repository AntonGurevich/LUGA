package silverbackgarden.example.luga.ui.profile

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.gotrue.user.UserInfo
import kotlinx.coroutines.launch
import silverbackgarden.example.luga.health.HealthConnectAvailability
import silverbackgarden.example.luga.Acteamity
import silverbackgarden.example.luga.AuthManager
import silverbackgarden.example.luga.BikeData
import silverbackgarden.example.luga.LoginActivity
import silverbackgarden.example.luga.PrivacyDisclosureActivity
import silverbackgarden.example.luga.ProfileEditActivity
import silverbackgarden.example.luga.R
import silverbackgarden.example.luga.StepData
import silverbackgarden.example.luga.SupabaseUserManager
import silverbackgarden.example.luga.SwimData
import silverbackgarden.example.luga.UserCorpLink
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

/**
 * Profile tab: identity, account info, appearance, activity permission status,
 * security, and data & privacy — ported from iOS ProfileView on top of the
 * existing GDPR/change-employer/edit-name flows from the earlier port.
 */
class ProfileFragment : Fragment() {

    private lateinit var emailTextView: TextView
    private lateinit var employerTextView: TextView
    private lateinit var nameTextView: TextView
    private lateinit var surnameTextView: TextView

    private lateinit var authManager: AuthManager
    private val supabaseUserManager = SupabaseUserManager()

    private val sharedPref by lazy {
        (requireContext().applicationContext as? Acteamity)?.sharedPref
    }
    private val appearancePrefs by lazy {
        requireContext().getSharedPreferences(Acteamity.PREFS_APPEARANCE, Context.MODE_PRIVATE)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        authManager = AuthManager(requireContext())
        emailTextView = view.findViewById(R.id.email_textview)
        employerTextView = view.findViewById(R.id.employer_textview)
        nameTextView = view.findViewById(R.id.name_textview)
        surnameTextView = view.findViewById(R.id.surname_textview)

        val email = authManager.getCurrentUserEmail()
        emailTextView.text = "Email: ${email ?: "—"}"

        val name = sharedPref?.getString("name", null)
        val surname = sharedPref?.getString("surname", null)
        updateNameSurnameDisplay(name, surname)
        updateIdentityCard(email, name, surname)

        employerTextView.text = "Employer: …"
        val userId = authManager.getCurrentUserId()
        if (!userId.isNullOrEmpty()) {
            loadEmployerName(userId)
            loadMemberSince(userId)
            loadLinkedSince(userId)
            loadStreak(userId)
        } else {
            employerTextView.text = "Employer: —"
        }

        view.findViewById<Button>(R.id.change_password_button).setOnClickListener {
            startActivity(Intent(requireContext(), ProfileEditActivity::class.java))
        }
        view.findViewById<Button>(R.id.change_employer_button).setOnClickListener { showChangeEmployerDialog() }
        view.findViewById<TextView>(R.id.edit_name_button).setOnClickListener { showEditNameDialog() }
        view.findViewById<TextView>(R.id.edit_surname_button).setOnClickListener { showEditSurnameDialog() }
        view.findViewById<TextView>(R.id.delete_account_link).setOnClickListener { showDeleteAccountDialogs() }
        view.findViewById<Button>(R.id.privacy_disclosure_button).setOnClickListener {
            startActivity(Intent(requireContext(), PrivacyDisclosureActivity::class.java))
        }
        view.findViewById<Button>(R.id.sign_out_button).setOnClickListener { signOut() }
        view.findViewById<TextView>(R.id.profile_privacy_link).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://acteamity.com/privacy")))
        }
        view.findViewById<TextView>(R.id.profile_terms_link).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://acteamity.com/terms")))
        }

        setupAppearanceSwitches(view)
        renderActivityPermissions(view)
    }

    private fun loadEmployerName(userId: String) {
        supabaseUserManager.fetchEmployerNameForUser(userId, object : SupabaseUserManager.DatabaseCallback<String?> {
            override fun onSuccess(result: String?) {
                if (!isAdded) return
                employerTextView.text = "Employer: ${result ?: "—"}"
                if (!result.isNullOrEmpty()) {
                    view?.findViewById<TextView>(R.id.tvCompanyBadge)?.apply {
                        text = result
                        visibility = View.VISIBLE
                    }
                }
            }
            override fun onError(error: String) {
                if (!isAdded) return
                employerTextView.text = "Employer: —"
            }
        })
    }

    /** Initials avatar + display name shown at the top of the Identity card. */
    private fun updateIdentityCard(email: String?, name: String?, surname: String?) {
        val displayName = listOfNotNull(name?.takeIf { it.isNotBlank() }, surname?.takeIf { it.isNotBlank() })
            .joinToString(" ")
            .ifBlank { email ?: "there" }
        view?.findViewById<TextView>(R.id.tvDisplayName)?.text = displayName

        val initials = when {
            !name.isNullOrBlank() && !surname.isNullOrBlank() -> "${name.first()}${surname.first()}"
            !name.isNullOrBlank() -> name.take(2)
            !email.isNullOrBlank() -> email.take(2)
            else -> "?"
        }.uppercase(Locale.getDefault())
        view?.findViewById<TextView>(R.id.tvAvatarInitials)?.text = initials
    }

    private fun loadMemberSince(userId: String) {
        supabaseUserManager.fetchUserDataByUid(userId, object : SupabaseUserManager.DatabaseCallback<silverbackgarden.example.luga.UserData?> {
            override fun onSuccess(result: silverbackgarden.example.luga.UserData?) {
                if (!isAdded) return
                val registrationDate = result?.registration_date
                val formatted = registrationDate?.let { formatMonthYear(it) }
                view?.findViewById<TextView>(R.id.tvMemberSince)?.text = "Member since: ${formatted ?: "—"}"
            }
            override fun onError(error: String) { }
        })
    }

    private fun loadLinkedSince(userId: String) {
        supabaseUserManager.fetchUserCorpLink(userId, object : SupabaseUserManager.DatabaseCallback<UserCorpLink?> {
            override fun onSuccess(result: UserCorpLink?) {
                if (!isAdded) return
                val effectiveFrom = result?.effective_from ?: return
                val formatted = formatMonthYear(effectiveFrom) ?: return
                view?.findViewById<TextView>(R.id.tvLinkedSince)?.apply {
                    text = "Linked since: $formatted"
                    visibility = View.VISIBLE
                }
            }
            override fun onError(error: String) { }
        })
    }

    private fun formatMonthYear(isoDate: String): String? {
        return runCatching {
            val date = LocalDate.parse(isoDate.take(10))
            date.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))
        }.getOrNull()
    }

    /** Same consecutive-active-days calculation as DashboardFragment, shown as a badge here. */
    private fun loadStreak(userId: String) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val endDate = dateFormat.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -30)
        val startDate = dateFormat.format(cal.time)

        supabaseUserManager.getStepDataRange(userId, startDate, endDate, object : SupabaseUserManager.DatabaseCallback<List<StepData>> {
            override fun onSuccess(stepRows: List<StepData>) {
                if (!isAdded) return
                supabaseUserManager.getBikeDataRange(userId, startDate, endDate, object : SupabaseUserManager.DatabaseCallback<List<BikeData>> {
                    override fun onSuccess(bikeRows: List<BikeData>) {
                        if (!isAdded) return
                        supabaseUserManager.getSwimDataRange(userId, startDate, endDate, object : SupabaseUserManager.DatabaseCallback<List<SwimData>> {
                            override fun onSuccess(swimRows: List<SwimData>) {
                                if (!isAdded) return
                                val activeDates = HashSet<String>()
                                stepRows.filter { it.steps > 0 }.forEach { activeDates.add(it.date) }
                                bikeRows.filter { it.m_per_day > 0 }.forEach { activeDates.add(it.date) }
                                swimRows.filter { it.m_per_day > 0 }.forEach { activeDates.add(it.date) }

                                var streak = 0
                                val dayCal = Calendar.getInstance()
                                while (true) {
                                    val dayString = dateFormat.format(dayCal.time)
                                    if (activeDates.contains(dayString)) {
                                        streak++
                                        dayCal.add(Calendar.DAY_OF_YEAR, -1)
                                    } else break
                                }
                                if (streak > 0) {
                                    view?.findViewById<TextView>(R.id.tvStreakBadge)?.apply {
                                        text = if (streak == 1) "1 day streak" else "$streak day streak"
                                        visibility = View.VISIBLE
                                    }
                                }
                            }
                            override fun onError(error: String) { }
                        })
                    }
                    override fun onError(error: String) { }
                })
            }
            override fun onError(error: String) { }
        })
    }

    private fun setupAppearanceSwitches(view: View) {
        val darkModeSwitch = view.findViewById<SwitchCompat>(R.id.switchDarkMode)
        val highContrastSwitch = view.findViewById<SwitchCompat>(R.id.switchHighContrast)

        darkModeSwitch.isChecked = appearancePrefs.getBoolean(Acteamity.KEY_PREFERS_DARK_MODE, false)
        highContrastSwitch.isChecked = appearancePrefs.getBoolean(Acteamity.KEY_PREFERS_HIGH_CONTRAST, false)

        darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            appearancePrefs.edit().putBoolean(Acteamity.KEY_PREFERS_DARK_MODE, isChecked).apply()
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                if (isChecked) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            )
        }
        highContrastSwitch.setOnCheckedChangeListener { _, isChecked ->
            appearancePrefs.edit().putBoolean(Acteamity.KEY_PREFERS_HIGH_CONTRAST, isChecked).apply()
            Toast.makeText(requireContext(), "High contrast will apply next time you open the app.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Status-only display (not a request flow): Health Connect's scope model is coarser than
     * HealthKit's per-type permissions, so this mirrors what's actually granted rather than
     * offering per-activity toggles.
     */
    private fun renderActivityPermissions(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.activityPermissionsContainer)
        container.removeAllViews()

        val activityRecognitionGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        val bodySensorsGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED

        addPermissionRow(container, "Steps (device sensors)", activityRecognitionGranted && bodySensorsGranted)
        addPermissionRow(container, "Cycling & Swimming (Health Connect)", false)

        if (HealthConnectAvailability.check(requireContext()) != HealthConnectAvailability.Status.AVAILABLE) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val client = HealthConnectClient.getOrCreate(requireContext())
                val granted = client.permissionController.getGrantedPermissions()
                val healthConnectGranted = granted.containsAll(
                    setOf(
                        HealthPermission.getReadPermission(DistanceRecord::class),
                        HealthPermission.getReadPermission(ExerciseSessionRecord::class)
                    )
                )
                if (!isAdded || !healthConnectGranted) return@launch
                container.removeAllViews()
                addPermissionRow(container, "Steps (device sensors)", activityRecognitionGranted && bodySensorsGranted)
                addPermissionRow(container, "Cycling & Swimming (Health Connect)", healthConnectGranted)
            } catch (e: Exception) {
                // Leave the default "Off" row in place.
            }
        }
    }

    private fun addPermissionRow(container: LinearLayout, label: String, isActive: Boolean) {
        val context = requireContext()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (10 * resources.displayMetrics.density).toInt()
            }
        }
        row.addView(TextView(context).apply {
            text = label
            setTextColor(ContextCompat.getColor(context, R.color.act_ink_900))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(context).apply {
            text = if (isActive) "Active" else "Off"
            setTextColor(ContextCompat.getColor(context, if (isActive) R.color.act_success else R.color.act_ink_400))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        container.addView(row)
    }

    private fun signOut() {
        AlertDialog.Builder(requireContext())
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to sign out?")
            .setPositiveButton("Sign Out") { _, _ ->
                authManager.signOut(object : AuthManager.AuthCallback {
                    override fun onSuccess(user: UserInfo?) = navigateToLogin()
                    override fun onError(error: String) = navigateToLogin()
                })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun navigateToLogin() {
        if (!isAdded) return
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    private fun updateNameSurnameDisplay(name: String?, surname: String?) {
        nameTextView.text = if (name.isNullOrEmpty()) "Name: —" else "Name: $name"
        surnameTextView.text = if (surname.isNullOrEmpty()) "Surname: —" else "Surname: $surname"
        updateIdentityCard(authManager.getCurrentUserEmail(), name, surname)
    }

    private fun showDeleteAccountDialogs() {
        AlertDialog.Builder(requireContext())
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
        val input = EditText(requireContext()).apply {
            hint = "DELETE"
            setSingleLine(true)
            setPadding(
                (48 * resources.displayMetrics.density).toInt(),
                (32 * resources.displayMetrics.density).toInt(),
                (48 * resources.displayMetrics.density).toInt(),
                (32 * resources.displayMetrics.density).toInt()
            )
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_type_confirm_title))
            .setMessage(getString(R.string.delete_type_confirm_message))
            .setView(input)
            .setPositiveButton(getString(R.string.delete_confirm_button)) { _, _ ->
                val typed = input.text.toString().trim()
                if (typed.equals("DELETE", ignoreCase = true)) {
                    performGdprDeletion()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.delete_must_type_delete), Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performGdprDeletion() {
        authManager.requestGdprAccountDeletion(object : AuthManager.AuthCallback {
            override fun onSuccess(user: UserInfo?) {
                clearLocalUserData()
                val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                if (!isAdded) {
                    requireContext().applicationContext.startActivity(intent)
                } else {
                    startActivity(intent)
                    requireActivity().finish()
                }
            }

            override fun onError(error: String) {
                if (!isAdded) return
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
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
        AlertDialog.Builder(requireContext())
            .setTitle("Change Employer")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showNewEmployerCodeDialog()
                    1 -> {
                        requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE).edit()
                            .putString("employer_name", null).apply()
                    }
                    2 -> { }
                }
            }
            .show()
    }

    private fun showNewEmployerCodeDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Employer code"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(
                (48 * resources.displayMetrics.density).toInt(),
                (32 * resources.displayMetrics.density).toInt(),
                (48 * resources.displayMetrics.density).toInt(),
                (32 * resources.displayMetrics.density).toInt()
            )
        }
        AlertDialog.Builder(requireContext())
            .setTitle("New Employer Code")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val connectionCode = input.text.toString().trim().toLongOrNull()
                if (connectionCode == null) {
                    Toast.makeText(requireContext(), "Please enter a valid employer code.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                applyNewEmployerCode(connectionCode)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyNewEmployerCode(connectionCode: Long) {
        val userId = authManager.getCurrentUserId()
        if (userId.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Not signed in.", Toast.LENGTH_SHORT).show()
            return
        }

        supabaseUserManager.validateEmployerCode(connectionCode, object : SupabaseUserManager.DatabaseCallback<silverbackgarden.example.luga.EmployerCodeValidationResult> {
            override fun onSuccess(result: silverbackgarden.example.luga.EmployerCodeValidationResult) {
                if (!isAdded) return
                if (!result.isValid) {
                    Toast.makeText(requireContext(), result.errorMessage ?: "Invalid employer code", Toast.LENGTH_LONG).show()
                    return
                }

                supabaseUserManager.updateUserConnectionCode(userId, connectionCode, object : SupabaseUserManager.DatabaseCallback<Unit> {
                    override fun onSuccess(result: Unit) {
                        if (!isAdded) return
                        supabaseUserManager.linkUserToCompany(userId, connectionCode, object : SupabaseUserManager.DatabaseCallback<UserCorpLink> {
                            override fun onSuccess(result: UserCorpLink) {
                                if (!isAdded) return
                                Toast.makeText(requireContext(), "Employer updated.", Toast.LENGTH_SHORT).show()
                                loadEmployerName(userId)
                                loadLinkedSince(userId)
                            }
                            override fun onError(error: String) {
                                if (!isAdded) return
                                Toast.makeText(requireContext(), "Employer code saved, but linking failed: $error", Toast.LENGTH_LONG).show()
                            }
                        })
                    }
                    override fun onError(error: String) {
                        if (!isAdded) return
                        Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                    }
                })
            }
            override fun onError(error: String) {
                if (!isAdded) return
                Toast.makeText(requireContext(), "Error validating employer code: $error", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun showEditNameDialog() {
        val prefs = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val current = prefs.getString("name", "") ?: ""
        val input = EditText(requireContext()).apply {
            setText(current)
            hint = "Name"
            setSingleLine(true)
            setPadding((48 * resources.displayMetrics.density).toInt(), (32 * resources.displayMetrics.density).toInt(), (48 * resources.displayMetrics.density).toInt(), (32 * resources.displayMetrics.density).toInt())
        }
        AlertDialog.Builder(requireContext())
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
        val prefs = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val current = prefs.getString("surname", "") ?: ""
        val input = EditText(requireContext()).apply {
            setText(current)
            hint = "Surname"
            setSingleLine(true)
            setPadding((48 * resources.displayMetrics.density).toInt(), (32 * resources.displayMetrics.density).toInt(), (48 * resources.displayMetrics.density).toInt(), (32 * resources.displayMetrics.density).toInt())
        }
        AlertDialog.Builder(requireContext())
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
