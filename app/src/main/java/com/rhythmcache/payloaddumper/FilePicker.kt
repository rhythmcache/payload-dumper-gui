package com.rhythmcache.payloaddumper

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePickerDialog(
    onDismiss: () -> Unit,
    onFileSelected: (String) -> Unit,
    selectDirectory: Boolean = false,
    rootPath: String = "/storage/emulated/0"
) {
  var currentPath by remember { mutableStateOf(rootPath) }
  var files by remember { mutableStateOf<List<File>>(emptyList()) }

  LaunchedEffect(currentPath) {
    val dir = File(currentPath)
    files =
        if (dir.exists() && dir.isDirectory) {
          dir.listFiles()
              ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
              ?.filter { file ->
                if (selectDirectory) {
                  file.isDirectory
                } else {
                  file.isDirectory || file.name.endsWith(".zip") || file.name.endsWith(".bin")
                }
              } ?: emptyList()
        } else {
          emptyList()
        }
  }

  Dialog(onDismissRequest = onDismiss) {
    Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
      Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
              Text(text = currentPath.substringAfterLast("/").ifEmpty { "Storage" }, maxLines = 1)
            },
            navigationIcon = {
              if (currentPath != rootPath) {
                IconButton(
                    onClick = {
                      val parent = File(currentPath).parent
                      if (parent != null && parent.startsWith(rootPath)) {
                        currentPath = parent
                      }
                    }) {
                      Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
              }
            },
            actions = {
              if (selectDirectory) {
                TextButton(onClick = { onFileSelected(currentPath) }) { Text("SELECT") }
              }
              TextButton(onClick = onDismiss) { Text("CANCEL") }
            })

        Text(
            text = currentPath,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        HorizontalDivider()

        if (files.isEmpty()) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (selectDirectory) "No directories found" else "No .zip or .bin files found",
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
