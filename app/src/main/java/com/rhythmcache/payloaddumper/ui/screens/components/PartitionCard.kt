@file:OptIn(ExperimentalFoundationApi::class)

package com.rhythmcache.payloaddumper.ui.screens.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rhythmcache.payloaddumper.BuildConfig
import com.rhythmcache.payloaddumper.R
import com.rhythmcache.payloaddumper.state.PartitionState
import com.rhythmcache.payloaddumper.viewmodel.PayloadViewModel

@Composable
fun PartitionCard(
    state: PartitionState,
    viewModel: PayloadViewModel,
    isLocal: Boolean,
    source: String,
    outputDirectory: String,
    selectionMode: Boolean,
    onEnterSelectionMode: () -> Unit,
    cookie: String? = null
) {
  val context = LocalContext.current

  Card(
      modifier =
          Modifier.fillMaxWidth()
              .combinedClickable(
                  onClick = {
                    if (selectionMode && !state.hasJob) {
                      if (isLocal) {
                        viewModel.toggleSelectionLocal(state.partition.name)
                      } else {
                        viewModel.toggleSelectionRemote(state.partition.name)
                      }
                    }
                  },
                  onLongClick = {
                    if (!state.hasJob) {
                      onEnterSelectionMode()
                      if (isLocal) {
                        viewModel.toggleSelectionLocal(state.partition.name)
                      } else {
                        viewModel.toggleSelectionRemote(state.partition.name)
                      }
                    }
                  })) {
        Column(modifier = Modifier.padding(12.dp)) {
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)) {
                      if (selectionMode) {
                        Checkbox(
                            checked = state.selected,
                            onCheckedChange = {
                              if (isLocal) {
                                viewModel.toggleSelectionLocal(state.partition.name)
                              } else {
                                viewModel.toggleSelectionRemote(state.partition.name)
                              }
                            },
                            enabled = !state.hasJob)
                        Spacer(modifier = Modifier.width(8.dp))
                      }

                      Column {
                        Text(state.partition.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(
                                R.string.ops_compression,
                                state.partition.size_readable,
                                state.partition.operations_count,
                                state.partition.compression_type.uppercase()),
                            style = MaterialTheme.typography.bodySmall)
                      }
                    }

                if (!selectionMode) {
                  if (state.hasJob) {
                    Button(
                        onClick = {
                          if (isLocal) {
                            viewModel.cancelExtractionLocal(state.partition.name)
                          } else {
                            viewModel.cancelExtractionRemote(state.partition.name)
                          }
                        },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error)) {
                          Text(stringResource(R.string.cancel))
                        }
                  } else {
                    Button(
                        onClick = {
                          val prefs = context.getSharedPreferences("settings", 0)
                          val verify = prefs.getBoolean("verify", true)

                          val limitThreading = prefs.getBoolean("limit_threading", false)
                          val threadMode = prefs.getString("thread_mode", "default") ?: "default"
                          val customThreadCount =
                              prefs.getInt(
                                  "custom_thread_count", Runtime.getRuntime().availableProcessors())

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
                            viewModel.extractPartitionLocal(
                                state.partition.name,
                                source,
                                outputDirectory,
                                verify,
                                concurrentLimit)
                          } else {
                            val userAgent =
                                prefs.getString("user_agent", BuildConfig.USER_AGENT)
                                    ?: BuildConfig.USER_AGENT
                            viewModel.extractPartitionRemote(
                                state.partition.name,
                                source,
                                outputDirectory,
                                userAgent,
                                verify,
                                concurrentLimit,
                                cookie)
                          }
                        }) {
                          Icon(Icons.Default.SaveAlt, contentDescription = null)
                          Spacer(modifier = Modifier.width(4.dp))
                          Text(stringResource(R.string.extract))
                        }
                  }
                }
              }

          if (state.isExtracting) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
            Text("${state.progress.toInt()}% - ${state.status}")
          }

          if (state.status.isNotBlank() && !state.isExtracting && !state.isVerifying) {
            Text(state.status, style = MaterialTheme.typography.bodySmall)
          }

          if (state.isVerifying) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.verifyProgress / 100f }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.verifying_progress, state.verifyProgress.toInt()))
          }

          if (!state.isVerifying && state.verifyStatus.isNotBlank()) {
            Text(
                state.verifyStatus,
                color =
                    if (state.verificationPassed) {
                      MaterialTheme.colorScheme.primary
                    } else {
                      MaterialTheme.colorScheme.error
                    })
          }
        }
      }
}
