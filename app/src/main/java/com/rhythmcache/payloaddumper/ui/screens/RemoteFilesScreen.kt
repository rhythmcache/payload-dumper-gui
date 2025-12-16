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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rhythmcache.payloaddumper.*
import com.rhythmcache.payloaddumper.state.UiState
import com.rhythmcache.payloaddumper.ui.screens.components.PartitionsView
import com.rhythmcache.payloaddumper.ui.screens.components.PermissionCard
import com.rhythmcache.payloaddumper.viewmodel.PayloadViewModel
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class FileDetectionResult {
  data class Success(val type: SourceType) : FileDetectionResult()

  data class Error(val message: String) : FileDetectionResult()
}

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

  suspend fun detectFileType(url: String): FileDetectionResult =
      withContext(Dispatchers.IO) {
        try {
          val connection = URL(url).openConnection() as HttpURLConnection
          connection.requestMethod = "GET"
          connection.setRequestProperty("Range", "bytes=0-3")
          connection.connectTimeout = 5000
          connection.readTimeout = 5000

          val userAgent =
              prefs.getString("user_agent", BuildConfig.USER_AGENT) ?: BuildConfig.USER_AGENT
          connection.setRequestProperty("User-Agent", userAgent)
          if (sessionCookie.isNotEmpty()) {
            connection.setRequestProperty("Cookie", sessionCookie)
          }

          connection.connect()
          val signature = connection.inputStream.readBytes()
          connection.disconnect()

          if (signature.size < 4) {
            return@withContext FileDetectionResult.Error("Unable to read file signature")
          }

          if (signature[0] == 0x50.toByte() && signature[1] == 0x4B.toByte()) {
            return@withContext FileDetectionResult.Success(SourceType.REMOTE_ZIP)
          }
          if (signature[0] == 0x43.toByte() && // 'C'
              signature[1] == 0x72.toByte() && // 'r'
              signature[2] == 0x41.toByte() && // 'A'
              signature[3] == 0x55.toByte()) { // 'U'
            return@withContext FileDetectionResult.Success(SourceType.REMOTE_BIN)
          }
          return@withContext FileDetectionResult.Error(
              "Unrecognized file format. Expected ZIP (PK) or Payload (CrAU) signature.")
        } catch (e: Exception) {
          if (url.endsWith(".zip", ignoreCase = true)) {
            return@withContext FileDetectionResult.Success(SourceType.REMOTE_ZIP)
          } else if (url.endsWith(".bin", ignoreCase = true)) {
            return@withContext FileDetectionResult.Success(SourceType.REMOTE_BIN)
          } else {
            return@withContext FileDetectionResult.Error("Unable to detect file type: ${e.message}")
          }
        }
      }

  fun handleLoadPartitions() {
    if (!inputUrl.startsWith("http")) return

    scope.launch {
      isValidating = true

      val isDownload = isDownloadableFile(inputUrl)

      if (!isDownload) {
        isValidating = false
        browserStartUrl = inputUrl
        showBrowser = true
        return@launch
      }

      when (val result = detectFileType(inputUrl)) {
        is FileDetectionResult.Success -> {
          isValidating = false
          val userAgent =
              prefs.getString("user_agent", BuildConfig.USER_AGENT) ?: BuildConfig.USER_AGENT
          val baseOutputDir =
              prefs.getString(
                  "output_dir",
                  File(Environment.getExternalStorageDirectory(), "payload_dumper").absolutePath)
                  ?: ""

          val cookieToUse = sessionCookie.ifEmpty { null }
          viewModel.loadRemotePartitions(
              inputUrl, result.type, userAgent, baseOutputDir, cookieToUse)
        }
        is FileDetectionResult.Error -> {
          isValidating = false
          viewModel.setRemoteError(result.message)
        }
      }
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
                                            contentDescription = "Open Browser",
                                            tint = MaterialTheme.colorScheme.primary)
                                      }

                                  OutlinedTextField(
                                      value = inputUrl,
                                      onValueChange = { inputUrl = it },
                                      label = { Text("File URL") },
                                      placeholder = { Text("https://example.com/payload.zip") },
                                      modifier = Modifier.weight(1f),
                                      singleLine = true,
                                      shape = RoundedCornerShape(12.dp))

                                  IconButton(
                                      onClick = { showCookieDialog = true },
                                      modifier = Modifier.size(48.dp)) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = "Cookie Settings",
                                            tint =
                                                if (sessionCookie.isNotEmpty())
                                                    MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant)
                                      }
                                }

                            if (sessionCookie.isNotEmpty()) {
                              Text(
                                  "Cookie configured for this session",
                                  style = MaterialTheme.typography.bodySmall,
                                  color = MaterialTheme.colorScheme.primary,
                                  modifier = Modifier.padding(top = 4.dp))
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { handleLoadPartitions() },
                                enabled =
                                    hasPermission && inputUrl.startsWith("http") && !isValidating,
                                modifier = Modifier.fillMaxWidth().height(56.dp)) {
                                  if (isValidating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Validating URL...")
                                  } else {
                                    Text("Load Partitions")
                                  }
                                }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                "URL could be a direct download or a webpage",
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
                                "Loading partitions...",
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
                              "Error: ${state.message}",
                              color = MaterialTheme.colorScheme.error,
                              textAlign = TextAlign.Center)
                          Spacer(modifier = Modifier.height(16.dp))
                          Button(
                              onClick = {
                                viewModel.resetRemote()
                                inputUrl = ""
                              }) {
                                Text("Try Again")
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
                                    contentDescription = "Back to Remote Files",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
                              }
                              Column {
                                Text(
                                    "Browser",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text(
                                    "Find and download payload files",
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
      errorMessage = "Invalid format. Please paste Cookie header value, not Set-Cookie header."
      showError = true
      return false
    }

    if (cookie.contains("HTTP/", ignoreCase = true) ||
        cookie.contains("\n") ||
        cookie.contains("\r")) {
      errorMessage = "Invalid format. Please paste only the cookie values (key=value; key2=value2)."
      showError = true
      return false
    }

    showError = false
    return true
  }

  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("Session Cookie") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
              "Enter cookie value for authenticated requests (Cookie header format only)",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant)

          OutlinedTextField(
              value = tempCookie,
              onValueChange = {
                tempCookie = it
                showError = false
              },
              label = { Text("Cookie") },
              placeholder = { Text("key=value; key2=value2") },
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
                          "How to get cookies:",
                          style = MaterialTheme.typography.bodySmall,
                          fontWeight = FontWeight.SemiBold)
                      Text(
                          "1. Open browser DevTools (F12)\n2. Go to Network tab\n3. Click any request\n4. Copy 'Cookie' header value",
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
                      "Persist cookie",
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.Medium)
                  Text(
                      if (persistCookie) "Saved across app restarts"
                      else "Cleared when loading different payload",
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
              Text("Save")
            }
      },
      dismissButton = {
        Row {
          if (currentCookie.isNotEmpty()) {
            TextButton(onClick = onClear) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            Spacer(modifier = Modifier.width(8.dp))
          }
          TextButton(onClick = onDismiss) { Text("Cancel") }
        }
      })
}
