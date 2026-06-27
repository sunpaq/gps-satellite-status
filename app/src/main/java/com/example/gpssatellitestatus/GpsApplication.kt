package com.example.gpssatellitestatus

import android.app.Application
import com.example.gpssatellitestatus.data.DefaultGnssSatelliteRepository
import com.example.gpssatellitestatus.data.GnssSatelliteRepository

class GpsApplication : Application() {
  lateinit var repository: GnssSatelliteRepository

  override fun onCreate() {
    super.onCreate()
    repository = DefaultGnssSatelliteRepository(this)
  }
}
