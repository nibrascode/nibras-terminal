package com.nibras.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** A new private phone command must be added here or the test fails. */
class SensitiveListTest {
    private val required = setOf(
        "sms-list", "sms-inbox", "sms-send",
        "call-log", "contact-list",
        "microphone-record", "camera-info", "camera-photo", "torch",
        "telephony-call",
        "location", "clipboard-get", "notification-list",
    )

    @Test
    fun privateCommandsStayBehindThePrompt() {
        assertEquals(required, Sensitive.names)
        val api = File("src/main/java/com/nibras/terminal/TermuxApi.kt").readText()
        val bridge = File("src/main/java/com/nibras/terminal/PhoneBridge.kt").readText()
        for (name in required) {
            assertTrue("$name is not dispatched", api.contains("\"$name\"") || bridge.contains("\"nibras-$name\""))
        }
    }
}
