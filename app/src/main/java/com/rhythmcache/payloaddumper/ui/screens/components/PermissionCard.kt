package com.rhythmcache.payloaddumper.ui.screens.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rhythmcache.payloaddumper.R

@Composable
fun PermissionCard() {
  val context = LocalContext.current

  Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
      modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
          Text(
              stringResource(R.string.storage_permission_required),
              color = MaterialTheme.colorScheme.onErrorContainer)
          Spacer(modifier = Modifier.height(8.dp))
          Button(
              onClick = {
                val intent =
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${context.packageName}"))
                context.startActivity(intent)
              }) {
                Text(stringResource(R.string.grant_permission))
              }
        }
      }
}
