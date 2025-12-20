package com.rhythmcache.payloaddumper.ui.screens.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rhythmcache.payloaddumper.BuildConfig
import com.rhythmcache.payloaddumper.R
import com.rhythmcache.payloaddumper.utils.UpdateChecker
import com.rhythmcache.payloaddumper.utils.UpdateInfo

@Composable
fun UpdateCheckDialog(onDismiss: () -> Unit, updateInfo: UpdateInfo?) {
  val context = LocalContext.current

  AlertDialog(
      onDismissRequest = onDismiss,
      icon = {
        Icon(
            if (updateInfo?.isUpdateAvailable == true) Icons.Default.SystemUpdate
            else Icons.Default.CheckCircle,
            contentDescription = null,
            tint =
                if (updateInfo?.isUpdateAvailable == true) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.tertiary)
      },
      title = {
        Text(
            if (updateInfo?.isUpdateAvailable == true) stringResource(R.string.update_available)
            else stringResource(R.string.up_to_date))
      },
      text = {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
              if (updateInfo?.isUpdateAvailable == true) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                      Text(
                          "v${updateInfo.versionName}",
                          style = MaterialTheme.typography.titleLarge,
                          fontWeight = FontWeight.Bold,
                          color = MaterialTheme.colorScheme.primary)
                      Text(
                          stringResource(R.string.current_version_label, BuildConfig.VERSION_NAME),
                          style = MaterialTheme.typography.bodyMedium,
                          color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                if (updateInfo.changelog.isNotEmpty()) {
                  HorizontalDivider()
                  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.whats_new),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        updateInfo.changelog,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
              } else {
                Text(
                    stringResource(R.string.running_latest_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium)
              }
            }
      },
      confirmButton = {
        if (updateInfo?.isUpdateAvailable == true) {
          Button(
              onClick = {
                UpdateChecker.openDownloadPage(context, updateInfo.downloadUrl)
                onDismiss()
              }) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.download))
              }
        } else {
          TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        }
      },
      dismissButton = {
        if (updateInfo?.isUpdateAvailable == true) {
          TextButton(onClick = onDismiss) { Text(stringResource(R.string.later)) }
        }
      })
}

@Composable
fun CheckingUpdateDialog(onDismiss: () -> Unit) {
  AlertDialog(
      onDismissRequest = onDismiss,
      icon = { CircularProgressIndicator(modifier = Modifier.size(32.dp)) },
      title = { Text(stringResource(R.string.checking_for_updates)) },
      text = {
        Text(stringResource(R.string.please_wait), style = MaterialTheme.typography.bodyMedium)
      },
      confirmButton = {},
      dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun UpdateErrorDialog(onDismiss: () -> Unit, onRetry: () -> Unit) {
  val context = LocalContext.current

  AlertDialog(
      onDismissRequest = onDismiss,
      icon = {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error)
      },
      title = { Text(stringResource(R.string.update_check_failed)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
              stringResource(R.string.update_check_error_message),
              style = MaterialTheme.typography.bodyMedium)
          Text(
              stringResource(R.string.check_internet_connection),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      },
      confirmButton = { TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) } },
      dismissButton = {
        TextButton(
            onClick = {
              UpdateChecker.openReleasesPage(context, BuildConfig.REPO_URL)
              onDismiss()
            }) {
              Text(stringResource(R.string.view_releases))
            }
      })
}
