package com.rhythmcache.payloaddumper

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PayloadInfo(
    val partitions: List<Partition>,
    val total_partitions: Int,
    val total_operations: Int,
    val total_size_bytes: Long,
    val total_size_readable: String,
    val security_patch_level: String? = null
)

@Serializable
data class Partition(
    val name: String,
    val size_bytes: Long,
    val size_readable: String,
    val operations_count: Int,
    val compression_type: String,
    val hash: String
)

object PayloadParser {
  private val json = Json { ignoreUnknownKeys = true }

  fun parse(jsonString: String): PayloadInfo {
    return json.decodeFromString(jsonString)
  }
}
