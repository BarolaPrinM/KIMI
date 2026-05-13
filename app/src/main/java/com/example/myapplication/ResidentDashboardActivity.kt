package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.fragments.MapboxFragment
import com.example.myapplication.network.LocationUpdateService
import com.example.myapplication.utils.SessionManager
import com.example.myapplication.utils.GpsStatusMonitor
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.os.Build
import com.google.firebase.database.FirebaseDatabase
import android.widget.Toast
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView

class ResidentDashboardActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var tvWelcome: TextView
    private lateinit var tvUserPurok: TextView
    private lateinit var tvActiveTrucksCount: TextView
    private lateinit var tvEstimatedTime: TextView
    private lateinit var cardNearbyAlert: MaterialCardView
    private lateinit var tvNearbyTitle: TextView
    private lateinit var tvNearbyMessage: TextView
    private lateinit var viewNotificationDot: View
    private var lastProcessedAlertTimestamp: Long = 0L
    private var mapFragment: MapboxFragment? = null
    private var isGpsActive: Boolean = true

    private var logoutDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Continuous GPS Monitoring
        lifecycle.addObserver(GpsStatusMonitor(this) { isEnabled ->
            isGpsActive = isEnabled
            if (!isEnabled) {
                mapFragment?.clearMap() // Pause map visuals
            }
        })

        enableEdgeToEdge()
        setContentView(R.layout.activity_resident_dashboard)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.resident_dashboard_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sessionManager = SessionManager(this)
        tvWelcome = findViewById(R.id.tvWelcome)
        tvUserPurok = findViewById(R.id.tvUserPurok)
        tvActiveTrucksCount = findViewById(R.id.tvActiveTrucksCount)
        tvEstimatedTime = findViewById(R.id.tvEstimatedTime)
        cardNearbyAlert = findViewById(R.id.cardNearbyAlert)
        tvNearbyTitle = findViewById(R.id.tvNearbyTitle)
        tvNearbyMessage = findViewById(R.id.tvNearbyMessage)
        viewNotificationDot = findViewById(R.id.view_notification_dot)

        val user = sessionManager.getUser()
        tvWelcome.text = user?.name ?: "Juan Dela Cruz"
        tvUserPurok.text = user?.purok ?: "Purok 2"

        mapFragment = supportFragmentManager.findFragmentById(R.id.map_fragment_container) as? MapboxFragment
        if (mapFragment == null) {
            mapFragment = MapboxFragment.newInstance(MapboxFragment.MODE_DASHBOARD)
            supportFragmentManager.beginTransaction()
                .replace(R.id.map_fragment_container, mapFragment!!)
                .commit()
        }

        setupClickListeners()
        setupBottomNavigation()
        setupLogout()
        setupNotificationListener()
        setupNearbyAlertListener()
        checkLocationPermissions()
    }

    private fun setupNotificationListener() {
        val user = sessionManager.getUser() ?: return
        val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
        val notificationsRef = FirebaseDatabase.getInstance(dbUrl).getReference("notifications").child(user.userId.toString())

        notificationsRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                var unreadCount = 0
                for (notifSnapshot in snapshot.children) {
                    val isRead = notifSnapshot.child("isRead").getValue(Boolean::class.java) ?: false
                    if (!isRead) unreadCount++
                }
                viewNotificationDot.visibility = if (unreadCount > 0) View.VISIBLE else View.GONE
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    private fun setupNearbyAlertListener() {
        val user = sessionManager.getUser() ?: return
        val purok = user.purok ?: "Purok 2"
        val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
        val alertRef = FirebaseDatabase.getInstance(dbUrl).getReference("alerts").child(purok)
        val notificationsRef = FirebaseDatabase.getInstance(dbUrl).getReference("notifications").child(user.userId.toString())

        alertRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (snapshot.exists()) {
                    val message = snapshot.child("message").getValue(String::class.java) ?: ""
                    val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                    val type = snapshot.child("type").getValue(String::class.java) ?: "ALERT"
                    val truckId = snapshot.child("truck_id").getValue(String::class.java) ?: "Unknown"
                    
                    // Show alert if it's less than 30 minutes old
                    val now = System.currentTimeMillis()
                    if (now - timestamp < 30 * 60 * 1000) {
                        tvNearbyMessage.text = message
                        cardNearbyAlert.visibility = View.VISIBLE

                        // Capture new alerts into history
                        if (timestamp > lastProcessedAlertTimestamp) {
                            lastProcessedAlertTimestamp = timestamp
                            saveAlertToHistory(notificationsRef, title = "Truck Update: $truckId", message = message, type = type, timestamp = timestamp)
                        }
                    } else {
                        cardNearbyAlert.visibility = View.GONE
                    }
                } else {
                    cardNearbyAlert.visibility = View.GONE
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    private fun saveAlertToHistory(ref: com.google.firebase.database.DatabaseReference, title: String, message: String, type: String, timestamp: Long) {
        // Check if this specific alert already exists in history to avoid duplicates
        ref.orderByChild("timestamp").equalTo(timestamp.toDouble()).addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (!snapshot.exists()) {
                    val notification = com.example.myapplication.models.SystemNotification(
                        type = type,
                        title = title,
                        message = message,
                        timestamp = timestamp,
                        isRead = false
                    )
                    ref.push().setValue(notification)
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    private fun checkLocationPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 1001)
        } else {
            startLocationService()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationService()
        }
    }

    private fun startLocationService() {
        startService(Intent(this, LocationUpdateService::class.java))
    }

    private fun setupTestTrigger() {
        findViewById<MaterialCardView>(R.id.cardNearbyAlert).setOnClickListener {
            // Check Notification Permission first
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Please allow notifications in settings", Toast.LENGTH_LONG).show()
                    val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                    }
                    startActivity(intent)
                    return@setOnClickListener
                }
            }

            val user = sessionManager.getUser() ?: return@setOnClickListener
            val purok = user.purok ?: "Purok 2"
            val driverName = "John Driver"
            val truckId = "GT-777"
            val lat = 13.9402
            val lng = 121.1638
            
            val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
            val db = FirebaseDatabase.getInstance(dbUrl)
            
            // 1. Send Alert (for Notification) - Simulating an Arrival Alert
            val alertData = mapOf(
                "message" to "The garbage truck has arrived at $purok. Please bring out your trash!",
                "timestamp" to System.currentTimeMillis(),
                "driver" to driverName,
                "truck_id" to truckId,
                "latitude" to lat,
                "longitude" to lng,
                "type" to "ARRIVAL_ALERT"
            )
            db.getReference("alerts").child(purok).setValue(alertData)
            
            // 2. Update Map Position (for real-time map marker)
            val truckData = mapOf(
                "truckId" to truckId,
                "driverName" to driverName,
                "latitude" to lat,
                "longitude" to lng,
                "speed" to 15.5,
                "isFull" to false,
                "status" to "active",
                "updatedAt" to System.currentTimeMillis().toString()
            )
            db.getReference("truck_locations").child(truckId).setValue(truckData)
            
            Toast.makeText(this, "Simulating $truckId nearby...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupLogout() {
        findViewById<View>(R.id.btn_logout).setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun showLogoutConfirmation() {
        if (isFinishing || isDestroyed) return
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_logout_confirmation_resident, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        logoutDialog = alertDialog

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            alertDialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btn_confirm_logout).setOnClickListener {
            sessionManager.logout()
            stopService(Intent(this, LocationUpdateService::class.java))
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        alertDialog.show()
    }

    override fun onDestroy() {
        logoutDialog?.dismiss()
        logoutDialog = null
        super.onDestroy()
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.btn_notifications).setOnClickListener {
            showNotificationsDialog()
        }

        findViewById<MaterialCardView>(R.id.cardTrackTruckQuick).setOnClickListener {
            startActivity(Intent(this, TrackTrucksActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardFileComplaintQuick).setOnClickListener {
            startActivity(Intent(this, ResidentComplaintsActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardRateService).setOnClickListener {
            showFeedbackDialog()
        }

        findViewById<TextView>(R.id.tvFullMap).setOnClickListener {
            startActivity(Intent(this, TrackTrucksActivity::class.java))
        }
    }

    private fun showNotificationsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_notifications, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val rvNotifications = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_notifications)
        val llEmpty = dialogView.findViewById<android.widget.LinearLayout>(R.id.ll_empty_state)
        val btnClearAll = dialogView.findViewById<TextView>(R.id.btn_clear_all)
        val btnClose = dialogView.findViewById<Button>(R.id.btn_close)

        rvNotifications.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        
        val user = sessionManager.getUser()
        val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
        val notificationsRef = FirebaseDatabase.getInstance(dbUrl).getReference("notifications").child(user?.userId.toString())

        val notificationList = mutableListOf<com.example.myapplication.models.SystemNotification>()
        val adapter = com.example.myapplication.adapters.NotificationAdapter(this, notificationList) { notification ->
            // Mark as read
            if (!notification.isRead) {
                notificationsRef.child(notification.id).child("isRead").setValue(true)
            }
            // Handle specific actions if needed (e.g. open complaint detail)
        }
        rvNotifications.adapter = adapter

        notificationsRef.orderByChild("timestamp").addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                notificationList.clear()
                for (notifSnapshot in snapshot.children) {
                    val notif = notifSnapshot.getValue(com.example.myapplication.models.SystemNotification::class.java)
                    if (notif != null) {
                        notif.id = notifSnapshot.key ?: ""
                        notificationList.add(notif)
                    }
                }
                notificationList.sortByDescending { it.timestamp }
                
                if (notificationList.isEmpty()) {
                    rvNotifications.visibility = View.GONE
                    llEmpty?.visibility = View.VISIBLE
                } else {
                    rvNotifications.visibility = View.VISIBLE
                    llEmpty?.visibility = View.GONE
                    adapter.updateData(notificationList)
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })

        btnClearAll.setOnClickListener {
            notificationsRef.removeValue()
        }

        btnClose.setOnClickListener {
            alertDialog.dismiss()
        }

        alertDialog.show()
    }

    private fun showFeedbackDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_feedback, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val ratingBar = dialogView.findViewById<android.widget.RatingBar>(R.id.ratingBar)
        val etFeedback = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etFeedback)
        val btnSubmit = dialogView.findViewById<Button>(R.id.btnSubmitFeedback)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)

        btnCancel.setOnClickListener { alertDialog.dismiss() }

        btnSubmit.setOnClickListener {
            val rating = ratingBar.rating
            val feedbackText = etFeedback.text.toString()
            val user = sessionManager.getUser()

            val feedbackData = mapOf(
                "userId" to (user?.userId ?: 0),
                "userName" to (user?.name ?: "Anonymous"),
                "rating" to rating,
                "feedback" to feedbackText,
                "timestamp" to System.currentTimeMillis()
            )

            val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
            FirebaseDatabase.getInstance(dbUrl).getReference("user_evaluations").push().setValue(feedbackData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Thank you for your feedback!", Toast.LENGTH_SHORT).show()
                    alertDialog.dismiss()
                }
        }

        alertDialog.show()
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.selectedItemId = R.id.nav_home

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
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
                R.id.nav_settings -> {
                    startActivity(Intent(this, ResidentSettingsActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                else -> false
            }
        }
    }
}
