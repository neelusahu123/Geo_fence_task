package com.example.goelocation

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return

        if (event.hasError()) {
            Log.e("GeofenceReceiver", "Error: ${event.errorCode}")
            return
        }

        val message = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "Entered Geofence!"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "Exited Geofence!"
            else -> "Unknown transition!"
        }

        Log.i("GeofenceReceiver", message)

        val notification = NotificationCompat.Builder(context, "geofence_service_channel")
            .setContentTitle("Geofence Alert")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context)
            .notify(System.currentTimeMillis().toInt(), notification)
    }
}
