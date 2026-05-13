package com.example.myapplication

import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.models.ApiResponse
import com.example.myapplication.network.RetrofitClient
import com.example.myapplication.utils.CustomNotification
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.regex.Pattern

class DriverRegisterActivity : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: com.google.android.material.textfield.TextInputEditText
    private lateinit var etConfirmPassword: com.google.android.material.textfield.TextInputEditText
    private lateinit var etFullName: EditText
    private lateinit var etLicenseNumber: EditText
    private lateinit var etContactNumber: EditText
    private lateinit var etTruckAssignment: EditText
    private lateinit var btnSubmit: View
    private lateinit var pbRegister: ProgressBar
    private lateinit var tvSubmitText: TextView

    private lateinit var tvUsernameError: TextView
    private lateinit var tvEmailError: TextView
    private lateinit var tvConfirmPasswordError: TextView
    private lateinit var tvCheckLength: TextView
    private lateinit var tvCheckCase: TextView
    private lateinit var tvCheckNumber: TextView
    private lateinit var tvCheckSymbol: TextView
    private lateinit var layoutPasswordChecklist: View

    private lateinit var tvFullNameError: TextView
    private lateinit var tvLicenseError: TextView
    private lateinit var tvContactError: TextView
    private lateinit var tvTruckError: TextView

    private var isEmailUnique = true
    private var isPhoneUnique = true
    private val checkHandler = Handler(Looper.getMainLooper())
    private var emailCheckRunnable: Runnable? = null
    private var phoneCheckRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_driver_register)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.driver_register_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        setupValidation()

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_cancel).setOnClickListener { finish() }
        btnSubmit.setOnClickListener { submitRequest() }
    }

    private fun initViews() {
        etUsername = findViewById(R.id.et_username)
        etEmail = findViewById(R.id.et_email)
        etPassword = findViewById(R.id.et_password)
        etConfirmPassword = findViewById(R.id.et_confirm_password)
        etFullName = findViewById(R.id.et_full_name)
        etLicenseNumber = findViewById(R.id.et_license_number)
        etContactNumber = findViewById(R.id.et_contact_number)
        etTruckAssignment = findViewById(R.id.et_truck_assignment)
        btnSubmit = findViewById(R.id.btn_submit)
        pbRegister = findViewById(R.id.pb_register)
        tvSubmitText = findViewById(R.id.tv_submit_text)

        tvUsernameError = findViewById(R.id.tv_username_error)
        tvEmailError = findViewById(R.id.tv_email_error)
        tvConfirmPasswordError = findViewById(R.id.tv_confirm_password_error)
        tvCheckLength = findViewById(R.id.tv_check_length)
        tvCheckCase = findViewById(R.id.tv_check_case)
        tvCheckNumber = findViewById(R.id.tv_check_number)
        tvCheckSymbol = findViewById(R.id.tv_check_symbol)
        layoutPasswordChecklist = findViewById(R.id.layout_password_checklist)

        tvFullNameError = findViewById(R.id.tv_full_name_error)
        tvLicenseError = findViewById(R.id.tv_license_error)
        tvContactError = findViewById(R.id.tv_contact_error)
        tvTruckError = findViewById(R.id.tv_truck_error)
    }

    private fun setupValidation() {
        val commonWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateForm()
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        etUsername.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Pinayagan ang numbers sa username
                validateForm()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val email = s.toString()
                if (email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    tvEmailError.text = "Invalid email format"
                    tvEmailError.visibility = View.VISIBLE
                } else {
                    tvEmailError.visibility = View.GONE
                    if (email.isNotEmpty()) checkEmailAvailability(email)
                }
                validateForm()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validatePassword(s.toString())
                validateConfirmPassword()
                validateForm()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etConfirmPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateConfirmPassword()
                validateForm()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etFullName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s.toString()
                if (input.any { it.isDigit() }) {
                    tvFullNameError.visibility = View.VISIBLE
                } else {
                    tvFullNameError.visibility = View.GONE
                }
                validateForm()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etLicenseNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s.toString()
                if (input.isEmpty()) {
                    tvLicenseError.visibility = View.VISIBLE
                } else {
                    tvLicenseError.visibility = View.GONE
                }
                validateForm()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etContactNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString()
                if (input.isNotEmpty() && (input.length != 9 || input.any { !it.isDigit() })) {
                    tvContactError.text = "Contact number must be 9 digits"
                    tvContactError.visibility = View.VISIBLE
                } else {
                    tvContactError.visibility = View.GONE
                    if (input.length == 9) checkPhoneAvailability(input)
                }
                validateForm()
            }
        })

        etTruckAssignment.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.toString().trim().isEmpty()) {
                    tvTruckError.visibility = View.VISIBLE
                } else {
                    tvTruckError.visibility = View.GONE
                }
                validateForm()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Initialize button state
        validateForm()
    }

    private fun validateForm() {
        val username = etUsername.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()
        val fullName = etFullName.text.toString().trim()
        val license = etLicenseNumber.text.toString().trim()
        val rawPhone = etContactNumber.text.toString().trim()
        val truck = etTruckAssignment.text.toString().trim()

        val isUsernameValid = username.length >= 3
        val isEmailValid = email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches() && isEmailUnique
        
        val hasLength = password.length >= 8
        val hasCase = password.any { it.isLowerCase() } && password.any { it.isUpperCase() }
        val hasNumber = password.any { it.isDigit() }
        val hasSymbol = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]").matcher(password).find()
        val isPasswordValid = hasLength && hasCase && hasNumber && hasSymbol
        
        val isConfirmValid = confirmPassword == password && confirmPassword.isNotEmpty()
        val isFullNameValid = fullName.isNotEmpty()
        val isLicenseValid = license.isNotEmpty()
        val isPhoneValid = rawPhone.length == 9 && rawPhone.all { it.isDigit() } && isPhoneUnique
        val isTruckValid = truck.isNotEmpty()

        val allValid = isUsernameValid && isEmailValid && isPasswordValid && isConfirmValid && 
                       isFullNameValid && isLicenseValid && isPhoneValid && isTruckValid

        btnSubmit.isEnabled = allValid
        btnSubmit.alpha = if (allValid) 1.0f else 0.5f
    }

    private fun validatePassword(password: String) {
        val hasLength = password.length >= 8
        val hasCase = password.any { it.isLowerCase() } && password.any { it.isUpperCase() }
        val hasNumber = password.any { it.isDigit() }
        val hasSymbol = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]").matcher(password).find()

        updateChecklistItem(tvCheckLength, hasLength)
        updateChecklistItem(tvCheckCase, hasCase)
        updateChecklistItem(tvCheckNumber, hasNumber)
        updateChecklistItem(tvCheckSymbol, hasSymbol)

        if (hasLength && hasCase && hasNumber && hasSymbol) {
            layoutPasswordChecklist.visibility = View.GONE
        } else {
            layoutPasswordChecklist.visibility = View.VISIBLE
        }
    }

    private fun updateChecklistItem(textView: TextView, isValid: Boolean) {
        if (isValid) {
            textView.setTextColor(ContextCompat.getColor(this, R.color.teal_text))
            val text = textView.text.toString()
            if (!text.startsWith("✓")) {
                textView.text = "✓ " + text.removePrefix("• ")
            }
        } else {
            textView.setTextColor(ContextCompat.getColor(this, R.color.text_gray))
            val text = textView.text.toString()
            if (!text.startsWith("•")) {
                textView.text = "• " + text.removePrefix("✓ ")
            }
        }
    }

    private fun validateConfirmPassword() {
        val pass = etPassword.text.toString()
        val confirmPass = etConfirmPassword.text.toString()
        if (confirmPass.isNotEmpty() && pass != confirmPass) {
            tvConfirmPasswordError.visibility = View.VISIBLE
        } else {
            tvConfirmPasswordError.visibility = View.GONE
        }
    }

    private fun checkEmailAvailability(email: String) {
        emailCheckRunnable?.let { checkHandler.removeCallbacks(it) }
        emailCheckRunnable = Runnable {
            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) return@Runnable
            
            RetrofitClient.instance.checkEmailAvailability(email).enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                    if (response.isSuccessful) {
                        if (response.body()?.success == true) {
                            isEmailUnique = false
                            tvEmailError.text = "Email is already registered"
                            tvEmailError.visibility = View.VISIBLE
                        } else {
                            isEmailUnique = true
                            tvEmailError.visibility = View.GONE
                        }
                        validateForm()
                    }
                }
                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {}
            })
        }
        checkHandler.postDelayed(emailCheckRunnable!!, 500)
    }

    private fun checkPhoneAvailability(phone: String) {
        phoneCheckRunnable?.let { checkHandler.removeCallbacks(it) }
        phoneCheckRunnable = Runnable {
            if (phone.length != 9) return@Runnable
            
            val fullPhone = "09$phone"
            RetrofitClient.instance.checkPhone(fullPhone).enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                    if (response.isSuccessful) {
                        if (response.body()?.success == true) {
                            isPhoneUnique = false
                            tvContactError.text = "Contact number is already registered"
                            tvContactError.visibility = View.VISIBLE
                        } else {
                            isPhoneUnique = true
                            tvContactError.visibility = View.GONE
                        }
                        validateForm()
                    }
                }
                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {}
            })
        }
        checkHandler.postDelayed(phoneCheckRunnable!!, 500)
    }

    private fun submitRequest() {
        // Form is already validated by btnSubmit.isEnabled
        val username = etUsername.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        val fullName = etFullName.text.toString().trim()
        val contactNumber = "09" + etContactNumber.text.toString().trim()
        val licenseNumber = etLicenseNumber.text.toString().trim()
        val truckAssignment = etTruckAssignment.text.toString().trim()

        showLoading(show = true)

        val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
        val database = FirebaseDatabase.getInstance(dbUrl).getReference("registration_requests")
        val requestId = database.push().key ?: System.currentTimeMillis().toString()

        val requestData = mapOf(
            "userId" to 0,
            "username" to username,
            "name" to fullName,
            "email" to email,
            "password" to password,
            "role" to "driver",
            "phone" to contactNumber,
            "license_number" to licenseNumber,
            "preferred_truck" to truckAssignment,
            "status" to "pending",
            "timestamp" to ServerValue.TIMESTAMP
        )

        database.child(requestId).setValue(requestData)
            .addOnSuccessListener {
<<<<<<< HEAD
                // Also Register to MySQL via Retrofit
                val retrofitRequest = com.example.myapplication.models.RegisterRequest(
                    username = username,
                    name = fullName,
                    email = email,
                    password = password,
                    role = "driver",
                    phone = contactNumber,
                    licenseNumber = licenseNumber,
                    preferredTruck = truckAssignment
                )

                RetrofitClient.instance.register(retrofitRequest).enqueue(object : Callback<ApiResponse> {
                    override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                        showLoading(false)
                        if (response.isSuccessful && response.body()?.success == true) {
                            CustomNotification.showTopNotification(this@DriverRegisterActivity, "Driver Application Sent! Wait for Admin Approval.", false)
                            Handler(Looper.getMainLooper()).postDelayed({
                                val intent = Intent(this@DriverRegisterActivity, MainActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            }, 2000)
                        } else {
                            val msg = response.body()?.message ?: "Failed to save to database"
                            CustomNotification.showTopNotification(this@DriverRegisterActivity, "Error: $msg")
                        }
                    }

                    override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                        showLoading(false)
                        CustomNotification.showTopNotification(this@DriverRegisterActivity, "Network Error: ${t.message}")
                    }
                })
=======
                // Send system notification to Admin
                val notification = com.example.myapplication.models.SystemNotification(
                    type = "REGISTRATION",
                    title = "New Driver Registration",
                    message = "$fullName is requesting registration as a driver.",
                    timestamp = System.currentTimeMillis(),
                    isRead = false,
                    relatedId = requestId
                )
                val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
                com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
                    .getReference("notifications").push().setValue(notification)

                showLoading(false)
                CustomNotification.showTopNotification(this, "Driver Application Sent! Wait for Admin Approval.", false)
                Handler(Looper.getMainLooper()).postDelayed({ finish() }, 2000)
>>>>>>> 117a85521b466e4f823d227f35cd645078d64a09
            }
            .addOnFailureListener {
                showLoading(false)
                CustomNotification.showTopNotification(this, "Failed to send request: ${it.message}")
            }
    }

    private fun showLoading(show: Boolean) {
        pbRegister.visibility = if (show) View.VISIBLE else View.GONE
        tvSubmitText.text = if (show) "Submitting..." else "Submit Registration"
        btnSubmit.isEnabled = !show
        btnSubmit.alpha = if (show) 0.7f else 1.0f
    }
}
