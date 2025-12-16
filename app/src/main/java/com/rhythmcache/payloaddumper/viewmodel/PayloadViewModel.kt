package com.rhythmcache.payloaddumper.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rhythmcache.payloaddumper.*
import com.rhythmcache.payloaddumper.repository.LocalPayloadRepository
import com.rhythmcache.payloaddumper.repository.RemotePayloadRepository
import com.rhythmcache.payloaddumper.state.PartitionState
import com.rhythmcache.payloaddumper.state.UiState
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext

class PayloadViewModel : ViewModel() {
  private val localRepository = LocalPayloadRepository()
  private val remoteRepository = RemotePayloadRepository()
  private val _localUiState = MutableStateFlow<UiState>(UiState.Idle)
  val localUiState: StateFlow<UiState> = _localUiState.asStateFlow()
  private val _remoteUiState = MutableStateFlow<UiState>(UiState.Idle)
  val remoteUiState: StateFlow<UiState> = _remoteUiState.asStateFlow()
  private val localPartitionStates = ConcurrentHashMap<String, PartitionState>()
  private val localCancelFlags = ConcurrentHashMap<String, AtomicBoolean>()
  private val localExtractionJobs = ConcurrentHashMap<String, Job>()
  private var localExtractionSemaphore: Semaphore? = null
  private val remotePartitionStates = ConcurrentHashMap<String, PartitionState>()
  private val remoteCancelFlags = ConcurrentHashMap<String, AtomicBoolean>()
  private val remoteExtractionJobs = ConcurrentHashMap<String, Job>()
  private var remoteExtractionSemaphore: Semaphore? = null

  fun resetLocal() {
    localExtractionJobs.values.forEach { it.cancel() }
    localExtractionJobs.clear()
    _localUiState.value = UiState.Idle
    localPartitionStates.clear()
    localCancelFlags.clear()
    localExtractionSemaphore = null
  }

  fun resetRemote() {
    remoteExtractionJobs.values.forEach { it.cancel() }
    remoteExtractionJobs.clear()
    _remoteUiState.value = UiState.Idle
    remotePartitionStates.clear()
    remoteCancelFlags.clear()
    remoteExtractionSemaphore = null
  }

  fun setRemoteError(message: String) {
    _remoteUiState.value = UiState.Error(message)
  }

  fun loadLocalPartitions(source: String, type: SourceType, baseOutputDir: String) {
    viewModelScope.launch {
      _localUiState.value = UiState.Loading
      try {
        val jsonResult =
            withContext(Dispatchers.IO) {
              when (type) {
                SourceType.LOCAL_BIN -> PayloadDumper.listPartitions(source)
                SourceType.LOCAL_ZIP -> PayloadDumper.listPartitionsZip(source)
                else -> throw IllegalArgumentException("Invalid type for local")
              }
            }
        val payloadInfo = PayloadParser.parse(jsonResult)

        localPartitionStates.clear()
        localCancelFlags.clear()
        localExtractionJobs.clear()
        payloadInfo.partitions.forEach { partition ->
          localPartitionStates[partition.name] = PartitionState(partition)
          localCancelFlags[partition.name] = AtomicBoolean(false)
        }

        val timestamp = System.currentTimeMillis()
        val typePrefix = if (type == SourceType.LOCAL_ZIP) "local-zip" else "local-bin"
        val uniqueDir = File(baseOutputDir, "$typePrefix-$timestamp")
        uniqueDir.mkdirs()

        _localUiState.value =
            UiState.PartitionsLoaded(
                payloadInfo,
                localPartitionStates.toMap(),
                source,
                type,
                uniqueDir.absolutePath,
                jsonResult)
      } catch (e: Exception) {
        _localUiState.value = UiState.Error(e.message ?: "Unknown error")
      }
    }
  }

  fun loadRemotePartitions(
      source: String,
      type: SourceType,
      userAgent: String,
      baseOutputDir: String,
      cookie: String? = null
  ) {
    viewModelScope.launch {
      _remoteUiState.value = UiState.Loading
      try {
        val jsonResult =
            withContext(Dispatchers.IO) {
              when (type) {
                SourceType.REMOTE_BIN ->
                    PayloadDumper.listPartitionsRemoteBin(source, userAgent, cookie)
                SourceType.REMOTE_ZIP ->
                    PayloadDumper.listPartitionsRemoteZip(source, userAgent, cookie)
                else -> throw IllegalArgumentException("Invalid type for remote")
              }
            }
        val payloadInfo = PayloadParser.parse(jsonResult)

        remotePartitionStates.clear()
        remoteCancelFlags.clear()
        remoteExtractionJobs.clear()
        payloadInfo.partitions.forEach { partition ->
          remotePartitionStates[partition.name] = PartitionState(partition)
          remoteCancelFlags[partition.name] = AtomicBoolean(false)
        }

        val timestamp = System.currentTimeMillis()
        val typePrefix = if (type == SourceType.REMOTE_ZIP) "remote-zip" else "remote-bin"
        val uniqueDir = File(baseOutputDir, "$typePrefix-$timestamp")
        uniqueDir.mkdirs()

        _remoteUiState.value =
            UiState.PartitionsLoaded(
                payloadInfo,
                remotePartitionStates.toMap(),
                source,
                type,
                uniqueDir.absolutePath,
                jsonResult,
                cookie)
      } catch (e: Exception) {
        _remoteUiState.value = UiState.Error(e.message ?: "Unknown error")
      }
    }
  }

  fun toggleSelectionLocal(partitionName: String) {
    localPartitionStates.compute(partitionName) { _, current ->
      if (current == null || current.hasJob) current else current.copy(selected = !current.selected)
    }
    updateLocalState()
  }

  fun selectAllLocal() {
    localPartitionStates.forEach { (name, state) ->
      if (!state.hasJob) {
        localPartitionStates.compute(name) { _, current -> current?.copy(selected = true) }
      }
    }
    updateLocalState()
  }

  fun deselectAllLocal() {
    localPartitionStates.forEach { (name, _) ->
      localPartitionStates.compute(name) { _, current -> current?.copy(selected = false) }
    }
    updateLocalState()
  }

  fun toggleSelectionRemote(partitionName: String) {
    remotePartitionStates.compute(partitionName) { _, current ->
      if (current == null || current.isExtracting) current
      else current.copy(selected = !current.selected)
    }
    updateRemoteState()
  }

  fun selectAllRemote() {
    remotePartitionStates.forEach { (name, state) ->
      if (!state.isExtracting) {
        remotePartitionStates.compute(name) { _, current -> current?.copy(selected = true) }
      }
    }
    updateRemoteState()
  }

  fun deselectAllRemote() {
    remotePartitionStates.forEach { (name, _) ->
      remotePartitionStates.compute(name) { _, current -> current?.copy(selected = false) }
    }
    updateRemoteState()
  }

  fun cancelExtractionLocal(partitionName: String) {
    localCancelFlags[partitionName]?.set(true)
    localExtractionJobs[partitionName]?.cancel()
    localExtractionJobs.remove(partitionName)

    localPartitionStates.compute(partitionName) { _, current ->
      current?.copy(hasJob = false, isExtracting = false, status = "Cancelled")
    }
    updateLocalState()
  }

  fun extractSelectedLocal(
      source: String,
      type: SourceType,
      outputDir: String,
      verify: Boolean,
      concurrentLimit: Int? = null
  ) {
    if (localExtractionSemaphore == null && concurrentLimit != null) {
      localExtractionSemaphore = Semaphore(concurrentLimit)
    }

    localPartitionStates.values
        .filter { it.selected && !it.hasJob }
        .forEach { state ->
          extractPartitionLocal(state.partition.name, source, type, outputDir, verify, null)
        }
  }

  fun extractPartitionLocal(
      partitionName: String,
      source: String,
      type: SourceType,
      outputDir: String,
      verify: Boolean,
      concurrentLimit: Int? = null
  ) {
    if (localExtractionSemaphore == null && concurrentLimit != null) {
      localExtractionSemaphore = Semaphore(concurrentLimit)
    }

    viewModelScope.launch(Dispatchers.IO) {
      localRepository.extractPartition(
          partitionName = partitionName,
          source = source,
          type = type,
          outputDir = outputDir,
          verify = verify,
          partitionStates = localPartitionStates,
          cancelFlags = localCancelFlags,
          semaphore = localExtractionSemaphore,
          jobs = localExtractionJobs,
          updateState = ::updateLocalState,
          launchJob = { block -> viewModelScope.launch(Dispatchers.IO) { block() } })
    }
  }

  fun cancelExtractionRemote(partitionName: String) {
    remoteCancelFlags[partitionName]?.set(true)
    remoteExtractionJobs[partitionName]?.cancel()
    remoteExtractionJobs.remove(partitionName)

    remotePartitionStates.compute(partitionName) { _, current ->
      current?.copy(hasJob = false, isExtracting = false, status = "Cancelled")
    }
    updateRemoteState()
  }

  fun extractSelectedRemote(
      source: String,
      type: SourceType,
      outputDir: String,
      userAgent: String,
      verify: Boolean,
      concurrentLimit: Int? = null,
      cookie: String? = null
  ) {
    if (remoteExtractionSemaphore == null && concurrentLimit != null) {
      remoteExtractionSemaphore = Semaphore(concurrentLimit)
    }

    remotePartitionStates.values
        .filter { it.selected && !it.hasJob }
        .forEach { state ->
          extractPartitionRemote(
              state.partition.name, source, type, outputDir, userAgent, verify, null, cookie)
        }
  }

  fun extractPartitionRemote(
      partitionName: String,
      source: String,
      type: SourceType,
      outputDir: String,
      userAgent: String,
      verify: Boolean,
      concurrentLimit: Int? = null,
      cookie: String? = null
  ) {
    if (remoteExtractionSemaphore == null && concurrentLimit != null) {
      remoteExtractionSemaphore = Semaphore(concurrentLimit)
    }

    viewModelScope.launch(Dispatchers.IO) {
      remoteRepository.extractPartition(
          partitionName = partitionName,
          source = source,
          type = type,
          outputDir = outputDir,
          userAgent = userAgent,
          verify = verify,
          partitionStates = remotePartitionStates,
          cancelFlags = remoteCancelFlags,
          semaphore = remoteExtractionSemaphore,
          jobs = remoteExtractionJobs,
          updateState = ::updateRemoteState,
          launchJob = { block -> viewModelScope.launch(Dispatchers.IO) { block() } },
          cookie = cookie)
    }
  }

  private fun updateLocalState() {
    val current = _localUiState.value
    if (current is UiState.PartitionsLoaded) {
      _localUiState.value = current.copy(partitionStates = localPartitionStates.toMap())
    }
  }

  private fun updateRemoteState() {
    val current = _remoteUiState.value
    if (current is UiState.PartitionsLoaded) {
      _remoteUiState.value = current.copy(partitionStates = remotePartitionStates.toMap())
    }
  }
}
