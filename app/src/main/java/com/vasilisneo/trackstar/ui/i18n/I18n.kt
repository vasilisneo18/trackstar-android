package com.vasilisneo.trackstar.ui.i18n

// In-app localization, shared in spirit with iOS's String Catalog: UI strings stay in English in the
// code and are looked up by their English text in a per-language dictionary
// (assets/i18n/<lang>.json, generated from the same translations as iOS). Unknown strings — user
// data like names and notes — come back unchanged.
//
// The current language is Compose state, so every `Text` / `tr()` read during composition
// recomposes the moment the user switches language in App Settings — no restart needed.
// "System" follows the phone (and Android 13+'s per-app language in system Settings).

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.util.Locale

enum class AppLanguage(val code: String, val displayName: String) {
    SYSTEM("system", "System"),
    EN("en", "English"),
    EL("el", "Ελληνικά"),
}

object I18n {
    private const val PREFS = "trackstar_prefs"
    private const val KEY = "appLanguage"

    private lateinit var appContext: Context
    private val systemLocale: Locale = Locale.getDefault()
    private val dictionaries = mutableMapOf<String, Map<String, String>>()
    private val lowercaseIndex = mutableMapOf<String, Map<String, String>>()

    /** The language picked in App Settings (SYSTEM = follow the phone). */
    var selected by mutableStateOf(AppLanguage.SYSTEM)
        private set

    /** Language code in effect: "en" or "el". */
    val effectiveCode: String
        get() = when (selected) {
            AppLanguage.SYSTEM -> if (systemLocale.language == "el") "el" else "en"
            else -> selected.code
        }

    /** Locale for dates/numbers — the phone's region with the app's language. */
    val locale: Locale
        get() = if (selected == AppLanguage.SYSTEM) systemLocale
                else Locale.Builder().setLocale(systemLocale).setLanguage(effectiveCode).build()

    fun init(context: Context) {
        appContext = context.applicationContext
        val saved = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        selected = AppLanguage.entries.firstOrNull { it.code == saved } ?: AppLanguage.SYSTEM
        Locale.setDefault(locale)
    }

    fun select(language: AppLanguage) {
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language.code).apply()
        selected = language
        Locale.setDefault(locale)
    }

    private fun dictionary(code: String): Map<String, String> = dictionaries.getOrPut(code) {
        if (code == "en" || !::appContext.isInitialized) return@getOrPut emptyMap()
        runCatching {
            val json = JSONObject(appContext.assets.open("i18n/$code.json").bufferedReader().use { it.readText() })
            buildMap { json.keys().forEach { put(it, json.getString(it)) } }
        }.getOrDefault(emptyMap())
    }

    fun lookup(english: String): String {
        val code = effectiveCode // reads Compose state → callers recompose on change
        if (code == "en" || english.isEmpty()) return english
        val dict = dictionary(code)
        dict[english]?.let { return it }
        // Case-insensitive fallback: section headers are uppercased ("GENERAL"), API values are
        // lowercase ("monday"). Match the original's casing in the result.
        val index = lowercaseIndex.getOrPut(code) { dict.entries.associate { it.key.lowercase() to it.value } }
        val hit = index[english.lowercase()] ?: return english
        return when {
            english == english.uppercase() -> hit.uppercase(locale)
            english.first().isLowerCase() -> hit.replaceFirstChar { it.lowercase(locale) }
            else -> hit
        }
    }
}

/** Localized UI string, looked up by its English text. */
fun tr(english: String): String = I18n.lookup(english)

/**
 * Localized format string: `trf("%s exercises", n)`. The template (with `%s` / `%1\$s` placeholders)
 * is what's translated; arguments are inserted afterwards.
 */
fun trf(template: String, vararg args: Any?): String =
    runCatching { String.format(I18n.locale, I18n.lookup(template), *args) }
        .getOrElse { String.format(Locale.US, template, *args) }
