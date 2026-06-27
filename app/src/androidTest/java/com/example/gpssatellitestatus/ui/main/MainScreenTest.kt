package com.example.gpssatellitestatus.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.gpssatellitestatus.theme.GPSSatelliteStatusTheme
import org.junit.Rule
import org.junit.Test

class MainScreenTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun permissionPrompt_isShownWithoutPermission() {
    composeTestRule.setContent {
      GPSSatelliteStatusTheme {
        MainScreen(hasLocationPermission = false, onRequestPermission = {})
      }
    }
    composeTestRule.onNodeWithText("Location permission required").assertExists()
    composeTestRule.onNodeWithText("Grant permission").assertExists()
  }
}
