package com.rhythmcache.payloaddumper.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.*

object LocaleManager {
  private const val PREF_LANGUAGE = "app_language"

  fun setLocale(context: Context, languageCode: String): Context {
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    prefs.edit().putString(PREF_LANGUAGE, languageCode).apply()
    return updateResources(context, languageCode)
  }

  fun getLanguage(context: Context): String {
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    return prefs.getString(PREF_LANGUAGE, "system") ?: "system"
  }

  fun updateResources(context: Context, languageCode: String): Context {
    val locale =
        if (languageCode == "system") {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
          } else {
            @Suppress("DEPRECATION") context.resources.configuration.locale
          }
        } else {
          Locale.forLanguageTag(languageCode)
        }

    Locale.setDefault(locale)

    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      context.createConfigurationContext(config)
    } else {
      @Suppress("DEPRECATION")
      context.resources.updateConfiguration(config, context.resources.displayMetrics)
      context
    }
  }

  data class Language(val code: String, val nameResId: Int)

  fun getAvailableLanguages(): List<Language> =
      listOf(
          Language("system", com.rhythmcache.payloaddumper.R.string.language_system),
          Language("en", com.rhythmcache.payloaddumper.R.string.language_english),
          Language("hi", com.rhythmcache.payloaddumper.R.string.language_hindi))
}
