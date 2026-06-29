package com.example.gpssatellitestatus.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.gpssatellitestatus.GpsApplication
import com.example.gpssatellitestatus.MainActivity
import com.example.gpssatellitestatus.R
import com.example.gpssatellitestatus.data.Constellation
import com.example.gpssatellitestatus.data.SatelliteInfo
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.flow.update

class GpsService : Service() {

    private lateinit var locationManager: LocationManager
    private lateinit var fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient
    private lateinit var powerManager: PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val CHANNEL_ID = "gps_service_channel"
    private val NOTIFICATION_ID = 1

    private var isListening = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            updateRepositoryLocation(location)
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {
            (application as GpsApplication).repository.refreshDeviceState()
        }
        override fun onProviderDisabled(provider: String) {
            (application as GpsApplication).repository.refreshDeviceState()
        }
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val repository = (application as GpsApplication).repository
            val minCn0Threshold = repository.minCn0Threshold
            
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

            repository.statusInternal.update { snapshot ->
                snapshot.copy(
                    satellites = satellites,
                    lastUpdatedMillis = System.currentTimeMillis(),
                    minCn0Threshold = minCn0Threshold,
                    locationPriority = repository.locationPriority,
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_SERVICE) {
            stopListening()
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundService()
        startListening()
        return START_STICKY
    }

    private fun startForegroundService() {
        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPS Tracking Active")
            .setContentText("Recording location and satellite status in background.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startListening() {
        if (isListening) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        acquireWakeLock()

        try {
            locationManager.registerGnssStatusCallback(gnssCallback, mainHandler)
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                locationListener,
                mainHandler.looper
            )

            val priority = (application as GpsApplication).repository.locationPriority
            val locationRequest = LocationRequest.Builder(priority, 1000L)
                .setMinUpdateIntervalMillis(500L)
                .build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainHandler.looper
            )

            isListening = true
            (application as GpsApplication).repository.statusInternal.update {
                it.copy(isListening = true, gnssSupported = true)
            }
        } catch (_: Exception) {
            (application as GpsApplication).repository.statusInternal.update {
                it.copy(isListening = false, gnssSupported = false)
            }
        }
    }

    private fun stopListening() {
        if (!isListening) return
        locationManager.unregisterGnssStatusCallback(gnssCallback)
        locationManager.removeUpdates(locationListener)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        releaseWakeLock()
        isListening = false
        (application as GpsApplication).repository.statusInternal.update {
            it.copy(isListening = false)
        }
    }

    private fun updateRepositoryLocation(location: Location) {
        (application as GpsApplication).repository.statusInternal.update {
            it.copy(
                currentLocation = location,
                locationHistory = it.locationHistory + location
            )
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GpsService::WakeLock")
            wakeLock?.acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "GPS Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopListening()
        super.onDestroy()
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

    companion object {
        const val ACTION_STOP_SERVICE = "com.example.gpssatellitestatus.STOP_SERVICE"
    }
}
