package com.nibras.terminal

/** Store flavor omits SMS, call log, Kali, APK install, all-files access and the notification listener. */
object Channel {
    val store: Boolean = BuildConfig.STORE

    fun blockedEnv(id: String): Boolean {
        if (!store) return false
        val x = id.lowercase()
        return "kali" in x || "nethunter" in x
    }

    fun unavailable(what: String): String =
        "ERR\n$what is not included in the store version of Nibras Terminal."
}
