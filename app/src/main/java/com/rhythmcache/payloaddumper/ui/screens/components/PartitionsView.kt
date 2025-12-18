@file:OptIn(ExperimentalFoundationApi::class)

package com.rhythmcache.payloaddumper.ui.screens.components

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rhythmcache.payloaddumper.BuildConfig
import com.rhythmcache.payloaddumper.R
import com.rhythmcache.payloaddumper.state.UiState
import com.rhythmcache.payloaddumper.viewmodel.PayloadViewModel
import java.io.File

@Composable
fun PartitionsView(
    state: UiState.PartitionsLoaded,
    viewModel: PayloadViewModel,
    isLocal: Boolean,
    onLoadDifferent: () -> Unit
) {
  val context = LocalContext.current
  var selectionMode by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }
  var showSearchBar by remember { mutableStateOf(false) }

  val selectedCount = state.partitionStates.values.count { it.selected }
  val allSelected = state.partitionStates.values.all { it.selected }

  val filteredPartitions =
      remember(state.partitionStates, searchQuery) {
        if (searchQuery.isBlank()) {
          state.partitionStates.values.toList()
        } else {
          state.partitionStates.values
              .filter { partState ->
                partState.partition.name.contains(searchQuery, ignoreCase = true)
              }
              .toList()
        }
      }

  Column {
    PartitionsHeader(
        state = state,
        showSearchBar = showSearchBar,
        onToggleSearch = {
          showSearchBar = !showSearchBar
          if (!showSearchBar) searchQuery = ""
        },
        onViewJson = {
          state.rawJson?.let { jsonString ->
            try {
              val jsonFile =
                  File(context.cacheDir, "payload_info_${System.currentTimeMillis()}.json")
              jsonFile.writeText(jsonString)

              val uri =
                  androidx.core.content.FileProvider.getUriForFile(
                      context, "${context.packageName}.fileprovider", jsonFile)

              val intent =
                  Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/json")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                  }

              try {
                context.startActivity(intent)
              } catch (e: Exception) {
                val textIntent =
                    Intent(Intent.ACTION_VIEW).apply {
                      setDataAndType(uri, "text/plain")
                      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                context.startActivity(textIntent)
              }
            } catch (e: Exception) {}
          }
        },
        onLoadDifferent = onLoadDifferent)

    if (showSearchBar) {
      SearchBar(searchQuery = searchQuery, onSearchQueryChange = { searchQuery = it })
    }

    if (selectionMode) {
      SelectionToolbar(
          selectedCount = selectedCount,
          allSelected = allSelected,
          onToggleSelectAll = {
            if (allSelected) {
              if (isLocal) viewModel.deselectAllLocal() else viewModel.deselectAllRemote()
            } else {
              if (isLocal) viewModel.selectAllLocal() else viewModel.selectAllRemote()
            }
          },
          onExtractSelected = {
            val prefs = context.getSharedPreferences("settings", 0)
            val verify = prefs.getBoolean("verify", true)

            val limitThreading = prefs.getBoolean("limit_threading", false)
            val threadMode = prefs.getString("thread_mode", "default") ?: "default"
            val customThreadCount =
                prefs.getInt("custom_thread_count", Runtime.getRuntime().availableProcessors())

            val concurrentLimit =
                if (limitThreading) {
                  if (threadMode == "default") {
                    Runtime.getRuntime().availableProcessors()
                  } else {
                    customThreadCount
                  }
                } else {
                  null
                }

            if (isLocal) {
              viewModel.extractSelectedLocal(
                  state.source, state.sourceType, state.outputDirectory, verify, concurrentLimit)
            } else {
              val userAgent =
                  prefs.getString("user_agent", BuildConfig.USER_AGENT) ?: BuildConfig.USER_AGENT

              val cookie = state.cookie
              viewModel.extractSelectedRemote(
                  state.source,
                  state.sourceType,
                  state.outputDirectory,
                  userAgent,
                  verify,
                  concurrentLimit,
                  cookie)
            }
            selectionMode = false
            if (isLocal) viewModel.deselectAllLocal() else viewModel.deselectAllRemote()
          },
          onCancel = {
            selectionMode = false
            if (isLocal) viewModel.deselectAllLocal() else viewModel.deselectAllRemote()
          })
    }

    if (searchQuery.isNotBlank()) {
      Text(
          stringResource(
              R.string.showing_filtered, filteredPartitions.size, state.partitionStates.size),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(bottom = 8.dp))
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      items(filteredPartitions, key = { it.partition.name }) { partState ->
        PartitionCard(
            state = partState,
            viewModel = viewModel,
            isLocal = isLocal,
            source = state.source,
            type = state.sourceType,
            outputDirectory = state.outputDirectory,
            selectionMode = selectionMode,
            onEnterSelectionMode = { selectionMode = true },
            cookie = if (isLocal) null else state.cookie)
      }
    }
  }
}

@Composable
private fun PartitionsHeader(
    state: UiState.PartitionsLoaded,
    showSearchBar: Boolean,
    onToggleSearch: () -> Unit,
    onViewJson: () -> Unit,
    onLoadDifferent: () -> Unit
) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          state.payloadInfo.security_patch_level?.let { patch ->
            Text(
                stringResource(R.string.security_patch, patch),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary)
          }
          Text(
              stringResource(
                  R.string.partitions_size,
                  state.payloadInfo.total_partitions,
                  state.payloadInfo.total_size_readable),
              style = MaterialTheme.typography.bodyMedium)
        }

        Row {
          IconButton(onClick = onToggleSearch) {
            Icon(
                if (showSearchBar) Icons.Default.Close else Icons.Default.Search,
                contentDescription =
                    if (showSearchBar) stringResource(R.string.close_search)
                    else stringResource(R.string.search_partitions))
          }

          IconButton(onClick = onViewJson) {
            Icon(
                Icons.Default.DataObject,
                contentDescription = stringResource(R.string.view_raw_json))
          }

          IconButton(onClick = onLoadDifferent) {
            Icon(
                Icons.Default.SwapHoriz,
                contentDescription = stringResource(R.string.load_different_payload))
          }
        }
      }
}

@Composable
private fun SearchBar(searchQuery: String, onSearchQueryChange: (String) -> Unit) {
  TextField(
      value = searchQuery,
      onValueChange = onSearchQueryChange,
      modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
      placeholder = { Text(stringResource(R.string.search_partitions_placeholder)) },
      leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
      trailingIcon = {
        if (searchQuery.isNotEmpty()) {
          IconButton(onClick = { onSearchQueryChange("") }) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear))
          }
        }
      },
      singleLine = true,
      colors =
          TextFieldDefaults.colors(
              focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
              unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
              focusedIndicatorColor = MaterialTheme.colorScheme.primary,
              unfocusedIndicatorColor = MaterialTheme.colorScheme.outline))
}

@Composable
private fun SelectionToolbar(
    selectedCount: Int,
    allSelected: Boolean,
    onToggleSelectAll: () -> Unit,
    onExtractSelected: () -> Unit,
    onCancel: () -> Unit
) {
  Card(
      modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
      colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleSelectAll) {
                  Icon(
                      if (allSelected) Icons.Default.CheckBox
                      else Icons.Default.CheckBoxOutlineBlank,
                      contentDescription = stringResource(R.string.select_all))
                }
                Text(
                    stringResource(R.string.selected_count, selectedCount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold)
              }

              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExtractSelected, enabled = selectedCount > 0) {
                  Icon(Icons.Default.SaveAlt, contentDescription = null)
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(stringResource(R.string.extract))
                }

                IconButton(onClick = onCancel) {
                  Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                }
              }
            }
      }
}
