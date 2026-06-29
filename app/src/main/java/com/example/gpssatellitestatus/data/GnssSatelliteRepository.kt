package com.example.gpssatellitestatus.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface GnssSatelliteRepository {
  val status: StateFlow<GnssStatusSnapshot>
  val statusInternal: MutableStateFlow<GnssStatusSnapshot>
  var minCn0Threshold: Float
  var locationPriority: Int
  fun startListening()
  fun stopListening()
  fun refreshDeviceState()
  fun clearHistory()
}

class DefaultGnssSatelliteRepository(private val context: Context) : GnssSatelliteRepository {
  private val locationManager = context.getSystemService(LocationManager::class.java)

  private val _status = MutableStateFlow(GnssStatusSnapshot())
  override val status: StateFlow<GnssStatusSnapshot> = _status.asStateFlow()
  override val statusInternal: MutableStateFlow<GnssStatusSnapshot> = _status

  override var minCn0Threshold: Float = 0f

  override var locationPriority: Int = Priority.PRIORITY_HIGH_ACCURACY
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    set(value) {
      field = value
      if (isRegistered) {
        // Restart service to apply new priority
        stopListening()
        startListening()
      }
    }

  private var isRegistered = false

  override fun refreshDeviceState() {
    val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    _status.update { 
      it.copy(
        gpsEnabled = gpsEnabled, 
        minCn0Threshold = minCn0Threshold,
        locationPriority = locationPriority
      ) 
    }
  }

  @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
  override fun startListening() {
    refreshDeviceState()
    if (isRegistered) return
    
    val intent = Intent(context, com.example.gpssatellitestatus.service.GpsService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      context.startForegroundService(intent)
    } else {
      context.startService(intent)
    }
    isRegistered = true
  }

  override fun stopListening() {
    if (!isRegistered) return
    val intent = Intent(context, com.example.gpssatellitestatus.service.GpsService::class.java)
    context.stopService(intent)
    isRegistered = false
    _status.update { it.copy(isListening = false) }
  }

  override fun clearHistory() {
    _status.update { it.copy(locationHistory = emptyList()) }
  }
}
