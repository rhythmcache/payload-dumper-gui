package com.rhythmcache.payloaddumper.repository

import com.rhythmcache.payloaddumper.PayloadDumper
import com.rhythmcache.payloaddumper.state.PartitionState
import com.rhythmcache.payloaddumper.utils.HashVerifier
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class LocalPayloadRepository {

  suspend fun extractPartition(
      partitionName: String,
      source: String,
      outputDir: String,
      verify: Boolean,
      partitionStates: ConcurrentHashMap<String, PartitionState>,
      cancelFlags: ConcurrentHashMap<String, AtomicBoolean>,
      semaphore: Semaphore?,
      jobs: ConcurrentHashMap<String, Job>,
      updateState: () -> Unit,
      launchJob: (suspend () -> Unit) -> Job
  ) {
    val initialState = partitionStates[partitionName] ?: return

    cancelFlags[partitionName]?.set(false)

    if (semaphore != null && semaphore.availablePermits == 0) {
      partitionStates.compute(partitionName) { _, current ->
        current?.copy(hasJob = true, isExtracting = false, progress = 0f, status = "Queued...")
      }
      updateState()
    } else {
      partitionStates.compute(partitionName) { _, current ->
        current?.copy(hasJob = true, isExtracting = true, progress = 0f, status = "Starting...")
      }
      updateState()
    }

    val outputPath = "$outputDir/${partitionName}.img"

    val job = launchJob {
      try {
        if (semaphore != null) {
          semaphore.withPermit {
            if (cancelFlags[partitionName]?.get() == true) {
              partitionStates.compute(partitionName) { _, current ->
                current?.copy(hasJob = false, isExtracting = false, status = "Cancelled")
              }
              updateState()
              return@launchJob
            }

            partitionStates.compute(partitionName) { _, current ->
              current?.copy(hasJob = true, isExtracting = true, status = "Starting...")
            }
            updateState()

            performExtraction(
                partitionName,
                source,
                outputPath,
                verify,
                partitionStates,
                cancelFlags,
                updateState)
          }
        } else {
          performExtraction(
              partitionName, source, outputPath, verify, partitionStates, cancelFlags, updateState)
        }
      } finally {
        jobs.remove(partitionName)
        partitionStates.compute(partitionName) { _, current -> current?.copy(hasJob = false) }
        updateState()
      }
    }

    jobs[partitionName] = job
  }

  private suspend fun performExtraction(
      partitionName: String,
      source: String,
      outputPath: String,
      verify: Boolean,
      partitionStates: ConcurrentHashMap<String, PartitionState>,
      cancelFlags: ConcurrentHashMap<String, AtomicBoolean>,
      updateState: () -> Unit
  ) {
    val wasCancelled = AtomicBoolean(false)
    val hadFatalError = AtomicBoolean(false)

    val callback =
        object : PayloadDumper.ProgressCallback {
          override fun onProgress(
              partitionName: String,
              currentOperation: Long,
              totalOperations: Long,
              percentage: Double,
              status: Int,
              warningOpIndex: Int,
              warningMessage: String
          ): Boolean {

            if (cancelFlags[partitionName]?.get() == true) {
              wasCancelled.set(true)
              return false
            }

            when (status) {
              0 -> {
                partitionStates.compute(partitionName) { _, current ->
                  current?.copy(progress = 0f, status = "Started")
                }
              }
              1 -> {
                partitionStates.compute(partitionName) { _, current ->
                  current?.copy(progress = percentage.toFloat(), status = "Extracting")
                }
              }
              2 -> {
                partitionStates.compute(partitionName) { _, current ->
                  current?.copy(progress = 100f, status = "Completed")
                }
              }
              3 -> {
                if (warningMessage.contains("Fatal error:", ignoreCase = true)) {
                  hadFatalError.set(true)
                  partitionStates.compute(partitionName) { _, current ->
                    current?.copy(status = "Error: $warningMessage")
                  }
                  return false
                } else {
                  partitionStates.compute(partitionName) { _, current ->
                    current?.copy(status = "Warning: $warningMessage")
                  }
                }
              }
            }

            updateState()
            return true
          }
        }

    try {
      PayloadDumper.extractLocalPartition(source, partitionName, outputPath, callback)

      if (wasCancelled.get()) {
        File(outputPath).delete()
        partitionStates.compute(partitionName) { _, current ->
          current?.copy(isExtracting = false, status = "Cancelled")
        }
      } else if (hadFatalError.get()) {
        File(outputPath).delete()
        partitionStates.compute(partitionName) { _, current -> current?.copy(isExtracting = false) }
      } else {
        partitionStates.compute(partitionName) { _, current ->
          current?.copy(isExtracting = false, status = "Completed: $outputPath")
        }

        val currentState = partitionStates[partitionName]
        if (verify && currentState != null && !currentState.partition.hash.isNullOrEmpty()) {
          HashVerifier.verifyPartition(
              partitionName, outputPath, currentState.partition.hash, partitionStates, updateState)
        }
      }
    } catch (e: Exception) {
      File(outputPath).delete()
      partitionStates.compute(partitionName) { _, current ->
        current?.copy(isExtracting = false, status = "Error: ${e.message}")
      }
    }

    updateState()
  }
}
