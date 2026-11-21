package com.nibras.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlusRulesTest {
    private val sensitive = setOf("sms-send", "camera-photo", "clipboard-get")

    @Test
    fun sentenceBecomesOnlyKnownCommands() {
        assertEquals(listOf("install debian", "run hello.py"), PlusRules.parsePhrase("debian qur, sonra hello.py işlət"))
        assertEquals(listOf("install python"), PlusRules.parsePhrase("install python"))
        assertEquals(listOf("theme dracula"), PlusRules.parsePhrase("mövzu dracula"))
        assertEquals(listOf("help"), PlusRules.parsePhrase("kömək"))
        assertEquals(listOf("run src/main.py"), PlusRules.parsePhrase("run src/main.py"))
    }

    @Test
    fun unknownOrUnsafeSentenceDoesNothing() {
        assertNull(PlusRules.parsePhrase("rm -rf /"))
        assertNull(PlusRules.parsePhrase("sms-send 050"))
        assertNull(PlusRules.parsePhrase("install foo; rm -rf /"))
        assertNull(PlusRules.parsePhrase("qur ../../etc"))
        assertNull(PlusRules.parsePhrase("run -rf"))
        assertNull(PlusRules.parsePhrase(""))
    }

    @Test
    fun automationSkipsPrivateCommandsAndHonoursTheList() {
        assertFalse(PlusRules.allowAutomatic("nibras-sms-send 1", null, sensitive))
        assertTrue(PlusRules.allowAutomatic("backup home", null, sensitive))
        assertFalse(PlusRules.allowAutomatic("backup home", emptyList(), sensitive))
        assertTrue(PlusRules.allowAutomatic("backup home", listOf("backup"), sensitive))
        assertFalse(PlusRules.allowAutomatic("camera-photo", listOf("camera-photo"), sensitive))
    }
}
