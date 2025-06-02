package com.example.goelocation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object LocationUpdateManager {
    private val _locationUpdates = MutableSharedFlow<LocationUpdate>(replay = 0)
    val locationUpdates: SharedFlow<LocationUpdate> = _locationUpdates

    suspend fun sendLocationUpdate(update: LocationUpdate) {
        _locationUpdates.emit(update)
    }
}

data class LocationUpdate(val latitude: Double, val longitude: Double, val isInside: Boolean)
