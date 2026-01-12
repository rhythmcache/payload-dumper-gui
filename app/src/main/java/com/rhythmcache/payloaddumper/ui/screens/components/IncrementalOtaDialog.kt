package com.rhythmcache.payloaddumper.ui.screens.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rhythmcache.payloaddumper.PayloadInfo
import com.rhythmcache.payloaddumper.R

@Composable
fun IncrementalOtaDialog(
    payloadInfo: PayloadInfo,
    lastSourceDir: String?,
    onDismiss: () -> Unit,
    onSelectDirectory: () -> Unit,
    onProceedWithoutSource: () -> Unit
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      icon = {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary)
      },
      title = { Text(stringResource(R.string.incremental_update_detected)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Card(
              colors =
                  CardDefaults.cardColors(
                      containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                      Icon(
                          Icons.Default.Warning,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.onErrorContainer)
                      Text(
                          stringResource(R.string.experimental_feature_warning),
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onErrorContainer)
                    }
              }
          Text(
              stringResource(R.string.incremental_ota_explanation),
              style = MaterialTheme.typography.bodyMedium)
          Card(
              colors =
                  CardDefaults.cardColors(
                      containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(modifier = Modifier.padding(12.dp)) {
                  Text(
                      stringResource(R.string.requires_source_images),
                      style = MaterialTheme.typography.bodySmall,
                      fontWeight = FontWeight.SemiBold,
                      color = MaterialTheme.colorScheme.onSecondaryContainer)
                  Spacer(modifier = Modifier.height(4.dp))
                  payloadInfo.partitions
                      .filter { it.is_differential }
                      .take(5)
                      .forEach { partition ->
                        Text(
                            "• ${partition.name}.img",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                      }
                  if (payloadInfo.partitions.count { it.is_differential } > 5) {
                    Text(
                        stringResource(R.string.and_more),
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                  }
                }
              }
          if (lastSourceDir != null) {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))) {
                  Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.last_used_directory),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        lastSourceDir,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                  }
                }
          }
        }
      },
      confirmButton = {
        Button(onClick = onSelectDirectory) {
          Text(stringResource(R.string.select_source_directory))
        }
      },
      dismissButton = {
        Column(horizontalAlignment = Alignment.End) {
          TextButton(onClick = onProceedWithoutSource) {
            Text(stringResource(R.string.try_without_source))
          }
          Text(
              stringResource(R.string.extraction_may_fail),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.padding(end = 8.dp))
        }
      })
}
