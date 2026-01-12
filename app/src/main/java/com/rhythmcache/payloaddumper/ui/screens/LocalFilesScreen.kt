package com.rhythmcache.payloaddumper.ui.screens

import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rhythmcache.payloaddumper.*
import com.rhythmcache.payloaddumper.R
import com.rhythmcache.payloaddumper.state.UiState
import com.rhythmcache.payloaddumper.ui.screens.components.IncrementalOtaDialog
import com.rhythmcache.payloaddumper.ui.screens.components.PartitionsView
import com.rhythmcache.payloaddumper.ui.screens.components.PermissionCard
import com.rhythmcache.payloaddumper.viewmodel.PayloadViewModel
import java.io.File

@Composable
fun LocalFilesScreen(viewModel: PayloadViewModel, hasPermission: Boolean) {
  val context = LocalContext.current
  val uiState by viewModel.localUiState.collectAsState()
  val prefs = context.getSharedPreferences("settings", 0)
  var showFilePicker by remember { mutableStateOf(false) }
  var showIncrementalDialog by remember { mutableStateOf(false) }
  var showSourceDirPicker by remember { mutableStateOf(false) }

  val currentState = uiState as? UiState.PartitionsLoaded
  LaunchedEffect(currentState) {
    if (currentState != null &&
        currentState.payloadInfo.is_incremental &&
        currentState.sourceDirectory == null) {
      showIncrementalDialog = true
    }
  }

  Column(
      modifier = Modifier.fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!hasPermission) {
          PermissionCard()
        }

        when (val state = uiState) {
          is UiState.Idle -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Button(
                  onClick = { showFilePicker = true },
                  enabled = hasPermission,
                  modifier = Modifier.fillMaxWidth(0.6f).height(56.dp)) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.choose_file))
                  }
            }
          }
          is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator()
            }
          }
          is UiState.PartitionsLoaded -> {
            PartitionsView(
                state = state,
                viewModel = viewModel,
                isLocal = true,
                onLoadDifferent = { viewModel.resetLocal() })
          }
          is UiState.Error -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                  Text(
                      stringResource(R.string.error_prefix, state.message),
                      color = MaterialTheme.colorScheme.error,
                      textAlign = TextAlign.Center)
                  Spacer(modifier = Modifier.height(16.dp))
                  Button(onClick = { viewModel.resetLocal() }) {
                    Text(stringResource(R.string.try_again))
                  }
                }
          }
        }
      }

  if (showFilePicker) {
    FilePickerDialog(
        onDismiss = { showFilePicker = false },
        onFileSelected = { path ->
          showFilePicker = false
          val baseOutputDir =
              prefs.getString(
                  "output_dir",
                  File(Environment.getExternalStorageDirectory(), "payload_dumper").absolutePath)
                  ?: ""
          viewModel.loadLocalPartitions(path, baseOutputDir)
        })
  }

  if (showIncrementalDialog && currentState != null) {
    IncrementalOtaDialog(
        payloadInfo = currentState.payloadInfo,
        lastSourceDir = prefs.getString("last_source_dir", null),
        onDismiss = { showIncrementalDialog = false },
        onSelectDirectory = {
          showIncrementalDialog = false
          showSourceDirPicker = true
        },
        onProceedWithoutSource = { showIncrementalDialog = false })
  }

  if (showSourceDirPicker) {
    FilePickerDialog(
        selectDirectory = true,
        onDismiss = { showSourceDirPicker = false },
        onFileSelected = { path ->
          showSourceDirPicker = false
          viewModel.setSourceDirectory(path, isLocal = true)
          prefs.edit().putString("last_source_dir", path).apply()
        })
  }
}
