package com.nibras.terminal.core

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/** The ABI whose native libraries Android actually installed for this process. */
object DeviceAbi {
    val current: String by lazy {
        if (android.os.Process.is64Bit()) "arm64-v8a" else "armeabi-v7a"
    }
    val is32: Boolean get() = current == "armeabi-v7a"
    val label: String get() = if (is32) "arm32 (armv7)" else "arm64"
    fun matches(abi: String) = abi == "any" || abi == current
    val deviceAbis: String get() = Build.SUPPORTED_ABIS.joinToString(",")
}

class Prefs(context: Context) {
    val sp: SharedPreferences = context.applicationContext.getSharedPreferences("nibras", Context.MODE_PRIVATE)

    var fontSize: Int
        get() = sp.getInt("fontSize", 0)
        set(v) = sp.edit().putInt("fontSize", v).apply()
    var theme: String
        get() = sp.getString("theme", "nibras") ?: "nibras"
        set(v) = sp.edit().putString("theme", v).apply()
    var language: String
        get() = sp.getString("language", "") ?: ""
        set(v) = sp.edit().putString("language", v).apply()
    var extraKeys: Boolean
        get() = sp.getBoolean("extraKeys", true)
        set(v) = sp.edit().putBoolean("extraKeys", v).apply()
    var keepScreenOn: Boolean
        get() = sp.getBoolean("keepScreenOn", false)
        set(v) = sp.edit().putBoolean("keepScreenOn", v).apply()
    var bellVibrate: Boolean
        get() = sp.getBoolean("bellVibrate", true)
        set(v) = sp.edit().putBoolean("bellVibrate", v).apply()
    var backIsEscape: Boolean
        get() = sp.getBoolean("backIsEscape", false)
        set(v) = sp.edit().putBoolean("backIsEscape", v).apply()
    var fullscreen: Boolean
        get() = sp.getBoolean("fullscreen", false)
        set(v) = sp.edit().putBoolean("fullscreen", v).apply()
    var cursorStyle: Int
        get() = sp.getInt("cursorStyle", 0)
        set(v) = sp.edit().putInt("cursorStyle", v).apply()
    var cursorBlink: Boolean
        get() = sp.getBoolean("cursorBlink", true)
        set(v) = sp.edit().putBoolean("cursorBlink", v).apply()
    var defaultEnv: String
        get() = sp.getString("defaultEnv", "rootfs-alpine") ?: "rootfs-alpine"
        set(v) = sp.edit().putString("defaultEnv", v).apply()
    var volumeKeys: Boolean
        get() = sp.getBoolean("volumeKeys", true)
        set(v) = sp.edit().putBoolean("volumeKeys", v).apply()
}

object Lang {
    val supported = listOf("", "az", "tr", "en", "ru")

    /** Effective UI language code (az / tr / ru / en). */
    fun current(context: Context): String {
        val code = Prefs(context).language.ifBlank { context.resources.configuration.locales[0].language }
        return if (code in listOf("az", "tr", "ru")) code else "en"
    }

    fun wrap(base: Context): Context {
        val code = Prefs(base).language
        if (code.isBlank()) return base
        val locale = Locale.forLanguageTag(code)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
