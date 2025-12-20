package com.rhythmcache.payloaddumper.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.rhythmcache.payloaddumper.BuildConfig
import com.rhythmcache.payloaddumper.FilePickerDialog
import com.rhythmcache.payloaddumper.R
import com.rhythmcache.payloaddumper.ui.screens.components.CheckingUpdateDialog
import com.rhythmcache.payloaddumper.ui.screens.components.UpdateCheckDialog
import com.rhythmcache.payloaddumper.ui.screens.components.UpdateErrorDialog
import com.rhythmcache.payloaddumper.utils.LocaleManager
import com.rhythmcache.payloaddumper.utils.UpdateChecker
import com.rhythmcache.payloaddumper.utils.UpdateInfo
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
  val context = LocalContext.current
  val prefs = context.getSharedPreferences("settings", 0)
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  var outputDir by remember {
    mutableStateOf(
        prefs.getString(
            "output_dir",
            File(Environment.getExternalStorageDirectory(), "payload_dumper").absolutePath) ?: "")
  }
  var userAgent by remember {
    mutableStateOf(prefs.getString("user_agent", BuildConfig.USER_AGENT) ?: BuildConfig.USER_AGENT)
  }
  var verifyOutput by remember { mutableStateOf(prefs.getBoolean("verify", true)) }
  var limitThreading by remember { mutableStateOf(prefs.getBoolean("limit_threading", false)) }
  var threadMode by remember {
    mutableStateOf(prefs.getString("thread_mode", "default") ?: "default")
  }
  var customThreadCount by remember {
    mutableStateOf(
        prefs.getInt("custom_thread_count", Runtime.getRuntime().availableProcessors()).toString())
  }
  var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "AUTO") ?: "AUTO") }
  var currentLanguage by remember { mutableStateOf(LocaleManager.getLanguage(context)) }

  var showDirPicker by remember { mutableStateOf(false) }
  var showThreadDialog by remember { mutableStateOf(false) }
  var showThemeDialog by remember { mutableStateOf(false) }
  var showUserAgentDialog by remember { mutableStateOf(false) }
  var showClearDataDialog by remember { mutableStateOf(false) }
  var showLanguageDialog by remember { mutableStateOf(false) }

  // Update check states
  var showUpdateCheck by remember { mutableStateOf(false) }
  var isCheckingUpdate by remember { mutableStateOf(false) }
  var updateInfo: UpdateInfo? by remember { mutableStateOf(null) }
  var updateCheckError by remember { mutableStateOf(false) }

  fun checkForUpdates() {
    isCheckingUpdate = true
    updateCheckError = false
    scope.launch {
      // Pass BuildConfig.VERSION_NAME for semantic version comparison
      val result =
          UpdateChecker.checkForUpdate(
              context = context,
              currentVersionCode = BuildConfig.VERSION_CODE,
              currentVersionName = BuildConfig.VERSION_NAME)
      isCheckingUpdate = false
      if (result != null) {
        updateInfo = result
        showUpdateCheck = true
      } else {
        updateCheckError = true
      }
    }
  }

  Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
          Text(
              stringResource(R.string.appearance),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold)

          SettingCard(
              title = stringResource(R.string.theme),
              subtitle =
                  when (themeMode) {
                    "AUTO" -> stringResource(R.string.theme_auto)
                    "LIGHT" -> stringResource(R.string.theme_light)
                    "DARK" -> stringResource(R.string.theme_dark)
                    "AMOLED" -> stringResource(R.string.theme_amoled)
                    else -> stringResource(R.string.theme_auto)
                  },
              icon = Icons.Default.Brightness4,
              onClick = { showThemeDialog = true })

          SettingCard(
              title = stringResource(R.string.language_label),
              subtitle = getLanguageName(currentLanguage, context),
              description = stringResource(R.string.language_description),
              icon = Icons.Default.Language,
              onClick = { showLanguageDialog = true })

          HorizontalDivider()
          Text(
              stringResource(R.string.storage),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold)

          SettingCard(
              title = stringResource(R.string.output_directory),
              subtitle = outputDir,
              description = stringResource(R.string.output_dir_description),
              icon = Icons.Default.FolderOpen,
              onClick = { showDirPicker = true })

          HorizontalDivider()
          Text(
              stringResource(R.string.network),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold)

          SettingCard(
              title = stringResource(R.string.user_agent),
              subtitle = userAgent,
              description = stringResource(R.string.user_agent_description),
              icon = Icons.Default.Edit,
              onClick = { showUserAgentDialog = true })

          SettingCard(
              title = stringResource(R.string.clear_browser_data),
              subtitle = stringResource(R.string.clear_browser_data_subtitle),
              icon = Icons.Default.Delete,
              onClick = { showClearDataDialog = true })

          HorizontalDivider()
          Text(
              stringResource(R.string.extraction),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold)

          SettingToggleCard(
              title = stringResource(R.string.verify_sha256),
              description = stringResource(R.string.verify_sha256_description),
              checked = verifyOutput,
              onCheckedChange = {
                verifyOutput = it
                prefs.edit().putBoolean("verify", it).apply()
              })

          Spacer(modifier = Modifier.height(4.dp))

          SettingToggleCard(
              title = stringResource(R.string.limit_concurrent),
              description = stringResource(R.string.limit_concurrent_description),
              checked = limitThreading,
              onCheckedChange = {
                limitThreading = it
                prefs.edit().putBoolean("limit_threading", it).apply()
              })

          AnimatedVisibility(visible = limitThreading) {
            SettingCard(
                title = stringResource(R.string.thread_limit),
                subtitle =
                    if (threadMode == "default")
                        stringResource(
                            R.string.thread_auto, Runtime.getRuntime().availableProcessors())
                    else
                        stringResource(
                            R.string.threads_count, customThreadCount.toIntOrNull() ?: 1),
                icon = Icons.Default.Edit,
                onClick = { showThreadDialog = true },
                containerColor = MaterialTheme.colorScheme.secondaryContainer)
          }

          HorizontalDivider()
          Text(
              stringResource(R.string.about),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold)

          SettingCard(
              title = stringResource(R.string.check_for_updates),
              subtitle = stringResource(R.string.current_version_format, BuildConfig.VERSION_NAME),
              description = stringResource(R.string.check_updates_description),
              icon = Icons.Default.SystemUpdate,
              onClick = { checkForUpdates() })

          Spacer(modifier = Modifier.weight(1f))
          HorizontalDivider()
          SettingsFooter()
        }
  }

  if (showDirPicker) {
    FilePickerDialog(
        onDismiss = { showDirPicker = false },
        onFileSelected = { path ->
          val testFile = File(path, ".test_write_${System.currentTimeMillis()}")
          try {
            testFile.createNewFile()
            testFile.delete()
            outputDir = path
            prefs.edit().putString("output_dir", path).apply()
            showDirPicker = false
          } catch (e: Exception) {}
        },
        selectDirectory = true)
  }

  if (showThreadDialog) {
    ThreadLimitDialog(
        threadMode = threadMode,
        customThreadCount = customThreadCount,
        onThreadModeChange = {
          threadMode = it
          prefs.edit().putString("thread_mode", it).apply()
        },
        onCustomThreadCountChange = { customThreadCount = it },
        onDismiss = { showThreadDialog = false },
        onConfirm = {
          val count = customThreadCount.toIntOrNull()
          if (threadMode == "custom" && count != null && count > 0) {
            prefs.edit().putInt("custom_thread_count", count).apply()
          }
          showThreadDialog = false
        })
  }

  if (showThemeDialog) {
    ThemeDialog(
        currentTheme = themeMode,
        onThemeChange = {
          themeMode = it
          prefs.edit().putString("theme_mode", it).apply()
        },
        onDismiss = { showThemeDialog = false })
  }

  if (showUserAgentDialog) {
    UserAgentDialog(
        userAgent = userAgent,
        onConfirm = {
          userAgent = it
          prefs.edit().putString("user_agent", it).apply()
          showUserAgentDialog = false
        },
        onDismiss = { showUserAgentDialog = false })
  }

  if (showLanguageDialog) {
    LanguageDialog(
        currentLanguage = currentLanguage,
        onLanguageChange = { newLang ->
          currentLanguage = newLang
          LocaleManager.setLocale(context, newLang)
          (context as? ComponentActivity)?.recreate()
        },
        onDismiss = { showLanguageDialog = false })
  }

  if (showClearDataDialog) {
    AlertDialog(
        onDismissRequest = { showClearDataDialog = false },
        icon = {
          Icon(
              Icons.Default.Delete,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error)
        },
        title = { Text(stringResource(R.string.clear_browser_data_title)) },
        text = { Text(stringResource(R.string.clear_browser_data_message)) },
        confirmButton = {
          TextButton(
              onClick = {
                try {
                  CookieManager.getInstance().removeAllCookies(null)
                  CookieManager.getInstance().flush()
                  WebStorage.getInstance().deleteAllData()
                  showClearDataDialog = false

                  scope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.browser_data_cleared),
                        duration = SnackbarDuration.Short)
                  }
                } catch (e: Exception) {
                  scope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.browser_data_clear_failed),
                        duration = SnackbarDuration.Short)
                  }
                }
              }) {
                Text(stringResource(R.string.clear), color = MaterialTheme.colorScheme.error)
              }
        },
        dismissButton = {
          TextButton(onClick = { showClearDataDialog = false }) {
            Text(stringResource(R.string.cancel))
          }
        })
  }

  // Update check dialogs
  if (isCheckingUpdate) {
    CheckingUpdateDialog(onDismiss = { isCheckingUpdate = false })
  }

  if (showUpdateCheck && updateInfo != null) {
    UpdateCheckDialog(
        onDismiss = {
          showUpdateCheck = false
          updateInfo = null
        },
        updateInfo = updateInfo)
  }

  if (updateCheckError) {
    UpdateErrorDialog(onDismiss = { updateCheckError = false }, onRetry = { checkForUpdates() })
  }
}

@Composable
private fun SettingCard(
    title: String,
    subtitle: String,
    description: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant
) {
  Card(
      modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
      colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1)
                description?.let {
                  Text(
                      it,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.padding(top = 4.dp))
                }
              }
              Icon(
                  icon,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
      }
}

@Composable
private fun SettingToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
  Card(
      modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
      }
}

@Composable
private fun SettingsFooter() {
  val context = LocalContext.current

  Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
              IconButton(
                  onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.GITHUB_URL))
                    context.startActivity(intent)
                  }) {
                    Icon(
                        painter = painterResource(R.drawable.gh),
                        contentDescription = "GitHub",
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary)
                  }

              IconButton(
                  onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.TELEGRAM_URL))
                    context.startActivity(intent)
                  }) {
                    Icon(
                        painter = painterResource(R.drawable.tg),
                        contentDescription = "Telegram",
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary)
                  }
            }

        Column(
            horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                  buildAnnotatedString {
                    append(stringResource(R.string.version_prefix))
                    withStyle(style = SpanStyle(fontWeight = FontWeight.SemiBold)) {
                      append(BuildConfig.VERSION_NAME)
                    }
                  },
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant)

              Text(
                  buildAnnotatedString {
                    withStyle(
                        style =
                            SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline)) {
                          append(stringResource(R.string.source_code))
                        }
                  },
                  style = MaterialTheme.typography.bodySmall,
                  modifier =
                      Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.REPO_URL))
                        context.startActivity(intent)
                      })
            }
      }
}

@Composable
private fun ThreadLimitDialog(
    threadMode: String,
    customThreadCount: String,
    onThreadModeChange: (String) -> Unit,
    onCustomThreadCountChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.concurrent_extraction_limit)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth().clickable { onThreadModeChange("default") }) {
                RadioButton(
                    selected = threadMode == "default", onClick = { onThreadModeChange("default") })
                Column(modifier = Modifier.padding(start = 8.dp)) {
                  Text(stringResource(R.string.thread_auto_recommended))
                  Text(
                      stringResource(
                          R.string.thread_auto_description,
                          Runtime.getRuntime().availableProcessors()),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }

          Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth().clickable { onThreadModeChange("custom") }) {
                RadioButton(
                    selected = threadMode == "custom", onClick = { onThreadModeChange("custom") })
                Text(
                    stringResource(R.string.thread_custom),
                    modifier = Modifier.padding(start = 8.dp))
              }

          if (threadMode == "custom") {
            OutlinedTextField(
                value = customThreadCount,
                onValueChange = {
                  if (it.isEmpty() || it.toIntOrNull() != null) {
                    onCustomThreadCountChange(it)
                  }
                },
                label = { Text(stringResource(R.string.thread_count_label)) },
                placeholder = { Text(stringResource(R.string.thread_count_placeholder)) },
                modifier = Modifier.fillMaxWidth().padding(start = 40.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true)
          }
        }
      },
      confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.ok)) } },
      dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun ThemeDialog(
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.choose_theme)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          ThemeOption(
              label = stringResource(R.string.theme_auto),
              description = stringResource(R.string.theme_auto_description),
              isSelected = currentTheme == "AUTO",
              onClick = { onThemeChange("AUTO") })
          ThemeOption(
              label = stringResource(R.string.theme_light),
              description = stringResource(R.string.theme_light_description),
              isSelected = currentTheme == "LIGHT",
              onClick = { onThemeChange("LIGHT") })
          ThemeOption(
              label = stringResource(R.string.theme_dark),
              description = stringResource(R.string.theme_dark_description),
              isSelected = currentTheme == "DARK",
              onClick = { onThemeChange("DARK") })
          ThemeOption(
              label = stringResource(R.string.theme_amoled),
              description = stringResource(R.string.theme_amoled_description),
              isSelected = currentTheme == "AMOLED",
              onClick = { onThemeChange("AMOLED") })
        }
      },
      confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } })
}

@Composable
private fun ThemeOption(
    label: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        RadioButton(selected = isSelected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 8.dp)) {
          Text(label, style = MaterialTheme.typography.bodyMedium)
          Text(
              description,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
}

@Composable
private fun LanguageDialog(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val languages = LocaleManager.getAvailableLanguages()

  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.choose_language)) },
      text = {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(400.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
              items(languages.size) { index ->
                val lang = languages[index]
                LanguageOption(
                    label = stringResource(lang.nameResId),
                    description =
                        if (lang.code == "system")
                            stringResource(R.string.language_system_description)
                        else "",
                    isSelected = currentLanguage == lang.code,
                    onClick = {
                      onLanguageChange(lang.code)
                      onDismiss()
                    })
              }
            }
      },
      confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } })
}

@Composable
private fun LanguageOption(
    label: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        RadioButton(selected = isSelected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 8.dp)) {
          Text(label, style = MaterialTheme.typography.bodyMedium)
          if (description.isNotEmpty()) {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
}

private fun getLanguageName(code: String, context: Context): String {
  return when (code) {
    "system" -> context.getString(R.string.language_system)
    "en" -> context.getString(R.string.language_english)
    "hi" -> context.getString(R.string.language_hindi)
    else -> context.getString(R.string.language_system)
  }
}

data class UserAgentPreset(val name: String, val value: String, val description: String)

@Composable
private fun UserAgentDialog(userAgent: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
  var tempUserAgent by remember { mutableStateOf(userAgent) }
  var showPresets by remember { mutableStateOf(true) }

  val context = LocalContext.current
  val presets = remember {
    listOf(
        UserAgentPreset(
            name = context.getString(R.string.ua_default),
            value = BuildConfig.USER_AGENT,
            description = context.getString(R.string.ua_default_description)),
        UserAgentPreset(
            name = context.getString(R.string.ua_chrome_desktop),
            value =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            description = context.getString(R.string.ua_chrome_desktop_description)),
        UserAgentPreset(
            name = context.getString(R.string.ua_chrome_mobile),
            value =
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36",
            description = context.getString(R.string.ua_chrome_mobile_description)),
        UserAgentPreset(
            name = context.getString(R.string.ua_firefox_desktop),
            value =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
            description = context.getString(R.string.ua_firefox_desktop_description)),
        UserAgentPreset(
            name = context.getString(R.string.ua_safari_ios),
            value =
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
            description = context.getString(R.string.ua_safari_ios_description)))
  }

  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(R.string.user_agent)) },
      text = {
        Column(
            modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Text(
                  stringResource(R.string.user_agent_help),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant)
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = showPresets,
                        onClick = { showPresets = true },
                        label = { Text(stringResource(R.string.presets)) },
                        leadingIcon =
                            if (showPresets) {
                              {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp))
                              }
                            } else null)
                    FilterChip(
                        selected = !showPresets,
                        onClick = { showPresets = false },
                        label = { Text(stringResource(R.string.custom)) },
                        leadingIcon =
                            if (!showPresets) {
                              {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp))
                              }
                            } else null)
                  }

              if (showPresets) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                      items(presets.size) { index ->
                        val preset = presets[index]
                        val isSelected = tempUserAgent == preset.value
                        Card(
                            modifier =
                                Modifier.fillMaxWidth().clickable { tempUserAgent = preset.value },
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant)) {
                              Row(
                                  modifier = Modifier.fillMaxWidth().padding(12.dp),
                                  verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { tempUserAgent = preset.value })
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                      Text(
                                          preset.name,
                                          style = MaterialTheme.typography.bodyMedium,
                                          fontWeight = FontWeight.Medium,
                                          color =
                                              if (isSelected)
                                                  MaterialTheme.colorScheme.onPrimaryContainer
                                              else MaterialTheme.colorScheme.onSurface)
                                      Text(
                                          preset.description,
                                          style = MaterialTheme.typography.bodySmall,
                                          color =
                                              if (isSelected)
                                                  MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                                      alpha = 0.8f)
                                              else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                  }
                            }
                      }
                    }
              } else {
                OutlinedTextField(
                    value = tempUserAgent,
                    onValueChange = { tempUserAgent = it },
                    label = { Text(stringResource(R.string.custom_user_agent)) },
                    placeholder = { Text(stringResource(R.string.custom_user_agent_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6)
              }
            }
      },
      confirmButton = {
        TextButton(onClick = { onConfirm(tempUserAgent) }) { Text(stringResource(R.string.save)) }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
