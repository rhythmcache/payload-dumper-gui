package com.rhythmcache.payloaddumper

object PayloadDumper {
  init {
    System.loadLibrary("payload_dumper")
  }

  external fun listLocalPartitions(path: String): String

  external fun listRemotePartitions(url: String, userAgent: String?, cookie: String?): String

  external fun extractLocalPartition(
      path: String,
      partitionName: String,
      outputPath: String,
      sourceDir: String?,
      callback: ProgressCallback?
  )

  external fun extractRemotePartition(
      url: String,
      partitionName: String,
      outputPath: String,
      userAgent: String?,
      cookie: String?,
      sourceDir: String?,
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
