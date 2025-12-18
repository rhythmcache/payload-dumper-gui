// StorageUtils.kt
package com.rhythmcache.payloaddumper.utils

import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import java.io.File

data class StorageLocation(
    val name: String,
    val path: String,
    val isRemovable: Boolean,
    val isPrimary: Boolean
)

object StorageUtils {

  fun getAvailableStorageLocations(context: Context): List<StorageLocation> {
    val locations = mutableListOf<StorageLocation>()
    val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    val storageVolumes = storageManager.storageVolumes

    for (volume in storageVolumes) {
      val path =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            volume.directory?.absolutePath
          } else {
            getVolumePathReflection(volume)
          }

      if (path != null) {
        locations.add(
            StorageLocation(
                name =
                    when {
                      volume.isPrimary -> "Internal Storage"
                      volume.isRemovable -> volume.getDescription(context)
                      else -> "Storage"
                    },
                path = path,
                isRemovable = volume.isRemovable,
                isPrimary = volume.isPrimary))
      }
    }
    return locations.filter {
      it.path.isNotEmpty() &&
          File(it.path).let { dir -> dir.exists() && dir.isDirectory && dir.canRead() }
    }
  }

  private fun getVolumePathReflection(volume: android.os.storage.StorageVolume): String? {
    return try {
      val getPathMethod = volume.javaClass.getMethod("getPath")
      getPathMethod.invoke(volume) as? String
    } catch (e: Exception) {
      null
    }
  }
}
