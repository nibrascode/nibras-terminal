package com.nibras.terminal

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.Semaphore

/** Private-phone commands ask every time. A previous Android permission is not enough. */
object Sensitive {
    val names = setOf(
        "sms-list", "sms-inbox", "sms-send",
        "call-log", "contact-list",
        "microphone-record", "camera-info", "camera-photo", "torch",
        "telephony-call",
        "location", "clipboard-get", "notification-list",
    )

    private val gate = Semaphore(1)

    fun allow(context: Context, command: String, detail: String = ""): Boolean {
        val bare = command.removePrefix("nibras-").removePrefix("termux-")
        if (bare !in names) return true
        if (GuestMode.on(context)) return false
        if (!gate.tryAcquire()) return false
        return try {
            val raw = PromptActivity.ask(context, "sensitive", listOf(bare, detail), timeoutSec = 30)
            JSONObject(raw).optString("text") == "yes"
        } finally {
            gate.release()
        }
    }
}
