package com.example.goelocation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.goelocation.databinding.ActivityMainBinding
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.SettingsClient
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val backgroundPermission = Manifest.permission.ACCESS_BACKGROUND_LOCATION
    private var hasRequestedBackgroundPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        observeLocationUpdates()
        requestForegroundPermissions()
    }

    private val foregroundPermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestForegroundPermissions() {
        val missing = foregroundPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            checkGPSAndBackgroundPermission()
        } else {
            foregroundPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    private val foregroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val denied = result.filterValues { !it }.keys
        val permanentlyDenied = denied.filter { !shouldShowRequestPermissionRationale(it) }

        when {
            denied.isEmpty() -> {
                checkGPSAndBackgroundPermission()
            }
            permanentlyDenied.isNotEmpty() -> {
                showSettingsDialog(permanentlyDenied)
            }
            else -> {
                Toast.makeText(this, "Permissions denied: $denied", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkGPSAndBackgroundPermission() {
        val locationRequest = LocationRequest.create()
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)

        val settingsClient: SettingsClient = LocationServices.getSettingsClient(this)
        val task = settingsClient.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            checkAndRequestBackgroundPermission()
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    exception.startResolutionForResult(this, 1001)
                } catch (sendEx: Exception) {
                    Toast.makeText(this, "Failed to prompt GPS dialog", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "GPS is unavailable or device doesn't support it", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkAndRequestBackgroundPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, backgroundPermission)
                != PackageManager.PERMISSION_GRANTED
            ) {
                if (shouldShowRequestPermissionRationale(backgroundPermission)) {
                    showBackgroundExplanation()
                } else {
                    if (!hasRequestedBackgroundPermission) {
                        hasRequestedBackgroundPermission = true
                        backgroundPermissionLauncher.launch(backgroundPermission)
                    } else {
                        showSettingsDialog(listOf(backgroundPermission))
                    }
                }
            } else {
                startGeofenceService()
            }
        } else {
            startGeofenceService()
        }
    }

    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startGeofenceService()
        } else {
            if (!shouldShowRequestPermissionRationale(backgroundPermission)) {
                showSettingsDialog(listOf(backgroundPermission))
            } else {
                Toast.makeText(this, "Background permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startGeofenceService() {
        Toast.makeText(this, "Starting Geofence Service...", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, GeofenceService::class.java).apply {
            putExtra("latitude", 28.5782472)
            putExtra("longitude", 77.3596155)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun showSettingsDialog(deniedPermissions: List<String>) {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("Some permissions are permanently denied: $deniedPermissions. Please allow them from app settings.")
            .setPositiveButton("Open Settings") { _, _ -> openAppSettings() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun showBackgroundExplanation() {
        AlertDialog.Builder(this)
            .setTitle("Background Location Permission")
            .setMessage("To detect geofence entry/exit when app is in background, background location permission is required.")
            .setPositiveButton("Allow") { _, _ ->
                backgroundPermissionLauncher.launch(backgroundPermission)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeLocationUpdates() {
        lifecycleScope.launch {
            LocationUpdateManager.locationUpdates.collectLatest { update ->
                Log.i("MainActivityGettingData", "onReceive: ${update.latitude} ---- ${update.longitude}")
                binding.lat.text = "latitude: ${update.latitude}"
                binding.lng.text = "longitude: ${update.longitude}"
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            checkAndRequestBackgroundPermission()
        }
    }
}
