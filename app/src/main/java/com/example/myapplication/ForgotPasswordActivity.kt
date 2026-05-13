package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
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
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.regex.Pattern

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var layoutStep1: LinearLayout
    private lateinit var layoutStep2: LinearLayout
    private lateinit var layoutStep3: LinearLayout
    
    private lateinit var pbStep1: ProgressBar
    private lateinit var pbStep2: ProgressBar
    private lateinit var pbStep3: ProgressBar

    private lateinit var stepIcon: ImageView
    private lateinit var stepTitle: TextView
    private lateinit var stepSubtitle: TextView
    
    private lateinit var etEmail: EditText
    private lateinit var tvTimer: TextView
    private lateinit var btnResend: TextView

    private lateinit var etNewPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var tvConfirmPasswordError: TextView
    private lateinit var tvCheckLength: TextView
    private lateinit var tvCheckCase: TextView
    private lateinit var tvCheckNumber: TextView
    private lateinit var tvCheckSymbol: TextView
    private lateinit var layoutPasswordChecklist: View

    private var currentEmail: String = ""
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_forgot_password)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_forgot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize views
        layoutStep1 = findViewById(R.id.layout_step1)
        layoutStep2 = findViewById(R.id.layout_step2)
        layoutStep3 = findViewById(R.id.layout_step3)
        
        pbStep1 = findViewById(R.id.pb_step1)
        pbStep2 = findViewById(R.id.pb_step2)
        pbStep3 = findViewById(R.id.pb_step3)

        stepIcon = findViewById(R.id.step_icon)
        stepTitle = findViewById(R.id.step_title)
        stepSubtitle = findViewById(R.id.step_subtitle)
        
        etEmail = findViewById(R.id.et_email)
        tvTimer = findViewById(R.id.tv_timer)
        btnResend = findViewById(R.id.btn_resend)

        etNewPassword = findViewById(R.id.et_new_password)
        etConfirmPassword = findViewById(R.id.et_confirm_password)
        tvConfirmPasswordError = findViewById(R.id.tv_confirm_password_error)
        tvCheckLength = findViewById(R.id.tv_check_length)
        tvCheckCase = findViewById(R.id.tv_check_case)
        tvCheckNumber = findViewById(R.id.tv_check_number)
        tvCheckSymbol = findViewById(R.id.tv_check_symbol)
        layoutPasswordChecklist = findViewById(R.id.layout_password_checklist)

        setupPasswordValidation()

        findViewById<View>(R.id.btn_back).setOnClickListener {
            handleBackNavigation()
        }

        // Step 1: Send Token
        findViewById<View>(R.id.btn_send_token).setOnClickListener {
            handleStep1()
        }

        // Step 2: Verify Token
        findViewById<View>(R.id.btn_verify_token).setOnClickListener {
            handleStep2()
        }

        btnResend.setOnClickListener {
            handleStep1()
        }

        // Step 3: Reset Password
        findViewById<View>(R.id.btn_submit_reset).setOnClickListener {
            handleStep3()
        }
        
        updateHeader(1)
    }

    private fun updateHeader(step: Int) {
        // Set lock icon for all steps as requested
        stepIcon.setImageResource(android.R.drawable.ic_lock_idle_lock)

        when(step) {
            1 -> {
                stepTitle.text = "Verify Email"
                stepSubtitle.text = "Step 1 of 3"
            }
            2 -> {
                stepTitle.text = "Enter Token"
                stepSubtitle.text = "Step 2 of 3"
            }
            3 -> {
                stepTitle.text = "Reset Password"
                stepSubtitle.text = "Step 3 of 3"
            }
        }
        // Animated transition for header
        stepIcon.scaleX = 0.5f
        stepIcon.scaleY = 0.5f
        stepIcon.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start()
    }

    private fun setupPasswordValidation() {
        etNewPassword.addTextChangedListener(object : TextWatcher {
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
        val pass = etNewPassword.text.toString()
        val confirmPass = etConfirmPassword.text.toString()
        if (confirmPass.isNotEmpty() && pass != confirmPass) {
            tvConfirmPasswordError.visibility = View.VISIBLE
        } else {
            tvConfirmPasswordError.visibility = View.GONE
        }
    }

    private fun handleStep1() {
        val email = etEmail.text.toString().trim()

        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            CustomNotification.showTopNotification(this, "Please enter a valid email address")
            return
        }

        showLoading(1, true)
        RetrofitClient.instance.checkEmail(email).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                showLoading(1, false)
                // check_email returns success=true if email EXISTS
                if (response.isSuccessful && response.body()?.success == true) {
                    currentEmail = email
                    val successMsg = if (layoutStep2.visibility == View.VISIBLE) {
                        "New verification code sent to your email"
                    } else {
                        "Verification code sent to your email"
                    }
                    CustomNotification.showTopNotification(this@ForgotPasswordActivity, successMsg, false)
                    layoutStep1.visibility = View.GONE
                    layoutStep2.visibility = View.VISIBLE
                    updateHeader(2)
                    startResendTimer()
                } else {
                    val message = response.body()?.message ?: "Error checking email"
                    CustomNotification.showTopNotification(this@ForgotPasswordActivity, message)
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                showLoading(1, false)
                CustomNotification.showTopNotification(this@ForgotPasswordActivity, "Network Error")
            }
        })
    }

    private fun handleStep2() {
        val token = findViewById<EditText>(R.id.et_token).text.toString().trim()

        if (token.isEmpty()) {
            CustomNotification.showTopNotification(this, "Please enter the verification token")
            return
        }

        showLoading(2, true)
        RetrofitClient.instance.verifyToken(currentEmail, token).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                showLoading(2, false)
                if (response.isSuccessful && response.body()?.success == true) {
                    countDownTimer?.cancel()
                    CustomNotification.showTopNotification(this@ForgotPasswordActivity, "Verification successful", false)
                    layoutStep2.visibility = View.GONE
                    layoutStep3.visibility = View.VISIBLE
                    updateHeader(3)
                } else {
                    val message = response.body()?.message ?: "Invalid token"
                    CustomNotification.showTopNotification(this@ForgotPasswordActivity, message)
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                showLoading(2, false)
                CustomNotification.showTopNotification(this@ForgotPasswordActivity, "Network Error")
            }
        })
    }

    private fun handleStep3() {
        val newPass = etNewPassword.text.toString()
        val confirmPass = etConfirmPassword.text.toString()

        val hasLength = newPass.length >= 8
        val hasCase = newPass.any { it.isLowerCase() } && newPass.any { it.isUpperCase() }
        val hasNumber = newPass.any { it.isDigit() }
        val hasSymbol = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]").matcher(newPass).find()
        
        if (!(hasLength && hasCase && hasNumber && hasSymbol)) {
            CustomNotification.showTopNotification(this, "Password does not meet requirements")
            layoutPasswordChecklist.visibility = View.VISIBLE
            return
        }

        if (newPass != confirmPass) {
            CustomNotification.showTopNotification(this, "Passwords do not match")
            tvConfirmPasswordError.visibility = View.VISIBLE
            return
        }

        showLoading(3, true)
        RetrofitClient.instance.resetPassword(currentEmail, newPass).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                showLoading(3, false)
                if (response.isSuccessful && response.body()?.success == true) {
                    CustomNotification.showTopNotification(this@ForgotPasswordActivity, "Password successfully updated", false)
                    Handler(Looper.getMainLooper()).postDelayed({
                        finish()
                    }, 1500)
                } else {
                    val message = response.body()?.message ?: "Update failed"
                    CustomNotification.showTopNotification(this@ForgotPasswordActivity, message)
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                showLoading(3, false)
                CustomNotification.showTopNotification(this@ForgotPasswordActivity, "Update Error")
            }
        })
    }

    private fun startResendTimer() {
        countDownTimer?.cancel()
        btnResend.visibility = View.GONE
        tvTimer.visibility = View.VISIBLE
        
        countDownTimer = object : CountDownTimer(180000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                tvTimer.text = String.format("Code expires in: %02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                tvTimer.text = "Token expired"
                btnResend.visibility = View.VISIBLE
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }

    override fun onBackPressed() {
        handleBackNavigation()
    }

    private fun handleBackNavigation() {
        when {
            layoutStep3.visibility == View.VISIBLE -> {
                layoutStep3.visibility = View.GONE
                layoutStep2.visibility = View.VISIBLE
                updateHeader(2)
            }
            layoutStep2.visibility == View.VISIBLE -> {
                countDownTimer?.cancel()
                layoutStep2.visibility = View.GONE
                layoutStep1.visibility = View.VISIBLE
                updateHeader(1)
            }
            else -> {
                finish()
            }
        }
    }

    private fun showLoading(step: Int, show: Boolean) {
        when(step) {
            1 -> {
                pbStep1.visibility = if (show) View.VISIBLE else View.GONE
                findViewById<View>(R.id.btn_send_token).apply {
                    isEnabled = !show
                    alpha = if (show) 0.7f else 1.0f
                }
            }
            2 -> {
                pbStep2.visibility = if (show) View.VISIBLE else View.GONE
                findViewById<View>(R.id.btn_verify_token).apply {
                    isEnabled = !show
                    alpha = if (show) 0.7f else 1.0f
                }
            }
            3 -> {
                pbStep3.visibility = if (show) View.VISIBLE else View.GONE
                findViewById<View>(R.id.btn_submit_reset).apply {
                    isEnabled = !show
                    alpha = if (show) 0.7f else 1.0f
                }
            }
        }
    }
}
