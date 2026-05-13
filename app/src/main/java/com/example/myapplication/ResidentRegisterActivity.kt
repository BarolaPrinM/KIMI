package com.example.myapplication

import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.example.myapplication.models.ApiResponse
import com.example.myapplication.network.RetrofitClient
import com.example.myapplication.utils.CustomNotification
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.regex.Pattern

class ResidentRegisterActivity : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: com.google.android.material.textfield.TextInputEditText
    private lateinit var etConfirmPassword: com.google.android.material.textfield.TextInputEditText
    private lateinit var etFullName: EditText
    private lateinit var etContactNumber: EditText
    private lateinit var spinnerPurok: Spinner
    private lateinit var etAddress: EditText
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
    private lateinit var tvContactError: TextView
    private lateinit var tvPurokError: TextView
    private lateinit var tvAddressError: TextView

    private var isEmailUnique = true
    private var isPhoneUnique = true
    private val checkHandler = Handler(Looper.getMainLooper())
    private var emailCheckRunnable: Runnable? = null
    private var phoneCheckRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_resident_register)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.resident_register_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        setupPurokSpinner()
        setupValidation()

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_cancel).setOnClickListener { finish() }
        btnSubmit.setOnClickListener { performRegistration() }
    }

    private fun initViews() {
        etUsername = findViewById(R.id.et_username)
        etEmail = findViewById(R.id.et_email)
        etPassword = findViewById(R.id.et_password)
        etConfirmPassword = findViewById(R.id.et_confirm_password)
        etFullName = findViewById(R.id.et_full_name)
        etContactNumber = findViewById(R.id.et_contact_number)
        spinnerPurok = findViewById(R.id.spinner_purok)
        etAddress = findViewById(R.id.et_address)
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
        tvContactError = findViewById(R.id.tv_contact_error)
        tvPurokError = findViewById(R.id.tv_purok_error)
        tvAddressError = findViewById(R.id.tv_address_error)
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

        etAddress.addTextChangedListener(commonWatcher)

        spinnerPurok.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0) tvPurokError.visibility = View.GONE
                validateForm()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Initialize button state
        validateForm()
    }

    private fun validateForm() {
        val username = etUsername.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()
        val fullName = etFullName.text.toString().trim()
        val rawPhone = etContactNumber.text.toString().trim()
        val address = etAddress.text.toString().trim()
        val purokPos = spinnerPurok.selectedItemPosition

        val isUsernameValid = username.length >= 3
        val isEmailValid = email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches() && isEmailUnique
        
        val hasLength = password.length >= 8
        val hasCase = password.any { it.isLowerCase() } && password.any { it.isUpperCase() }
        val hasNumber = password.any { it.isDigit() }
        val hasSymbol = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]").matcher(password).find()
        val isPasswordValid = hasLength && hasCase && hasNumber && hasSymbol
        
        val isConfirmValid = confirmPassword == password && confirmPassword.isNotEmpty()
        val isFullNameValid = fullName.isNotEmpty()
        val isPhoneValid = rawPhone.length == 9 && rawPhone.all { it.isDigit() } && isPhoneUnique
        val isPurokValid = purokPos > 0
        val isAddressValid = address.isNotEmpty()

        val allValid = isUsernameValid && isEmailValid && isPasswordValid && isConfirmValid && 
                       isFullNameValid && isPhoneValid && isPurokValid && isAddressValid

        btnSubmit.isEnabled = allValid
        btnSubmit.alpha = if (allValid) 1.0f else 0.5f
    }

    private fun checkEmailAvailability(email: String) {
        emailCheckRunnable?.let { checkHandler.removeCallbacks(it) }
        emailCheckRunnable = Runnable {
            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) return@Runnable
            
            RetrofitClient.instance.checkEmailAvailability(email).enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                    if (response.isSuccessful) {
                        // success=true means email exists in master database (not available)
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
                        // success=true means phone exists in database (not available)
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

    private fun setupPurokSpinner() {
        val puroks = arrayOf(
<<<<<<< HEAD
            "Choose Purok...",
            "Purok 2",
            "Purok 3",
            "Purok 4",
            "Dos Riles",
            "Sentro",
            "San Isidro",
            "Paraiso",
            "Riverside",
            "Kalaw Street",
            "Home Subdivision",
            "Tanco Road / Ayala Highway",
            "Brixton Area"
=======
            "Choose Purok...", "Purok 2", "Purok 3", "Purok 4", "Dos Riles", 
            "Sentro", "San Isidro", "Paraiso", "Riverside", "Kalaw Street", 
            "Home Subdivision", "Tanco Road / Ayala Highway", "Brixton Area"
>>>>>>> 117a85521b466e4f823d227f35cd645078d64a09
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, puroks)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPurok.adapter = adapter
    }

    private fun performRegistration() {
        // Form is already validated by btnSubmit.isEnabled
        val username = etUsername.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        val fullName = etFullName.text.toString().trim()
        val phone = "09" + etContactNumber.text.toString().trim()
        val address = etAddress.text.toString().trim()
        val purok = spinnerPurok.selectedItem.toString()

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
            "role" to "resident",
            "phone" to phone,
            "purok" to purok,
            "complete_address" to address,
            "status" to "pending",
            "timestamp" to com.google.firebase.database.ServerValue.TIMESTAMP
        )

        database.child(requestId).setValue(requestData)
            .addOnSuccessListener {
                // Also Register to MySQL via Retrofit
                val retrofitRequest = com.example.myapplication.models.RegisterRequest(
                    username = username,
                    name = fullName,
                    email = email,
                    password = password,
                    role = "resident",
                    phone = phone,
                    purok = purok,
                    completeAddress = address
                )

                RetrofitClient.instance.register(retrofitRequest).enqueue(object : Callback<ApiResponse> {
                    override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                        showLoading(false)
                        if (response.isSuccessful && response.body()?.success == true) {
                            CustomNotification.showTopNotification(this@ResidentRegisterActivity, "Registration Sent! Wait for Admin Approval.", false)
                            Handler(Looper.getMainLooper()).postDelayed({
                                val intent = Intent(this@ResidentRegisterActivity, MainActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            }, 2000)
                        } else {
                            val msg = response.body()?.message ?: "Failed to save to database"
                            CustomNotification.showTopNotification(this@ResidentRegisterActivity, "Error: $msg")
                        }
                    }

                    override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                        showLoading(false)
                        CustomNotification.showTopNotification(this@ResidentRegisterActivity, "Network Error: ${t.message}")
                    }
                })
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
