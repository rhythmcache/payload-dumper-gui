package com.rhythmcache.payloaddumper.state

import com.rhythmcache.payloaddumper.PayloadInfo
import com.rhythmcache.payloaddumper.SourceType

sealed class UiState {
  object Idle : UiState()

  object Loading : UiState()

  data class PartitionsLoaded(
      val payloadInfo: PayloadInfo,
      val partitionStates: Map<String, PartitionState>,
      val source: String,
      val sourceType: SourceType,
      val outputDirectory: String,
      val rawJson: String? = null,
      val cookie: String? = null
  ) : UiState()

  data class Error(val message: String) : UiState()
}
