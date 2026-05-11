package com.example.myapplication.network

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import com.example.myapplication.models.ApiResponse
import com.example.myapplication.utils.PredictionEngine
import com.example.myapplication.utils.PurokManager
import com.example.myapplication.utils.SessionManager
import com.google.android.gms.location.*
import com.google.firebase.database.FirebaseDatabase
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

import android.media.RingtoneManager
import android.graphics.Color
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class LocationUpdateService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var sessionManager: SessionManager
    private var alertListener: ValueEventListener? = null
    private val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"

    private val NOTIFICATION_ID = 12345
    private val ALERT_NOTIFICATION_ID = 54321
    private val CHANNEL_ID = "location_service_channel"
    private val ALERT_CHANNEL_ID = "garbage_alert_channel"

    private val notifiedZones = mutableSetOf<String>()
    private var lastInsidePurok: String? = null
    private val pendingPredictions = mutableMapOf<String, Long>() // Key: TruckID_ZoneName

    private var lastLocation: Location? = null
    private var isTruckFull = false
    private var isFullListener: ValueEventListener? = null
    
    private var serviceStartTime = 0L
    private var zonesCoveredThisTrip = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager(this)
        serviceStartTime = System.currentTimeMillis()

        val user = sessionManager.getUser()
        if (user?.role?.lowercase() == "driver") {
            val truckId = user.preferredTruck ?: "GT-001"
            isFullListener = FirebaseDatabase.getInstance(dbUrl).getReference("truck_locations")
                .child(truckId).child("isFull")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        isTruckFull = snapshot.getValue(Boolean::class.java) ?: false
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    lastLocation = location
                    updateLiveLocation(location)
                    
                    // ✅ FEATURE: AUTO-FULL LOGIC
                    checkAutoFullConditions()
                }
            }
        }
    }

    private fun checkAutoFullConditions() {
        val user = sessionManager.getUser() ?: return
        if (user.role.lowercase() != "driver") return
        if (isTruckFull) return

        val truckId = user.preferredTruck ?: "GT-001"
        val currentTime = System.currentTimeMillis()
        
        // Condition 1: Auto-full after 4 hours (simulating 3-5 hrs range)
        val fourHoursInMillis = 4 * 60 * 60 * 1000L
        if (currentTime - serviceStartTime > fourHoursInMillis) {
            markTruckAsFull(truckId, "AUTO_TIME")
            return
        }

        // Condition 2: Auto-full after covering 3 unique zones (simulating "after coverage")
        if (zonesCoveredThisTrip.size >= 3) {
            markTruckAsFull(truckId, "AUTO_COVERAGE")
        }
    }

    private fun markTruckAsFull(truckId: String, reason: String) {
        isTruckFull = true
        val ref = FirebaseDatabase.getInstance(dbUrl).getReference("truck_locations").child(truckId)
        ref.child("isFull").setValue(true)
        ref.child("status").setValue("full")
        
        // Log for analytics
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val logRef = FirebaseDatabase.getInstance(dbUrl).getReference("collection_logs").push()
        logRef.setValue(mapOf(
            "truckId" to truckId,
            "timestamp" to System.currentTimeMillis(),
            "type" to "FULL",
            "reason" to reason,
            "date" to today
        ))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startLocationUpdates()
        setupAlertListener()

        return START_STICKY
    }

    private fun setupAlertListener() {
        val user = sessionManager.getUser() ?: return
        if (user.role.lowercase() != "resident") return
        
        val purok = user.purok ?: "Purok 2"
        val alertRef = FirebaseDatabase.getInstance(dbUrl).getReference("alerts").child(purok)
        
        alertListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val driverName = snapshot.child("driver").getValue(String::class.java) ?: "Unknown Driver"
                val truckId = snapshot.child("truck_id").getValue(String::class.java) ?: "GT-001"
                val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                val truckLat = snapshot.child("latitude").getValue(Double::class.java)
                val truckLng = snapshot.child("longitude").getValue(Double::class.java)
                
                if (System.currentTimeMillis() - timestamp < 600000) {
                    var distanceText = ""
                    if (truckLat != null && truckLng != null && lastLocation != null) {
                        val truckLoc = Location("").apply {
                            latitude = truckLat
                            longitude = truckLng
                        }
                        val distanceInMeters = lastLocation!!.distanceTo(truckLoc)
                        distanceText = if (distanceInMeters >= 1000) {
                            String.format("%.1f km away", distanceInMeters / 1000)
                        } else {
                            String.format("%d meters away", distanceInMeters.toInt())
                        }
                    }

                    val title = "Garbage Truck Approaching"
                    val message = "Truck $truckId driven by $driverName is approaching your area ($distanceText). Please bring out your trash."
                    
                    showSystemNotification(title, message)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        alertRef.addValueEventListener(alertListener!!)
    }

    private fun showSystemNotification(title: String, message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_truck)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setVibrate(longArrayOf(1000, 1000, 1000))
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2500)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(2000)
            .setMinUpdateDistanceMeters(5f)
            .setMaxUpdateDelayMillis(4000)
            .build()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }
        
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun updateLiveLocation(location: Location) {
        val user = sessionManager.getUser() ?: return
        val timestamp = System.currentTimeMillis()

        if (user.role.lowercase() == "driver") {
            val truckId = user.preferredTruck ?: "GT-001"
            
            val database = FirebaseDatabase.getInstance(dbUrl).getReference("truck_locations")
            
            // First, fetch current status from DB to ensure we don't overwrite it with a default "active"
            database.child(truckId).get().addOnSuccessListener { snapshot ->
                val currentStatus = snapshot.child("status").getValue(String::class.java) ?: "active"
                val isFull = snapshot.child("isFull").getValue(Boolean::class.java) ?: false
                isTruckFull = isFull // Sync local state

                val locationData = mutableMapOf<String, Any>(
                    "truckId" to truckId,
                    "driverId" to user.userId,
                    "driverName" to user.name,
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "speed" to location.speed.toDouble(),
                    "isFull" to isFull,
                    "status" to currentStatus,
                    "updatedAt" to timestamp
                )

                // Update current position and preserve status
                database.child(truckId).updateChildren(locationData)
                
                // ✅ Geofencing Check for Driver - only if NOT full and ACTIVE
                if (!isFull && currentStatus == "active") {
                    checkGeofences(location, user.name)
                }

                // Save route history
                val pointData = mapOf(
                    "lat" to location.latitude,
                    "lng" to location.longitude,
                    "time" to timestamp
                )
                database.child(truckId).child("route_history").push().setValue(pointData)
            }

            // Retrofit fallback
            RetrofitClient.instance.updateLocation(
                user.userId, location.latitude, location.longitude, 
                truckId, location.speed.toDouble(), isTruckFull
            ).enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {}
                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {}
            })
        } else {
            // ✅ SAVING RESIDENT LOCATION
            val database = FirebaseDatabase.getInstance(dbUrl).getReference("resident_locations")
            val locationData = mapOf(
                "userId" to user.userId,
                "name" to user.name,
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "purok" to (user.purok ?: "Unknown"),
                "updatedAt" to timestamp
            )
            database.child(user.userId.toString()).setValue(locationData)
        }
    }

    private fun checkGeofences(location: Location, driverName: String) {
        val user = sessionManager.getUser()
        val truckId = user?.preferredTruck ?: "GT-001"
        var currentlyInside: String? = null

        // Use Centralized PurokManager
        val zone = PurokManager.getZoneAt(location.latitude, location.longitude)
        if (zone != null) {
            currentlyInside = zone.name
            zonesCoveredThisTrip.add(zone.name)
            
            // Check checkpoints
            zone.checkpoints.forEachIndexed { index, checkpoint ->
                val cpLoc = Location("").apply {
                    latitude = checkpoint.first
                    longitude = checkpoint.second
                }
                if (location.distanceTo(cpLoc) <= 50.0) {
                    logCheckpointHit(truckId, zone.name, "CP_$index")
                }
            }
        }

        // Handle Entry
        if (currentlyInside != null && currentlyInside != lastInsidePurok) {
            logZoneEvent(truckId, currentlyInside, "ENTRY")
            
            // Log Actual Arrival if prediction was pending
            val predictionKey = "${truckId}_$currentlyInside"
            if (pendingPredictions.containsKey(predictionKey)) {
                val predictedTime = pendingPredictions[predictionKey] ?: 0L
                logPredictionEval(truckId, currentlyInside, predictedTime, System.currentTimeMillis())
                pendingPredictions.remove(predictionKey)
            }

            // ✅ CONDITION: ONLY NOTIFY IF NOT FULL
            if (!isTruckFull && !notifiedZones.contains(currentlyInside)) {
                // Trigger alert in Firebase
                val alertRef = FirebaseDatabase.getInstance(dbUrl).getReference("alerts").child(currentlyInside)
                
                val etaSeconds = PredictionEngine.predictArrivalTime(0.0, emptyList())
                
                val alertData = mapOf(
                    "message" to "Approaching $currentlyInside",
                    "timestamp" to System.currentTimeMillis(),
                    "driver" to driverName,
                    "truck_id" to truckId,
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "predicted_eta" to etaSeconds
                )
                alertRef.setValue(alertData)
                notifiedZones.add(currentlyInside)
            }
        }

        // Handle Proximity Alert & Prediction
        for (z in PurokManager.purokZones) {
            if (z.name == currentlyInside) continue
            
            val zoneLoc = Location("").apply {
                latitude = z.latitude
                longitude = z.longitude
            }
            val distance = location.distanceTo(zoneLoc)
            
            if (distance < 1000 && distance > z.radiusMeters && !pendingPredictions.containsKey("${truckId}_${z.name}")) {
                val etaSeconds = PredictionEngine.predictArrivalTime(distance.toDouble(), emptyList())
                val predictedArrivalTime = System.currentTimeMillis() + (etaSeconds * 1000).toLong()
                pendingPredictions["${truckId}_${z.name}"] = predictedArrivalTime
            }
        }

        // Handle Exit
        if (lastInsidePurok != null && lastInsidePurok != currentlyInside) {
            logZoneEvent(truckId, lastInsidePurok!!, "EXIT")
        }

        lastInsidePurok = currentlyInside
    }

    private fun logCheckpointHit(truckId: String, zoneName: String, cpId: String) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val ref = FirebaseDatabase.getInstance(dbUrl).getReference("purok_coverage")
            .child(today)
            .child(zoneName)
            .child(cpId)
        ref.setValue(true)
    }

    private fun logZoneEvent(truckId: String, zoneName: String, type: String) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val logRef = FirebaseDatabase.getInstance(dbUrl).getReference("collection_logs").push()
        val logData = mapOf(
            "truckId" to truckId,
            "zoneName" to zoneName,
            "timestamp" to System.currentTimeMillis(),
            "type" to type,
            "date" to today
        )
        logRef.setValue(logData)
    }

    private fun logPredictionEval(truckId: String, zoneName: String, predicted: Long, actual: Long) {
        val evalRef = FirebaseDatabase.getInstance(dbUrl).getReference("prediction_eval").push()
        val errorSeconds = (actual - predicted) / 1000.0
        val evalData = mapOf(
            "truckId" to truckId,
            "zoneName" to zoneName,
            "predictedTimestamp" to predicted,
            "actualTimestamp" to actual,
            "errorSeconds" to errorSeconds,
            "timestamp" to System.currentTimeMillis()
        )
        evalRef.setValue(evalData)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "System Background Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Running in background"
                setShowBadge(false)
            }
            manager.createNotificationChannel(serviceChannel)

            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Garbage Collection Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when the garbage truck is nearby"
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Waste Management System")
            .setContentText("Service is active")
            .setSmallIcon(R.drawable.ic_truck)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isFullListener?.let {
            val user = sessionManager.getUser()
            val truckId = user?.preferredTruck ?: "GT-001"
            FirebaseDatabase.getInstance(dbUrl).getReference("truck_locations")
                .child(truckId).child("isFull")
                .removeEventListener(it)
        }
        fusedLocationClient.removeLocationUpdates(locationCallback)
        alertListener?.let { 
            val user = sessionManager.getUser()
            if (user?.role?.lowercase() == "resident" && user.purok != null) {
                FirebaseDatabase.getInstance(dbUrl).getReference("alerts").child(user.purok).removeEventListener(it)
            }
        }
        
        // When service stops, mark status as offline in DB
        val user = sessionManager.getUser()
        if (user?.role?.lowercase() == "driver") {
            val truckId = user.preferredTruck ?: "GT-001"
            FirebaseDatabase.getInstance(dbUrl).getReference("truck_locations").child(truckId).child("status").setValue("offline")
        }
        
        super.onDestroy()
    }
}
