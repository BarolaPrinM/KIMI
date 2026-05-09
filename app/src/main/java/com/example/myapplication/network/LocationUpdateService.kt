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

    data class PurokZone(val name: String, val latitude: Double, val longitude: Double, val radiusMeters: Double)
    private val purokZones = listOf(
        PurokZone("Purok 2", 13.9402, 121.1638, 220.0),
        PurokZone("Purok 3", 13.9375, 121.1660, 230.0),
        PurokZone("Purok 4", 13.9430, 121.1625, 250.0),
        PurokZone("Dos Riles", 13.9358, 121.1595, 200.0),
        PurokZone("Sentro", 13.9388, 121.1645, 180.0),
        PurokZone("San Isidro", 13.9342, 121.1620, 210.0),
        PurokZone("Paraiso", 13.9325, 121.1602, 200.0),
        PurokZone("Riverside", 13.9365, 121.1678, 240.0),
        PurokZone("Kalaw Street", 13.9395, 121.1580, 150.0),
        PurokZone("Home Subdivision", 13.9415, 121.1565, 260.0),
        PurokZone("Tanco Road / Ayala Highway", 13.9312, 121.1705, 300.0),
        PurokZone("Brixton Area", 13.9382, 121.1552, 230.0)
    )

    private val notifiedZones = mutableSetOf<String>()
    private var lastInsidePurok: String? = null

    private var lastLocation: Location? = null

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    lastLocation = location
                    updateLiveLocation(location)
                }
            }
        }
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
        val timestamp = System.currentTimeMillis().toString()

        if (user.role.lowercase() == "driver") {
            val truckId = user.preferredTruck ?: "GT-001"
            val isFull = false 

            val database = FirebaseDatabase.getInstance(dbUrl).getReference("truck_locations")
            
            val locationData = mapOf(
                "truckId" to truckId,
                "driverId" to user.userId,
                "driverName" to user.name,
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "speed" to location.speed.toDouble(),
                "isFull" to isFull,
                "status" to "active",
                "updatedAt" to timestamp
            )

            // Update current position
            database.child(truckId).setValue(locationData)
            
            // ✅ Geofencing Check for Driver
            checkGeofences(location, user.name)

            // Save full history
            val pointData = mapOf(
                "lat" to location.latitude,
                "lng" to location.longitude,
                "time" to timestamp
            )
            database.child(truckId).child("route_history").push().setValue(pointData)

            // Retrofit fallback
            RetrofitClient.instance.updateLocation(
                user.userId, location.latitude, location.longitude, 
                truckId, location.speed.toDouble(), isFull
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
        for (zone in purokZones) {
            val zoneLoc = Location("").apply {
                latitude = zone.latitude
                longitude = zone.longitude
            }
            val distance = location.distanceTo(zoneLoc)

            if (distance <= zone.radiusMeters) {
                if (zone.name != lastInsidePurok && !notifiedZones.contains(zone.name)) {
                    // Trigger alert in Firebase
                    val alertRef = FirebaseDatabase.getInstance(dbUrl).getReference("alerts").child(zone.name)
                    val alertData = mapOf(
                        "message" to "Approaching ${zone.name}",
                        "timestamp" to System.currentTimeMillis(),
                        "driver" to driverName,
                        "latitude" to location.latitude,
                        "longitude" to location.longitude
                    )
                    alertRef.setValue(alertData)
                    
                    notifiedZones.add(zone.name)
                    lastInsidePurok = zone.name
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            
            // BACKGROUND SERVICE CHANNEL (Silent)
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "System Background Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Running in background"
                setShowBadge(false)
            }
            manager.createNotificationChannel(serviceChannel)

            // ALERT CHANNEL (High Priority)
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
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        alertListener?.let { 
            val user = sessionManager.getUser()
            if (user?.role?.lowercase() == "resident" && user.purok != null) {
                FirebaseDatabase.getInstance(dbUrl).getReference("alerts").child(user.purok).removeEventListener(it)
            }
        }
    }
}
