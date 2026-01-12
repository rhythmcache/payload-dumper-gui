package com.rhythmcache.payloaddumper.ui.screens

import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rhythmcache.payloaddumper.*
import com.rhythmcache.payloaddumper.FilePickerDialog
import com.rhythmcache.payloaddumper.R
import com.rhythmcache.payloaddumper.state.UiState
import com.rhythmcache.payloaddumper.ui.screens.components.IncrementalOtaDialog
import com.rhythmcache.payloaddumper.ui.screens.components.PartitionsView
import com.rhythmcache.payloaddumper.ui.screens.components.PermissionCard
import com.rhythmcache.payloaddumper.viewmodel.PayloadViewModel
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RemoteFilesScreen(viewModel: PayloadViewModel, hasPermission: Boolean) {
  val context = LocalContext.current
  val uiState by viewModel.remoteUiState.collectAsState()
  val prefs = context.getSharedPreferences("settings", 0)
  val scope = rememberCoroutineScope()

  var inputUrl by remember { mutableStateOf("") }
  var isValidating by remember { mutableStateOf(false) }
  var showBrowser by remember { mutableStateOf(false) }
  var browserStartUrl by remember { mutableStateOf<String?>(null) }
  var urlFromBrowser by remember { mutableStateOf(false) }

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

  var sessionCookie by remember {
    mutableStateOf(
        if (prefs.getBoolean("persist_cookie", false)) {
          prefs.getString("session_cookie", "") ?: ""
        } else {
          ""
        })
  }

  var showCookieDialog by remember { mutableStateOf(false) }

  val isShowingPartitions = uiState is UiState.PartitionsLoaded

  suspend fun isDownloadableFile(url: String): Boolean =
      withContext(Dispatchers.IO) {
        try {
          val connection = URL(url).openConnection() as HttpURLConnection

          var success = false
          var contentType = ""
          var contentDisposition = ""

          try {
            connection.requestMethod = "HEAD"
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val userAgent =
                prefs.getString("user_agent", BuildConfig.USER_AGENT) ?: BuildConfig.USER_AGENT
            connection.setRequestProperty("User-Agent", userAgent)

            if (sessionCookie.isNotEmpty()) {
              connection.setRequestProperty("Cookie", sessionCookie)
            }

            connection.connect()

            contentType = connection.contentType?.lowercase() ?: ""
            contentDisposition = connection.getHeaderField("Content-Disposition")?.lowercase() ?: ""
            success = true
          } catch (e: Exception) {

            connection.disconnect()

            val rangeConnection = URL(url).openConnection() as HttpURLConnection
            rangeConnection.requestMethod = "GET"
            rangeConnection.setRequestProperty("Range", "bytes=0-0")
            rangeConnection.instanceFollowRedirects = true
            rangeConnection.connectTimeout = 5000
            rangeConnection.readTimeout = 5000

            val userAgent =
                prefs.getString("user_agent", BuildConfig.USER_AGENT) ?: BuildConfig.USER_AGENT
            rangeConnection.setRequestProperty("User-Agent", userAgent)

            if (sessionCookie.isNotEmpty()) {
              rangeConnection.setRequestProperty("Cookie", sessionCookie)
            }

            rangeConnection.connect()
            contentType = rangeConnection.contentType?.lowercase() ?: ""
            contentDisposition =
                rangeConnection.getHeaderField("Content-Disposition")?.lowercase() ?: ""
            rangeConnection.disconnect()
            success = true
          }

          connection.disconnect()

          if (!success) return@withContext false

          val isDownloadable =
              url.endsWith(".zip", ignoreCase = true) ||
                  url.endsWith(".bin", ignoreCase = true) ||
                  contentType.contains("application/zip") ||
                  contentType.contains("application/octet-stream") ||
                  contentType.contains("application/x-zip") ||
                  contentDisposition.contains("attachment")

          isDownloadable
        } catch (e: Exception) {
          false
        }
      }

  fun handleLoadPartitions() {
    if (!inputUrl.startsWith("http")) return

    scope.launch {
      isValidating = true

      if (!urlFromBrowser) {
        val isDownload = isDownloadableFile(inputUrl)
        if (!isDownload) {
          isValidating = false
          browserStartUrl = inputUrl
          showBrowser = true
          return@launch
        }
      }

      urlFromBrowser = false
      isValidating = false

      val userAgent =
          prefs.getString("user_agent", BuildConfig.USER_AGENT) ?: BuildConfig.USER_AGENT
      val baseOutputDir =
          prefs.getString(
              "output_dir",
              File(Environment.getExternalStorageDirectory(), "payload_dumper").absolutePath) ?: ""

      val cookieToUse = sessionCookie.ifEmpty { null }
      viewModel.loadRemotePartitions(inputUrl, userAgent, baseOutputDir, cookieToUse)
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    AnimatedVisibility(
        visible = !showBrowser,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it })) {
          Column(
              modifier = Modifier.fillMaxSize().padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!hasPermission) {
                  PermissionCard()
                }

                when (val state = uiState) {
                  is UiState.Idle -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                      Column(
                          horizontalAlignment = Alignment.CenterHorizontally,
                          modifier = Modifier.fillMaxWidth(0.9f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                  IconButton(
                                      onClick = { showBrowser = true },
                                      modifier = Modifier.size(48.dp)) {
                                        Icon(
                                            Icons.Default.Language,
                                            contentDescription =
                                                stringResource(R.string.open_browser),
                                            tint = MaterialTheme.colorScheme.primary)
                                      }

                                  OutlinedTextField(
                                      value = inputUrl,
                                      onValueChange = {
                                        inputUrl = it

                                        urlFromBrowser = false
                                      },
                                      label = { Text(stringResource(R.string.file_url)) },
                                      placeholder = {
                                        Text(stringResource(R.string.file_url_placeholder))
                                      },
                                      modifier = Modifier.weight(1f),
                                      singleLine = true,
                                      shape = RoundedCornerShape(12.dp))

                                  IconButton(
                                      onClick = { showCookieDialog = true },
                                      modifier = Modifier.size(48.dp)) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription =
                                                stringResource(R.string.cookie_settings),
                                            tint =
                                                if (sessionCookie.isNotEmpty())
                                                    MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant)
                                      }
                                }

                            if (sessionCookie.isNotEmpty()) {
                              Text(
                                  stringResource(R.string.cookie_configured),
                                  style = MaterialTheme.typography.bodySmall,
                                  color = MaterialTheme.colorScheme.primary,
                                  modifier = Modifier.padding(top = 4.dp))
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { handleLoadPartitions() },
                                enabled =
                                    hasPermission && inputUrl.startsWith("http") && !isValidating,
                                modifier = Modifier.fillMaxWidth(0.7f).height(56.dp)) {
                                  if (isValidating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.validating_url))
                                  } else {
                                    Text(stringResource(R.string.load_partitions))
                                  }
                                }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                stringResource(R.string.url_help_text),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center)
                          }
                    }
                  }
                  is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                      Column(
                          horizontalAlignment = Alignment.CenterHorizontally,
                          verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CircularProgressIndicator()
                            Text(
                                stringResource(R.string.loading_partitions),
                                style = MaterialTheme.typography.bodyMedium)
                          }
                    }
                  }
                  is UiState.PartitionsLoaded -> {
                    PartitionsView(
                        state = state,
                        viewModel = viewModel,
                        isLocal = false,
                        onLoadDifferent = {
                          viewModel.resetRemote()
                          inputUrl = ""
                          urlFromBrowser = false

                          val shouldPersist = prefs.getBoolean("persist_cookie", false)
                          if (!shouldPersist) {
                            sessionCookie = ""
                          } else {
                            sessionCookie = prefs.getString("session_cookie", "") ?: ""
                          }
                        })
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
                          Button(
                              onClick = {
                                viewModel.resetRemote()
                                inputUrl = ""
                                urlFromBrowser = false
                              }) {
                                Text(stringResource(R.string.try_again))
                              }
                        }
                  }
                }
              }
        }

    AnimatedVisibility(
        visible = showBrowser,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it })) {
          Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 2.dp) {
                  Row(
                      modifier =
                          Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                              IconButton(onClick = { showBrowser = false }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back_to_remote),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
                              }
                              Column {
                                Text(
                                    stringResource(R.string.browser),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text(
                                    stringResource(R.string.find_download_payload),
                                    style = MaterialTheme.typography.bodySmall,
                                    color =
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                            alpha = 0.7f))
                              }
                            }
                      }
                }

            BrowserScreen(
                initialUrl = browserStartUrl,
                onDownloadCaptured = { url, cookie ->
                  inputUrl = url
                  urlFromBrowser = true

                  if (cookie != null && cookie.isNotEmpty()) {
                    sessionCookie = cookie
                    if (prefs.getBoolean("persist_cookie", false)) {
                      prefs.edit().putString("session_cookie", cookie).apply()
                    }
                  }
                  showBrowser = false
                  browserStartUrl = null
                })
          }
        }
  }

  if (showCookieDialog) {
    CookieDialog(
        currentCookie = sessionCookie,
        onDismiss = { showCookieDialog = false },
        onSave = { newCookie, shouldPersist ->
          sessionCookie = newCookie
          prefs.edit().putBoolean("persist_cookie", shouldPersist).apply()

          if (shouldPersist && newCookie.isNotEmpty()) {

            prefs.edit().putString("session_cookie", newCookie).apply()
          } else if (!shouldPersist) {

            prefs.edit().remove("session_cookie").apply()
          } else if (newCookie.isEmpty()) {

            prefs.edit().remove("session_cookie").apply()
          }

          showCookieDialog = false
        },
        onClear = {
          sessionCookie = ""
          prefs.edit().remove("session_cookie").apply()
          prefs.edit().putBoolean("persist_cookie", false).apply()
          showCookieDialog = false
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
          viewModel.setSourceDirectory(path, isLocal = false)
          prefs.edit().putString("last_source_dir", path).apply()
        })
  }
}

@Composable
private fun CookieDialog(
    currentCookie: String,
    onDismiss: () -> Unit,
    onSave: (cookie: String, persist: Boolean) -> Unit,
    onClear: () -> Unit
) {
  var tempCookie by remember { mutableStateOf(currentCookie) }
  val context = LocalContext.current
  val prefs = context.getSharedPreferences("settings", 0)
  var persistCookie by remember { mutableStateOf(prefs.getBoolean("persist_cookie", false)) }
  var showError by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf("") }

  fun validateCookie(cookie: String): Boolean {
    if (cookie.isEmpty()) return true

    if (cookie.contains("Set-Cookie", ignoreCase = true)) {
      errorMessage = context.resources.getString(R.string.cookie_error_set_cookie)
      showError = true
      return false
    }

    if (cookie.contains("HTTP/", ignoreCase = true) ||
        cookie.contains("\n") ||
        cookie.contains("\r")) {
      errorMessage = context.resources.getString(R.string.cookie_error_invalid)
      showError = true
      return false
    }

    showError = false
    return true
  }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.session_cookie)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
              stringResource(R.string.cookie_help_text),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant)

          OutlinedTextField(
              value = tempCookie,
              onValueChange = {
                tempCookie = it
                showError = false
              },
              label = { Text(stringResource(R.string.cookie)) },
              placeholder = { Text(stringResource(R.string.cookie_placeholder)) },
              modifier = Modifier.fillMaxWidth(),
              minLines = 3,
              maxLines = 6,
              isError = showError,
              supportingText =
                  if (showError) {
                    { Text(errorMessage, color = MaterialTheme.colorScheme.error) }
                  } else null)

          Card(
              colors =
                  CardDefaults.cardColors(
                      containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                      Text(
                          stringResource(R.string.how_to_get_cookies),
                          style = MaterialTheme.typography.bodySmall,
                          fontWeight = FontWeight.SemiBold)
                      Text(
                          stringResource(R.string.cookie_instructions),
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
              }

          HorizontalDivider()

          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                      stringResource(R.string.persist_cookie),
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.Medium)
                  Text(
                      if (persistCookie) stringResource(R.string.persist_cookie_on)
                      else stringResource(R.string.persist_cookie_off),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Checkbox(checked = persistCookie, onCheckedChange = { persistCookie = it })
              }
        }
      },
      confirmButton = {
        TextButton(
            onClick = {
              if (validateCookie(tempCookie)) {
                onSave(tempCookie, persistCookie)
              }
            }) {
              Text(stringResource(R.string.save))
            }
      },
      dismissButton = {
        Row {
          if (currentCookie.isNotEmpty()) {
            TextButton(onClick = onClear) {
              Text(stringResource(R.string.clear), color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.width(8.dp))
          }
          TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
      })
}
