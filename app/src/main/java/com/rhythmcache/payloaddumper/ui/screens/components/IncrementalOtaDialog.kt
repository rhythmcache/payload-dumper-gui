package com.rhythmcache.payloaddumper.ui.screens.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
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
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error)
      },
      title = { Text(stringResource(R.string.incremental_update_detected)) },
      text = {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.verticalScroll(rememberScrollState())) {
              Text(
                  stringResource(R.string.experimental_feature_warning),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.error)

              Text(
                  stringResource(R.string.incremental_ota_explanation),
                  style = MaterialTheme.typography.bodyMedium)

              Text(
                  stringResource(R.string.requires_source_images),
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.SemiBold)

              payloadInfo.partitions
                  .asSequence()
                  .filter { it.is_differential }
                  .take(5)
                  .forEach { partition ->
                    Text("- ${partition.name}.img", style = MaterialTheme.typography.bodySmall)
                  }

              if (payloadInfo.partitions.count { it.is_differential } > 5) {
                Text(
                    stringResource(R.string.and_more),
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic)
              }

              if (!lastSourceDir.isNullOrBlank()) {
                Text(
                    stringResource(R.string.last_used_directory),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold)
                Text(lastSourceDir, style = MaterialTheme.typography.bodySmall)
              }

              Text(
                  stringResource(R.string.extraction_may_fail),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.error)
            }
      },
      confirmButton = {
        Button(onClick = onSelectDirectory) {
          Text(stringResource(R.string.select_source_directory))
        }
      },
      dismissButton = {
        TextButton(onClick = onProceedWithoutSource) {
          Text(stringResource(R.string.try_without_source))
        }
      })
}
