package com.rhythmcache.payloaddumper.utils

import com.rhythmcache.payloaddumper.state.PartitionState
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object HashVerifier {

  suspend fun verifyPartition(
      partitionName: String,
      filePath: String,
      expectedHash: String,
      partitionStates: ConcurrentHashMap<String, PartitionState>,
      updateState: () -> Unit
  ) {
    partitionStates.compute(partitionName) { _, current ->
      current?.copy(isVerifying = true, verifyProgress = 0f, verifyStatus = "Verifying...")
    }
    updateState()

    withContext(Dispatchers.IO) {
      try {
        val file = File(filePath)
        val md = MessageDigest.getInstance("SHA-256")
        val fis = FileInputStream(file)
        val buffer = ByteArray(8192)
        var read: Int
        var totalRead = 0L
        val fileSize = file.length()

        while (fis.read(buffer).also { read = it } != -1) {
          md.update(buffer, 0, read)
          totalRead += read
          val progress = (totalRead * 100f / fileSize)

          partitionStates.compute(partitionName) { _, current ->
            current?.copy(verifyProgress = progress)
          }
          updateState()
        }
        fis.close()

        val hash = md.digest().joinToString("") { "%02x".format(it) }
        val passed = hash.equals(expectedHash, ignoreCase = true)

        partitionStates.compute(partitionName) { _, current ->
          current?.copy(
              isVerifying = false,
              verifyProgress = 100f,
              verifyStatus = if (passed) "Verified" else "Verification FAILED",
              verificationPassed = passed)
        }
      } catch (e: Exception) {
        partitionStates.compute(partitionName) { _, current ->
          current?.copy(isVerifying = false, verifyStatus = "Verify Error: ${e.message}")
        }
      }
      updateState()
    }
  }
}
