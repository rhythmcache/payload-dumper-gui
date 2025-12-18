package com.rhythmcache.payloaddumper

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.rhythmcache.payloaddumper.utils.StorageUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePickerDialog(
    onDismiss: () -> Unit,
    onFileSelected: (String) -> Unit,
    selectDirectory: Boolean = false
) {
  val context = LocalContext.current
  val storageLocations = remember { StorageUtils.getAvailableStorageLocations(context) }

  var selectedStorage by remember {
    mutableStateOf(storageLocations.firstOrNull { it.isPrimary } ?: storageLocations.first())
  }
  var currentPath by remember { mutableStateOf(selectedStorage.path) }
  var files by remember { mutableStateOf<List<File>>(emptyList()) }
  var showStorageMenu by remember { mutableStateOf(false) }

  LaunchedEffect(currentPath) {
    val dir = File(currentPath)

    files =
        if (dir.exists() && dir.isDirectory && dir.canRead()) {
          try {
            dir.listFiles()
                ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                ?.filter { file ->
                  if (selectDirectory) {
                    file.isDirectory
                  } else {
                    file.isDirectory || file.name.endsWith(".zip") || file.name.endsWith(".bin")
                  }
                } ?: emptyList()
          } catch (e: SecurityException) {

            emptyList()
          }
        } else {
          emptyList()
        }
  }

  Dialog(onDismissRequest = onDismiss) {
    Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
      Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
              Text(
                  text =
                      currentPath.substringAfterLast("/").ifEmpty {
                        stringResource(R.string.storage)
                      },
                  maxLines = 1)
            },
            navigationIcon = {
              if (currentPath != selectedStorage.path) {
                IconButton(
                    onClick = {
                      val parent = File(currentPath).parent
                      if (parent != null && parent.startsWith(selectedStorage.path)) {
                        currentPath = parent
                      }
                    }) {
                      Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
              } else if (storageLocations.size > 1) {

                IconButton(onClick = { showStorageMenu = true }) {
                  Icon(Icons.Default.Storage, contentDescription = stringResource(R.string.storage))
                }
              }
            },
            actions = {
              if (selectDirectory) {
                TextButton(onClick = { onFileSelected(currentPath) }) {
                  Text(stringResource(R.string.select))
                }
              }
              TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            })

        if (storageLocations.size > 1) {
          Surface(
              modifier = Modifier.fillMaxWidth().clickable { showStorageMenu = true },
              color = MaterialTheme.colorScheme.secondaryContainer,
              tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                      Row(
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                if (selectedStorage.isRemovable) Icons.Default.SdCard
                                else Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(
                                selectedStorage.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                          }
                      Icon(
                          Icons.Default.ArrowDropDown,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
              }
        }

        Text(
            text = currentPath,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        HorizontalDivider()

        if (files.isEmpty()) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (selectDirectory) stringResource(R.string.no_directories_found)
                else stringResource(R.string.no_files_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        } else {
          LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
            items(files) { file ->
              FileItem(
                  file = file,
                  onClick = {
                    if (file.isDirectory) {
                      currentPath = file.absolutePath
                    } else if (!selectDirectory) {
                      onFileSelected(file.absolutePath)
                    }
                  })
            }
          }
        }
      }
    }
  }

  if (showStorageMenu) {
    AlertDialog(
        onDismissRequest = { showStorageMenu = false },
        icon = { Icon(Icons.Default.Storage, contentDescription = null) },
        title = { Text(stringResource(R.string.select_storage)) },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            storageLocations.forEach { location ->
              Card(
                  modifier =
                      Modifier.fillMaxWidth().clickable {
                        selectedStorage = location
                        currentPath = location.path
                        showStorageMenu = false
                      },
                  colors =
                      CardDefaults.cardColors(
                          containerColor =
                              if (location == selectedStorage)
                                  MaterialTheme.colorScheme.primaryContainer
                              else MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                          Icon(
                              if (location.isRemovable) Icons.Default.SdCard
                              else Icons.Default.PhoneAndroid,
                              contentDescription = null,
                              tint =
                                  if (location == selectedStorage)
                                      MaterialTheme.colorScheme.onPrimaryContainer
                                  else MaterialTheme.colorScheme.onSurfaceVariant)
                          Column(modifier = Modifier.weight(1f)) {
                            Text(
                                location.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color =
                                    if (location == selectedStorage)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface)
                            Text(
                                location.path,
                                style = MaterialTheme.typography.bodySmall,
                                color =
                                    if (location == selectedStorage)
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                            alpha = 0.7f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                          }
                          if (location == selectedStorage) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                          }
                        }
                  }
            }
          }
        },
        confirmButton = {
          TextButton(onClick = { showStorageMenu = false }) { Text(stringResource(R.string.close)) }
        })
  }
}

@Composable
fun FileItem(file: File, onClick: () -> Unit) {
  Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick)) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
          Icon(
              imageVector =
                  if (file.isDirectory) Icons.Default.Folder
                  else Icons.AutoMirrored.Filled.InsertDriveFile,
              contentDescription = null,
              tint =
                  if (file.isDirectory) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.secondary)
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(text = file.name, style = MaterialTheme.typography.bodyLarge)
            if (!file.isDirectory) {
              Text(
                  text = formatFileSize(file.length()),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
  }
}

fun formatFileSize(bytes: Long): String {
  val kb = bytes / 1024.0
  val mb = kb / 1024.0
  val gb = mb / 1024.0

  return when {
    gb >= 1 -> "%.2f GB".format(gb)
    mb >= 1 -> "%.2f MB".format(mb)
    kb >= 1 -> "%.2f KB".format(kb)
    else -> "$bytes B"
  }
}
