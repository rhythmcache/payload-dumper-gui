package com.rhythmcache.payloaddumper.state

import com.rhythmcache.payloaddumper.Partition

data class PartitionState(
    val partition: Partition,
    val selected: Boolean = false,
    val hasJob: Boolean = false,
    val isExtracting: Boolean = false,
    val progress: Float = 0f,
    val status: String = "",
    val isVerifying: Boolean = false,
    val verifyProgress: Float = 0f,
    val verifyStatus: String = "",
    val verificationPassed: Boolean = false
)
