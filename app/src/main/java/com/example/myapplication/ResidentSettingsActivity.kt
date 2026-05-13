package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.utils.SessionManager
import com.example.myapplication.utils.CustomNotification
import com.example.myapplication.network.RetrofitClient
import com.example.myapplication.models.ApiResponse
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton

class ResidentSettingsActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private var activeDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_resident_settings)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.resident_settings_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sessionManager = SessionManager(this)

        setupProfileData()
        setupClickListeners()
        setupBottomNavigation()
    }

    private fun setupProfileData() {
        val user = sessionManager.getUser()
        findViewById<TextView>(R.id.tv_profile_name).text = user?.name ?: "Juan Dela Cruz"
        findViewById<TextView>(R.id.tv_profile_email).text = user?.email ?: "resident@example.com"
        findViewById<TextView>(R.id.tv_profile_contact).text = user?.phone ?: "09187654321"
        findViewById<TextView>(R.id.tv_profile_purok).text = user?.purok ?: "Purok 2"
    }

    private fun setupClickListeners() {
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            val intent = Intent(this, ResidentDashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        findViewById<MaterialButton>(R.id.btn_logout).setOnClickListener {
            showLogoutConfirmation()
        }

        findViewById<android.view.View>(R.id.row_change_password).setOnClickListener {
            showModal(R.layout.dialog_change_password)
        }

        findViewById<android.view.View>(R.id.row_data_management).setOnClickListener {
            showModal(R.layout.dialog_data_management)
        }

        findViewById<android.view.View>(R.id.row_faqs).setOnClickListener {
            showModal(R.layout.dialog_faqs)
        }

        findViewById<android.view.View>(R.id.row_contact_support).setOnClickListener {
            showModal(R.layout.dialog_contact_support)
        }

        findViewById<android.view.View>(R.id.row_about).setOnClickListener {
            showModal(R.layout.dialog_about)
        }
    }

    private fun showModal(layoutResId: Int) {
        if (isFinishing || isDestroyed) return

        val dialogView = LayoutInflater.from(this).inflate(layoutResId, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        activeDialog = alertDialog

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<android.view.View>(R.id.btn_close)?.setOnClickListener {
            alertDialog.dismiss()
        }

        if (layoutResId == R.layout.dialog_change_password) {
            val etCurrentPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_current_password)
            val etNewPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_new_password)
            val etConfirmPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_confirm_password)
            
            val tvCheckLength = dialogView.findViewById<TextView>(R.id.tv_check_length)
            val tvCheckCase = dialogView.findViewById<TextView>(R.id.tv_check_case)
            val tvCheckNumber = dialogView.findViewById<TextView>(R.id.tv_check_number)
            val tvCheckSymbol = dialogView.findViewById<TextView>(R.id.tv_check_symbol)
            val layoutPasswordChecklist = dialogView.findViewById<android.view.View>(R.id.layout_password_checklist)
            val tvCurrentError = dialogView.findViewById<TextView>(R.id.tv_current_password_error)
            val tvNewPassError = dialogView.findViewById<TextView>(R.id.tv_new_password_error)
            val tvConfirmError = dialogView.findViewById<TextView>(R.id.tv_confirm_password_error)
            val btnSave = dialogView.findViewById<MaterialButton>(R.id.btn_save_password)

            // Initial state: locked
            btnSave?.isEnabled = false
            btnSave?.alpha = 0.5f

            fun validatePassword() {
                val currentPassInput = etCurrentPassword?.text.toString()
                val newPassword = etNewPassword?.text.toString()
                val confirmPass = etConfirmPassword?.text.toString()
                
                val user = sessionManager.getUser()
                val isCurrentMatch = currentPassInput == user?.password
                
                // Show current password error only if not empty
                tvCurrentError?.visibility = if (currentPassInput.isNotEmpty() && !isCurrentMatch) android.view.View.VISIBLE else android.view.View.GONE

                val hasLength = newPassword.length >= 8
                val hasCase = newPassword.any { it.isLowerCase() } && newPassword.any { it.isUpperCase() }
                val hasNumber = newPassword.any { it.isDigit() }
                val hasSymbol = java.util.regex.Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]").matcher(newPassword).find()

                fun updateChecklistItem(textView: TextView, isValid: Boolean) {
                    if (isValid) {
                        textView.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.teal_text))
                        val text = textView.text.toString()
                        if (!text.startsWith("✓")) {
                            textView.text = "✓ " + text.removePrefix("• ")
                        }
                    } else {
                        textView.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_gray))
                        val text = textView.text.toString()
                        if (!text.startsWith("•")) {
                            textView.text = "• " + text.removePrefix("✓ ")
                        }
                    }
                }

                if (newPassword.isNotEmpty()) {
                    updateChecklistItem(tvCheckLength, hasLength)
                    updateChecklistItem(tvCheckCase, hasCase)
                    updateChecklistItem(tvCheckNumber, hasNumber)
                    updateChecklistItem(tvCheckSymbol, hasSymbol)
                    layoutPasswordChecklist.visibility = if (hasLength && hasCase && hasNumber && hasSymbol) android.view.View.GONE else android.view.View.VISIBLE
                } else {
                    layoutPasswordChecklist.visibility = android.view.View.GONE
                }

                // New logic: New password cannot be the same as current
                val isSameAsOld = newPassword.isNotEmpty() && newPassword == currentPassInput
                tvNewPassError?.visibility = if (isSameAsOld) android.view.View.VISIBLE else android.view.View.GONE

                tvConfirmError?.visibility = if (confirmPass.isNotEmpty() && newPassword != confirmPass) android.view.View.VISIBLE else android.view.View.GONE
                
                val isPasswordValid = hasLength && hasCase && hasNumber && hasSymbol && !isSameAsOld
                val isConfirmValid = confirmPass == newPassword && confirmPass.isNotEmpty()
                
                val allValid = isCurrentMatch && isPasswordValid && isConfirmValid
                btnSave?.isEnabled = allValid
                btnSave?.alpha = if (allValid) 1.0f else 0.5f
            }

            etNewPassword?.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    validatePassword()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })

            etConfirmPassword?.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    validatePassword()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })

            etCurrentPassword?.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    validatePassword()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })

            btnSave?.setOnClickListener {
                val currentPass = etCurrentPassword?.text.toString()
                val newPass = etNewPassword?.text.toString()
                val user = sessionManager.getUser() ?: return@setOnClickListener

                showActionConfirmation("Update Password", "Are you sure you want to change your password?") {
                    val userId = user.userId.toString()
                    val role = user.role

                    com.example.myapplication.network.RetrofitClient.instance.changePassword(
                        userId, role, currentPass, newPass
                    ).enqueue(object : retrofit2.Callback<com.example.myapplication.models.ApiResponse> {
                        override fun onResponse(
                            call: retrofit2.Call<com.example.myapplication.models.ApiResponse>,
                            response: retrofit2.Response<com.example.myapplication.models.ApiResponse>
                        ) {
                            if (response.isSuccessful && response.body()?.success == true) {
                                // Update password in session
                                sessionManager.saveUser(user.copy(password = newPass))
                                CustomNotification.showTopNotification(this@ResidentSettingsActivity, "Password updated successfully!", false)
                                alertDialog.dismiss()
                            } else {
                                val msg = response.body()?.message ?: "Failed to update password"
                                CustomNotification.showTopNotification(this@ResidentSettingsActivity, msg)
                            }
                        }

                        override fun onFailure(call: retrofit2.Call<com.example.myapplication.models.ApiResponse>, t: Throwable) {
                            CustomNotification.showTopNotification(this@ResidentSettingsActivity, "Error: ${t.message}")
                        }
                    })
                }
            }
        }

        if (layoutResId == R.layout.dialog_data_management) {
            val user = sessionManager.getUser()
            val etName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_name)
            val etEmail = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_email)
            val etPhone = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_phone)
            val spinnerPurok = dialogView.findViewById<android.widget.AutoCompleteTextView>(R.id.spinner_purok)

            etName?.setText(user?.name)
            etEmail?.setText(user?.email)
            etPhone?.setText(user?.phone)

            val puroks = arrayOf("Purok 1", "Purok 2", "Purok 3", "Purok 4", "Purok 5", "Purok 6", "Purok 7")
            val adapter = android.widget.ArrayAdapter(this, R.layout.dropdown_item, puroks)
            spinnerPurok?.setAdapter(adapter)
            spinnerPurok?.setText(user?.purok ?: puroks[0], false)

            dialogView.findViewById<MaterialButton>(R.id.btn_save_profile)?.setOnClickListener {
                val newName = etName?.text.toString().trim()
                val newEmail = etEmail?.text.toString().trim()
                val newPhone = etPhone?.text.toString().trim()
                val newPurok = spinnerPurok?.text.toString()

                if (newName.isEmpty() || newEmail.isEmpty() || newPhone.isEmpty()) {
                    CustomNotification.showTopNotification(this, "Please fill in all fields")
                    return@setOnClickListener
                }

                RetrofitClient.instance.updateResidentProfile(
                    user?.userId ?: 0,
                    newName,
                    newEmail,
                    newPhone,
                    newPurok
                ).enqueue(object : retrofit2.Callback<ApiResponse> {
                    override fun onResponse(call: retrofit2.Call<ApiResponse>, response: retrofit2.Response<ApiResponse>) {
                        if (response.isSuccessful && response.body()?.success == true) {
                            // Update local session
                            val updatedUser = user?.copy(
                                name = newName,
                                email = newEmail,
                                phone = newPhone,
                                purok = newPurok
                            )
                            if (updatedUser != null) {
                                sessionManager.saveUser(updatedUser)
                                setupProfileData() // Refresh UI in background
                            }
                            CustomNotification.showTopNotification(this@ResidentSettingsActivity, "Profile updated successfully!", false)
                            alertDialog.dismiss()
                        } else {
                            val msg = response.body()?.message ?: "Update failed"
                            CustomNotification.showTopNotification(this@ResidentSettingsActivity, msg)
                        }
                    }

                    override fun onFailure(call: retrofit2.Call<ApiResponse>, t: Throwable) {
                        CustomNotification.showTopNotification(this@ResidentSettingsActivity, "Connection Error")
                    }
                })
            }
        }

        alertDialog.show()
    }

    private fun showActionConfirmation(title: String, message: String, onConfirm: () -> Unit) {
        if (isFinishing || isDestroyed) return

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_generic_confirmation, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        activeDialog = alertDialog

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tv_confirm_title).text = title
        dialogView.findViewById<TextView>(R.id.tv_confirm_msg).text = message

        dialogView.findViewById<android.view.View>(R.id.btn_confirm_no).setOnClickListener {
            alertDialog.dismiss()
        }

        dialogView.findViewById<android.view.View>(R.id.btn_confirm_yes).setOnClickListener {
            onConfirm()
            alertDialog.dismiss()
        }

        alertDialog.show()
    }

    private fun showLogoutConfirmation() {
        if (isFinishing || isDestroyed) return

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_logout_confirmation_resident, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        activeDialog = alertDialog

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            alertDialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btn_confirm_logout).setOnClickListener {
            sessionManager.logout()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        alertDialog.show()
    }

    override fun onDestroy() {
        activeDialog?.dismiss()
        activeDialog = null
        super.onDestroy()
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.selectedItemId = R.id.nav_settings

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, ResidentDashboardActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_track -> {
                    startActivity(Intent(this, TrackTrucksActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_complaints -> {
                    startActivity(Intent(this, ResidentComplaintsActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_settings -> true
                else -> false
            }
        }
    }
}