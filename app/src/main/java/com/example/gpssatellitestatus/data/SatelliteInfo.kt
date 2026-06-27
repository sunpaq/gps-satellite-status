package com.example.gpssatellitestatus.data

import android.location.Location

data class SatelliteInfo(
  val svid: Int,
  val constellation: Constellation,
  val cn0DbHz: Float,
  val elevationDegrees: Float,
  val azimuthDegrees: Float,
  val usedInFix: Boolean,
  val hasEphemeris: Boolean,
  val hasAlmanac: Boolean,
  val carrierFrequencyHz: Float,
)

enum class Constellation(val label: String) {
  Gps("GPS"),
  Sbas("SBAS"),
  Glonass("GLONASS"),
  Qzss("QZSS"),
  Beidou("BeiDou"),
  Galileo("Galileo"),
  Irnss("NavIC"),
  Unknown("Unknown"),
}

data class GnssStatusSnapshot(
  val satellites: List<SatelliteInfo> = emptyList(),
  val isListening: Boolean = false,
  val gpsEnabled: Boolean = false,
  val gnssSupported: Boolean = true,
  val lastUpdatedMillis: Long = 0L,
  val currentLocation: Location? = null,
  val locationHistory: List<Location> = emptyList(),
  val minCn0Threshold: Float = 0f,
  val locationPriority: Int = 100, // Priority.PRIORITY_HIGH_ACCURACY is 100
) {
  val totalCount: Int get() = satellites.size
  val usedInFixCount: Int get() = satellites.count { it.usedInFix }
  val averageCn0: Float
    get() =
      if (satellites.isEmpty()) 0f
      else satellites.map { it.cn0DbHz }.average().toFloat()
}
