package com.example.gpssatellitestatus.data

import android.Manifest
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface GnssSatelliteRepository {
  val status: StateFlow<GnssStatusSnapshot>
  var minCn0Threshold: Float
  var locationPriority: Int
  fun startListening()
  fun stopListening()
  fun refreshDeviceState()
  fun clearHistory()
}

class DefaultGnssSatelliteRepository(context: Context) : GnssSatelliteRepository {
  private val locationManager = context.getSystemService(LocationManager::class.java)
  private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
  private val mainHandler = Handler(Looper.getMainLooper())

  private val _status = MutableStateFlow(GnssStatusSnapshot())
  override val status: StateFlow<GnssStatusSnapshot> = _status.asStateFlow()

  override var minCn0Threshold: Float = 0f

  override var locationPriority: Int = Priority.PRIORITY_HIGH_ACCURACY
    set(value) {
      field = value
      if (isRegistered) {
        // Restart listening to apply new priority
        stopListening()
        startListening()
      }
    }

  private var isRegistered = false

  private val locationCallback = object : LocationCallback() {
    override fun onLocationResult(result: LocationResult) {
      val location = result.lastLocation ?: return
      _status.update {
        it.copy(
          currentLocation = location,
          locationHistory = it.locationHistory + location
        )
      }
    }
  }

  private val locationListener = object : LocationListener {
    override fun onLocationChanged(location: Location) {}
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) { refreshDeviceState() }
    override fun onProviderDisabled(provider: String) { refreshDeviceState() }
  }

  private val gnssCallback =
    object : GnssStatus.Callback() {
      override fun onSatelliteStatusChanged(status: GnssStatus) {
        val satellites = buildList {
          for (index in 0 until status.satelliteCount) {
            val cn0 = status.getCn0DbHz(index)
            if (cn0 < minCn0Threshold) continue

            val carrierFrequency = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              status.getCarrierFrequencyHz(index)
            } else {
              0f
            }
            add(
              SatelliteInfo(
                svid = status.getSvid(index),
                constellation = status.getConstellationType(index).toConstellation(),
                cn0DbHz = cn0,
                elevationDegrees = status.getElevationDegrees(index),
                azimuthDegrees = status.getAzimuthDegrees(index),
                usedInFix = status.usedInFix(index),
                hasEphemeris = status.hasEphemerisData(index),
                hasAlmanac = status.hasAlmanacData(index),
                carrierFrequencyHz = carrierFrequency,
              )
            )
          }
        }.distinctBy { "${it.constellation.name}-${it.svid}-${it.carrierFrequencyHz}" }
        .sortedWith(
          compareByDescending<SatelliteInfo> { it.usedInFix }
            .thenByDescending { it.cn0DbHz }
            .thenBy { it.constellation.label }
            .thenBy { it.svid }
        )

        _status.update {
          it.copy(
            satellites = satellites,
            lastUpdatedMillis = System.currentTimeMillis(),
            minCn0Threshold = minCn0Threshold,
            locationPriority = locationPriority,
          )
        }
      }
    }

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
    try {
      locationManager.registerGnssStatusCallback(gnssCallback, mainHandler)

      // Start raw GPS updates to keep GNSS hardware alive
      locationManager.requestLocationUpdates(
        LocationManager.GPS_PROVIDER,
        1000L,
        0f,
        locationListener,
        mainHandler.looper
      )

      // Start Fused Location updates for better accuracy reporting
      val locationRequest = LocationRequest.Builder(locationPriority, 1000L)
        .setMinUpdateIntervalMillis(500L)
        .build()

      fusedLocationClient.requestLocationUpdates(
        locationRequest,
        locationCallback,
        mainHandler.looper
      )

      isRegistered = true
      _status.update { it.copy(isListening = true, gnssSupported = true) }
    } catch (_: Exception) {
      _status.update { it.copy(isListening = false, gnssSupported = false) }
    }
  }

  override fun stopListening() {
    if (!isRegistered) return
    locationManager.unregisterGnssStatusCallback(gnssCallback)
    locationManager.removeUpdates(locationListener)
    fusedLocationClient.removeLocationUpdates(locationCallback)
    isRegistered = false
    _status.update { it.copy(isListening = false) }
  }

  override fun clearHistory() {
    _status.update { it.copy(locationHistory = emptyList()) }
  }

  private fun Int.toConstellation(): Constellation =
    when (this) {
      GnssStatus.CONSTELLATION_GPS -> Constellation.Gps
      GnssStatus.CONSTELLATION_SBAS -> Constellation.Sbas
      GnssStatus.CONSTELLATION_GLONASS -> Constellation.Glonass
      GnssStatus.CONSTELLATION_QZSS -> Constellation.Qzss
      GnssStatus.CONSTELLATION_BEIDOU -> Constellation.Beidou
      GnssStatus.CONSTELLATION_GALILEO -> Constellation.Galileo
      GnssStatus.CONSTELLATION_IRNSS -> Constellation.Irnss
      else -> Constellation.Unknown
    }
}
