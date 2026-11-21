package com.nibras.terminal

import android.content.Context
import org.json.JSONObject

/** Private-phone commands ask every time. A previous Android permission is not enough. */
object Sensitive {
    private val names = setOf(
        "sms-list", "sms-inbox", "sms-send",
        "call-log", "contact-list",
        "microphone-record", "camera-info", "camera-photo", "torch",
        "telephony-call",
    )

    fun allow(context: Context, command: String): Boolean {
        val bare = command.removePrefix("nibras-").removePrefix("termux-")
        if (bare !in names) return true
        val raw = PromptActivity.ask(
            context,
            "dialog",
            listOf(
                "confirm",
                "-t",
                "Nibras",
                "-i",
                "Allow $bare? It uses private phone data: camera, microphone, SMS, contacts or the call log.",
            ),
        )
        return JSONObject(raw).optString("text") == "yes"
    }
}
