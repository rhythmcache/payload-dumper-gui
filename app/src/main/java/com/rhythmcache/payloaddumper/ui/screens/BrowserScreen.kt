package com.rhythmcache.payloaddumper.ui.screens

import android.annotation.SuppressLint
import android.webkit.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rhythmcache.payloaddumper.BuildConfig
import kotlinx.coroutines.launch

data class HistoryItem(val url: String, val title: String, val timestamp: Long)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(initialUrl: String? = null, onDownloadCaptured: (String, String?) -> Unit) {
  val context = LocalContext.current
  val prefs = context.getSharedPreferences("settings", 0)
  val focusManager = LocalFocusManager.current
  val urlFocusRequester = remember { FocusRequester() }
  val scope = rememberCoroutineScope()

  val startUrl = initialUrl ?: "https://www.google.com"
  var currentUrl by remember { mutableStateOf(startUrl) }
  var urlInput by remember { mutableStateOf(TextFieldValue(startUrl)) }
  var canGoBack by remember { mutableStateOf(false) }
  var canGoForward by remember { mutableStateOf(false) }
  var isLoading by remember { mutableStateOf(false) }
  var progress by remember { mutableStateOf(0) }
  var showDownloadDialog by remember { mutableStateOf(false) }
  var capturedUrl by remember { mutableStateOf("") }
  var capturedCookie by remember { mutableStateOf<String?>(null) }
  var isUrlFocused by remember { mutableStateOf(false) }
  var showHistory by remember { mutableStateOf(false) }
  val webViewRef = remember { mutableListOf<WebView?>(null) }
  var browserHistory by remember { mutableStateOf(loadBrowserHistory(prefs)) }
  DisposableEffect(Unit) {
    onDispose {
      webViewRef[0]?.destroy()
      webViewRef[0] = null
    }
  }
  LaunchedEffect(initialUrl) {
    initialUrl?.let { url ->
      currentUrl = url
      urlInput = TextFieldValue(url)
      webViewRef[0]?.loadUrl(url)
    }
  }

  fun saveBrowserHistory(history: List<HistoryItem>) {
    val json =
        history.take(50).joinToString("|||") { item ->
          "${item.url}::${item.title}::${item.timestamp}"
        }
    prefs.edit().putString("browser_history", json).apply()
  }

  fun addToHistory(url: String, title: String) {
    if (url.startsWith("https://www.google.com/search") || title.isBlank()) return

    val newItem = HistoryItem(url, title, System.currentTimeMillis())
    val updatedHistory = (listOf(newItem) + browserHistory.filterNot { it.url == url }).take(50)
    browserHistory = updatedHistory
    saveBrowserHistory(updatedHistory)
  }

  fun clearHistory() {
    browserHistory = emptyList()
    prefs.edit().remove("browser_history").apply()
  }

  fun processUrl(input: String): String {
    val trimmed = input.trim()
    return if (trimmed.contains(".") && !trimmed.contains(" ")) {
      if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
        "https://$trimmed"
      } else {
        trimmed
      }
    } else {
      "https://www.google.com/search?q=${java.net.URLEncoder.encode(trimmed, "UTF-8")}"
    }
  }

  fun loadUrl(url: String) {
    val processedUrl = processUrl(url)
    currentUrl = processedUrl
    webViewRef[0]?.loadUrl(processedUrl)
    focusManager.clearFocus()
  }

  Column(modifier = Modifier.fillMaxSize()) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(0.dp)) {
          Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                  IconButton(onClick = { webViewRef[0]?.goBack() }, enabled = canGoBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                  }

                  IconButton(onClick = { webViewRef[0]?.goForward() }, enabled = canGoForward) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward")
                  }

                  IconButton(onClick = { webViewRef[0]?.reload() }) {
                    Icon(Icons.Default.Refresh, "Reload")
                  }

                  IconButton(
                      onClick = {
                        currentUrl = "https://www.google.com"
                        urlInput = TextFieldValue(currentUrl)
                        webViewRef[0]?.loadUrl(currentUrl)
                      }) {
                        Icon(Icons.Default.Home, "Home")
                      }

                  IconButton(
                      onClick = { showHistory = true }, enabled = browserHistory.isNotEmpty()) {
                        Badge(
                            containerColor =
                                if (browserHistory.isNotEmpty()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant) {
                              Icon(
                                  Icons.Default.History, "History", modifier = Modifier.size(20.dp))
                            }
                      }

                  Spacer(modifier = Modifier.weight(1f))

                  if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                  }
                }

            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
              OutlinedTextField(
                  value = urlInput,
                  onValueChange = { urlInput = it },
                  modifier =
                      Modifier.fillMaxWidth().focusRequester(urlFocusRequester).onFocusChanged {
                          focusState ->
                        if (focusState.isFocused && !isUrlFocused) {
                          isUrlFocused = true
                          urlInput = urlInput.copy(selection = TextRange(0, urlInput.text.length))
                        } else if (!focusState.isFocused) {
                          isUrlFocused = false
                        }
                      },
                  placeholder = { Text("Search or enter URL") },
                  singleLine = true,
                  shape = RoundedCornerShape(24.dp),
                  colors =
                      OutlinedTextFieldDefaults.colors(
                          focusedContainerColor = MaterialTheme.colorScheme.surface,
                          unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                          focusedBorderColor = MaterialTheme.colorScheme.primary,
                          unfocusedBorderColor =
                              MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                  keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                  keyboardActions = KeyboardActions(onGo = { loadUrl(urlInput.text) }),
                  leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                  },
                  trailingIcon = {
                    Row {
                      if (urlInput.text.isNotEmpty()) {
                        IconButton(onClick = { urlInput = TextFieldValue("") }) {
                          Icon(
                              Icons.Default.Close,
                              "Clear",
                              modifier = Modifier.size(18.dp),
                              tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                      }
                      IconButton(onClick = { loadUrl(urlInput.text) }) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Go")
                      }
                    }
                  })
            }
            if (isLoading && progress > 0 && progress < 100) {
              LinearProgressIndicator(
                  progress = { progress / 100f },
                  modifier = Modifier.fillMaxWidth(),
                  color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
          WebView(ctx).apply {
            webViewRef[0] = this

            val userAgent =
                prefs.getString("user_agent", BuildConfig.USER_AGENT) ?: BuildConfig.USER_AGENT

            settings.apply {
              javaScriptEnabled = true
              domStorageEnabled = true
              setSupportZoom(true)
              builtInZoomControls = true
              displayZoomControls = false
              loadWithOverviewMode = true
              useWideViewPort = true
              userAgentString = userAgent
            }

            webViewClient =
                object : WebViewClient() {
                  override fun onPageStarted(
                      view: WebView?,
                      url: String?,
                      favicon: android.graphics.Bitmap?
                  ) {
                    isLoading = true
                    progress = 0
                    url?.let {
                      currentUrl = it
                      urlInput = TextFieldValue(it)
                    }
                  }

                  override fun onPageFinished(view: WebView?, url: String?) {
                    isLoading = false
                    progress = 100
                    canGoBack = view?.canGoBack() ?: false
                    canGoForward = view?.canGoForward() ?: false

                    view?.title?.let { title ->
                      url?.let { u -> scope.launch { addToHistory(u, title) } }
                    }
                  }
                }

            webChromeClient =
                object : WebChromeClient() {
                  override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress
                  }
                }

            setDownloadListener {
                downloadUrl,
                userAgent,
                contentDisposition,
                mimetype,
                contentLength ->
              val isPayloadFile =
                  downloadUrl.endsWith(".zip", ignoreCase = true) ||
                      downloadUrl.endsWith(".bin", ignoreCase = true) ||
                      mimetype.contains("zip", ignoreCase = true) ||
                      mimetype.contains("application/octet-stream", ignoreCase = true)

              if (isPayloadFile) {
                capturedUrl = downloadUrl
                val pageUrl = this.url
                val cookieManager = CookieManager.getInstance()
                capturedCookie = cookieManager.getCookie(pageUrl ?: downloadUrl)

                showDownloadDialog = true
              }
            }

            loadUrl(currentUrl)
          }
        })
  }

  if (showDownloadDialog) {
    AlertDialog(
        onDismissRequest = { showDownloadDialog = false },
        icon = {
          Icon(
              Icons.Default.Download,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary)
        },
        title = { Text("Download Detected") },
        text = {
          Column {
            Text("Payload file detected:")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                capturedUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary)
            if (capturedCookie != null) {
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                  "Authentication cookies will be transferred",
                  style = MaterialTheme.typography.bodySmall)
            }
          }
        },
        confirmButton = {
          TextButton(
              onClick = {
                onDownloadCaptured(capturedUrl, capturedCookie)
                showDownloadDialog = false
              }) {
                Text("Open in Dumper")
              }
        },
        dismissButton = { TextButton(onClick = { showDownloadDialog = false }) { Text("Cancel") } })
  }

  if (showHistory) {
    AlertDialog(
        onDismissRequest = { showHistory = false },
        icon = { Icon(Icons.Default.History, contentDescription = null) },
        title = { Text("Browsing History") },
        text = {
          Column(modifier = Modifier.fillMaxWidth()) {
            if (browserHistory.isEmpty()) {
              Text(
                  "No browsing history yet",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
              LazyColumn(
                  modifier = Modifier.fillMaxWidth().height(400.dp),
                  verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(browserHistory) { item ->
                      Card(
                          modifier =
                              Modifier.fillMaxWidth().clickable {
                                currentUrl = item.url
                                urlInput = TextFieldValue(item.url)
                                webViewRef[0]?.loadUrl(item.url)
                                showHistory = false
                              },
                          colors =
                              CardDefaults.cardColors(
                                  containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                              Text(
                                  item.title,
                                  style = MaterialTheme.typography.bodyMedium,
                                  maxLines = 1,
                                  overflow = TextOverflow.Ellipsis)
                              Text(
                                  item.url,
                                  style = MaterialTheme.typography.bodySmall,
                                  color = MaterialTheme.colorScheme.primary,
                                  maxLines = 1,
                                  overflow = TextOverflow.Ellipsis)
                            }
                          }
                    }
                  }
            }
          }
        },
        confirmButton = { TextButton(onClick = { showHistory = false }) { Text("Close") } },
        dismissButton = {
          if (browserHistory.isNotEmpty()) {
            TextButton(
                onClick = {
                  clearHistory()
                  showHistory = false
                }) {
                  Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
          }
        })
  }
}

private fun loadBrowserHistory(prefs: android.content.SharedPreferences): List<HistoryItem> {
  val json = prefs.getString("browser_history", "") ?: ""
  if (json.isEmpty()) return emptyList()

  return try {
    json.split("|||").mapNotNull { itemStr ->
      val parts = itemStr.split("::")
      if (parts.size == 3) {
        HistoryItem(url = parts[0], title = parts[1], timestamp = parts[2].toLongOrNull() ?: 0L)
      } else null
    }
  } catch (e: Exception) {
    emptyList()
  }
}
