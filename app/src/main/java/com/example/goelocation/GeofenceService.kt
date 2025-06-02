package com.example.goelocation

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GeofenceService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var targetLat = 0.0
    private var targetLng = 0.0
    private var lastInside = false
    private val geofenceRadius = 100f
    private val channelId = "geofence_service_channel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        targetLat = intent?.getDoubleExtra("latitude", 0.0) ?: 0.0
        targetLng = intent?.getDoubleExtra("longitude", 0.0) ?: 0.0

        startForeground(1, createNotification("Monitoring your location..."))
        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val request = LocationRequest.create().apply {
            interval = 3000
            fastestInterval = 2000
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    logAndCheckLocation(location)
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }

        fusedLocationClient.requestLocationUpdates(request, locationCallback, mainLooper)
    }

    private fun logAndCheckLocation(location: Location) {
        val lat = location.latitude
        val lng = location.longitude
        val distance = FloatArray(1)
        Location.distanceBetween(lat, lng, targetLat, targetLng, distance)

        val isInside = distance[0] <= geofenceRadius
        Log.d("GeofenceService", "Lat: $lat, Lng: $lng, Distance: ${distance[0]}, Inside: $isInside")

        sendLocationUpdate(lat, lng, isInside)

        if (isInside && !lastInside) {
            showNotification("Entered location")
        } else if (!isInside && lastInside) {
            showNotification("Exited location")
        }

        lastInside = isInside
    }

    private fun sendLocationUpdate(lat: Double, lng: Double, isInside: Boolean) {
        Log.i("MainActivityGettingData", "sendLocationUpdate: $lat--Data---$lng")

        CoroutineScope(Dispatchers.Default).launch {
            LocationUpdateManager.sendLocationUpdate(LocationUpdate(lat, lng, isInside))
        }
    }

    private fun showNotification(message: String) {
        val notification = createNotification(message)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Geofence Alert")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId, "Geofence Location Service",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("GeofenceService", "Service destroyed.")
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }


    override fun onBind(intent: Intent?): IBinder? = null
}
