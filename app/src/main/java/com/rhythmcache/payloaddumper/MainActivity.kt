@file:OptIn(ExperimentalMaterial3Api::class)

package com.rhythmcache.payloaddumper

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import com.rhythmcache.payloaddumper.ui.screens.LocalFilesScreen
import com.rhythmcache.payloaddumper.ui.screens.RemoteFilesScreen
import com.rhythmcache.payloaddumper.ui.screens.SettingsScreen
import com.rhythmcache.payloaddumper.ui.theme.PayloadDumperTheme
import com.rhythmcache.payloaddumper.ui.theme.ThemeMode
import com.rhythmcache.payloaddumper.viewmodel.PayloadViewModel
import java.io.File

class MainActivity : ComponentActivity() {
  private val viewModel: PayloadViewModel by viewModels()
  private var hasPermission by mutableStateOf(false)

  override fun onCreate(savedInstanceState: Bundle?) {
    setTheme(R.style.Theme_PayloadDumperGUI)
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    checkPermission()
    initializeDefaultOutputDir()

    setContent {
      val context = LocalContext.current
      val prefsState = context.getSharedPreferences("settings", 0)
      var themeMode by remember {
        mutableStateOf(ThemeMode.valueOf(prefsState.getString("theme_mode", "AUTO") ?: "AUTO"))
      }

      DisposableEffect(Unit) {
        val listener =
            android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
              if (key == "theme_mode") {
                themeMode = ThemeMode.valueOf(prefs.getString("theme_mode", "AUTO") ?: "AUTO")
              }
            }
        prefsState.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefsState.unregisterOnSharedPreferenceChangeListener(listener) }
      }

      PayloadDumperTheme(themeMode = themeMode) {
        MainScreen(viewModel = viewModel, hasPermission = hasPermission)
      }
    }
  }

  override fun onResume() {
    super.onResume()
    checkPermission()
  }

  private fun checkPermission() {
    hasPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          Environment.isExternalStorageManager()
        } else {
          ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
              PackageManager.PERMISSION_GRANTED
        }
  }

  private fun initializeDefaultOutputDir() {
    val prefs = getSharedPreferences("settings", 0)
    if (!prefs.contains("output_dir")) {
      val defaultDir = File(Environment.getExternalStorageDirectory(), "payload_dumper")
      defaultDir.mkdirs()
      prefs.edit().putString("output_dir", defaultDir.absolutePath).apply()
    }
  }
}

@Composable
fun MainScreen(viewModel: PayloadViewModel, hasPermission: Boolean) {
  var selectedTab by remember { mutableStateOf(0) }

  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Text(
                  "Payload Dumper",
                  style =
                      MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold))
            })
      },
      bottomBar = {
        NavigationBar {
          NavigationBarItem(
              icon = { Icon(Icons.Default.Home, contentDescription = "Local") },
              label = { Text("Local") },
              selected = selectedTab == 0,
              onClick = { selectedTab = 0 })
          NavigationBarItem(
              icon = { Icon(Icons.Default.Cloud, contentDescription = "Remote") },
              label = { Text("Remote") },
              selected = selectedTab == 1,
              onClick = { selectedTab = 1 })
          NavigationBarItem(
              icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
              label = { Text("Settings") },
              selected = selectedTab == 2,
              onClick = { selectedTab = 2 })
        }
      }) { padding ->
        Box(modifier = Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()) {
          when (selectedTab) {
            0 -> LocalFilesScreen(viewModel = viewModel, hasPermission = hasPermission)
            1 -> RemoteFilesScreen(viewModel = viewModel, hasPermission = hasPermission)
            2 -> SettingsScreen()
          }
        }
      }
}
