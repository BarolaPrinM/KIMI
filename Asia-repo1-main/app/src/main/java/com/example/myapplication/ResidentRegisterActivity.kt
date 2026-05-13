package com.example.myapplication

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
import com.example.myapplication.models.ApiResponse
import com.example.myapplication.models.RegisterRequest
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
        etUsername.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s.toString()
                if (input.any { it.isDigit() }) {
                    tvUsernameError.visibility = View.VISIBLE
                } else {
                    tvUsernameError.visibility = View.GONE
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val email = s.toString()
                if (email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    tvEmailError.visibility = View.VISIBLE
                } else {
                    tvEmailError.visibility = View.GONE
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validatePassword(s.toString())
                validateConfirmPassword()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etConfirmPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateConfirmPassword()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
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
        val puroks = arrayOf("Choose Purok...", "Purok 1", "Purok 2", "Purok 3", "Purok 4", "Purok 5", "Purok 6", "Purok 7")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, puroks)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPurok.adapter = adapter
    }

    private fun performRegistration() {
        val username = etUsername.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        val fullName = etFullName.text.toString().trim()
        val phone = etContactNumber.text.toString().trim()
        val address = etAddress.text.toString().trim()
        val purok = spinnerPurok.selectedItem.toString()

        // Reset visibility
        tvFullNameError.visibility = View.GONE
        tvContactError.visibility = View.GONE
        tvPurokError.visibility = View.GONE
        tvAddressError.visibility = View.GONE

        var hasError = false

        if (username.isEmpty()) {
            tvUsernameError.text = "Username is required"
            tvUsernameError.visibility = View.VISIBLE
            hasError = true
        }
        if (email.isEmpty()) {
            tvEmailError.text = "Email is required"
            tvEmailError.visibility = View.VISIBLE
            hasError = true
        }
        if (password.isEmpty()) {
            layoutPasswordChecklist.visibility = View.VISIBLE
            hasError = true
        }
        if (fullName.isEmpty()) {
            tvFullNameError.visibility = View.VISIBLE
            hasError = true
        }
        if (phone.isEmpty()) {
            tvContactError.visibility = View.VISIBLE
            hasError = true
        }
        if (spinnerPurok.selectedItemPosition == 0) {
            tvPurokError.visibility = View.VISIBLE
            hasError = true
        }
        if (address.isEmpty()) {
            tvAddressError.visibility = View.VISIBLE
            hasError = true
        }

        if (hasError) {
            CustomNotification.showTopNotification(this, "Please fix the errors")
            return
        }

        // Additional Validations
        if (username.any { it.isDigit() }) {
            tvUsernameError.text = "Username cannot contain numbers"
            tvUsernameError.visibility = View.VISIBLE
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tvEmailError.text = "Invalid email format"
            tvEmailError.visibility = View.VISIBLE
            return
        }

        val hasLength = password.length >= 8
        val hasCase = password.any { it.isLowerCase() } && password.any { it.isUpperCase() }
        val hasNumber = password.any { it.isDigit() }
        val hasSymbol = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]").matcher(password).find()

        if (!hasLength || !hasCase || !hasNumber || !hasSymbol) {
            CustomNotification.showTopNotification(this, "Password does not meet requirements")
            return
        }

        if (password != etConfirmPassword.text.toString()) {
            CustomNotification.showTopNotification(this, "Passwords do not match")
            return
        }

        showLoading(true)

        val request = RegisterRequest(
            username = username,
            name = fullName,
            email = email,
            password = password,
            role = "resident",
            phone = phone,
            purok = purok,
            completeAddress = address
        )

        RetrofitClient.instance.register(request).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                showLoading(false)
                if (response.isSuccessful && response.body()?.success == true) {
                    CustomNotification.showTopNotification(this@ResidentRegisterActivity, "Registration Successful!", false)
                    Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1500)
                } else {
                    val msg = response.body()?.message ?: "Registration Failed"
                    CustomNotification.showTopNotification(this@ResidentRegisterActivity, msg)
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                showLoading(false)
                CustomNotification.showTopNotification(this@ResidentRegisterActivity, "Connection Error")
            }
        })
    }

    private fun showLoading(show: Boolean) {
        pbRegister.visibility = if (show) View.VISIBLE else View.GONE
        tvSubmitText.text = if (show) "Submitting..." else "Submit Registration"
        btnSubmit.isEnabled = !show
    }
}
