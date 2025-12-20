package com.rhythmcache.payloaddumper.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.rhythmcache.payloaddumper.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val changelog: String,
    val releaseDate: String,
    val isUpdateAvailable: Boolean
)

object UpdateChecker {
  suspend fun checkForUpdate(
      context: Context,
      currentVersionCode: Int,
      currentVersionName: String
  ): UpdateInfo? {
    return withContext(Dispatchers.IO) {
      try {
        val url = URL(BuildConfig.GITHUB_API_URL)
        val connection = url.openConnection() as HttpURLConnection
        connection.apply {
          requestMethod = "GET"
          setRequestProperty("Accept", "application/vnd.github.v3+json")
          connectTimeout = 10000
          readTimeout = 10000
        }

        if (connection.responseCode != 200) {
          return@withContext null
        }

        val response = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()

        parseReleaseInfo(response, currentVersionCode, currentVersionName)
      } catch (e: Exception) {
        e.printStackTrace()
        null
      }
    }
  }

  private fun parseReleaseInfo(
      jsonResponse: String,
      currentVersionCode: Int,
      currentVersionName: String
  ): UpdateInfo? {
    try {
      val json = JSONObject(jsonResponse)
      val tagName = json.getString("tag_name")
      val versionName = tagName.removePrefix("v")
      val releaseName = json.getString("name")
      val releaseBody = json.optString("body", "")
      val versionCode = extractVersionCode(releaseName, releaseBody, versionName)
      val changelog = releaseBody.ifEmpty { "No changelog available" }
      val releaseDate = json.getString("published_at")
      val assets = json.getJSONArray("assets")
      val currentAbi = getCurrentAbi()
      var downloadUrl = ""

      for (i in 0 until assets.length()) {
        val asset = assets.getJSONObject(i)
        val assetName = asset.getString("name")
        if (assetName.endsWith(".apk") &&
            (assetName.contains(currentAbi, ignoreCase = true) ||
                assetName.contains(currentAbi.replace("-", "_"), ignoreCase = true))) {
          downloadUrl = asset.getString("browser_download_url")
          break
        }
      }
      if (downloadUrl.isEmpty()) {
        for (i in 0 until assets.length()) {
          val asset = assets.getJSONObject(i)
          val assetName = asset.getString("name")
          if (assetName.endsWith(".apk") && assetName.contains("universal", ignoreCase = true)) {
            downloadUrl = asset.getString("browser_download_url")
            break
          }
        }
      }
      if (downloadUrl.isEmpty()) {
        for (i in 0 until assets.length()) {
          val asset = assets.getJSONObject(i)
          val assetName = asset.getString("name")
          if (assetName.endsWith(".apk") && assetName.contains("android", ignoreCase = true)) {
            downloadUrl = asset.getString("browser_download_url")
            break
          }
        }
      }

      if (downloadUrl.isEmpty()) {
        return null
      }
      val isUpdateAvailable =
          if (versionCode > 0 && currentVersionCode > 0) {
            versionCode > currentVersionCode
          } else {
            compareVersions(versionName, currentVersionName) > 0
          }

      return UpdateInfo(
          versionName = versionName,
          versionCode = versionCode,
          downloadUrl = downloadUrl,
          changelog = cleanChangelog(changelog),
          releaseDate = releaseDate,
          isUpdateAvailable = isUpdateAvailable)
    } catch (e: Exception) {
      e.printStackTrace()
      return null
    }
  }

  private fun extractVersionCode(releaseName: String, body: String, versionName: String): Int {
    val regex = """\((\d+)\)""".toRegex()
    regex.find(releaseName)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
      return it
    }
    val bodyRegex = """version\s*code[:\s]+(\d+)""".toRegex(RegexOption.IGNORE_CASE)
    bodyRegex.find(body)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
      return it
    }
    return generateVersionCodeFromSemVer(versionName)
  }

  private fun generateVersionCodeFromSemVer(version: String): Int {
    return try {
      val parts = version.split(".")
      val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
      val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
      val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
      major * 10000 + minor * 100 + patch
    } catch (e: Exception) {
      0
    }
  }

  private fun compareVersions(v1: String, v2: String): Int {
    val v1Parts = v1.split(".").map { it.toIntOrNull() ?: 0 }
    val v2Parts = v2.split(".").map { it.toIntOrNull() ?: 0 }

    for (i in 0 until maxOf(v1Parts.size, v2Parts.size)) {
      val v1Part = v1Parts.getOrNull(i) ?: 0
      val v2Part = v2Parts.getOrNull(i) ?: 0

      if (v1Part != v2Part) {
        return v1Part - v2Part
      }
    }

    return 0
  }

  private fun getCurrentAbi(): String {
    return Build.SUPPORTED_ABIS.firstOrNull() ?: "armeabi-v7a"
  }

  private fun cleanChangelog(changelog: String): String {
    return changelog
        .replace("""(?m)^Version\s*Code:.*$""".toRegex(), "")
        .replace("""(?m)^#{1,6}\s*""".toRegex(), "")
        .trim()
  }

  fun openDownloadPage(context: Context, downloadUrl: String) {
    try {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
      context.startActivity(intent)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  fun openReleasesPage(context: Context, repoUrl: String) {
    try {
      val releasesUrl = repoUrl.removeSuffix("/") + "/releases"
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(releasesUrl))
      context.startActivity(intent)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }
}
