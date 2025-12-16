package com.rhythmcache.payloaddumper

object PayloadDumper {
  init {
    System.loadLibrary("payload_dumper")
  }

  external fun listPartitions(payloadPath: String): String

  external fun listPartitionsZip(zipPath: String): String

  external fun extractPartition(
      payloadPath: String,
      partitionName: String,
      outputPath: String,
      callback: ProgressCallback?
  )

  external fun extractPartitionZip(
      zipPath: String,
      partitionName: String,
      outputPath: String,
      callback: ProgressCallback?
  )

  external fun listPartitionsRemoteZip(url: String, userAgent: String?, cookie: String?): String

  external fun listPartitionsRemoteBin(url: String, userAgent: String?, cookie: String?): String

  external fun extractPartitionRemoteZip(
      url: String,
      partitionName: String,
      outputPath: String,
      userAgent: String?,
      cookie: String?,
      callback: ProgressCallback?
  )

  external fun extractPartitionRemoteBin(
      url: String,
      partitionName: String,
      outputPath: String,
      userAgent: String?,
      cookie: String?,
      callback: ProgressCallback?
  )

  interface ProgressCallback {
    fun onProgress(
        partitionName: String,
        currentOperation: Long,
        totalOperations: Long,
        percentage: Double,
        status: Int,
        warningOpIndex: Int,
        warningMessage: String
    ): Boolean
  }
}
