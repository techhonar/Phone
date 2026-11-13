package com.example

import com.example.rust.RustPhoneBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testT9CharacterMapping() {
        assertEquals('2', RustPhoneBridge.charToT9('A'))
        assertEquals('2', RustPhoneBridge.charToT9('b'))
        assertEquals('3', RustPhoneBridge.charToT9('D'))
        assertEquals('4', RustPhoneBridge.charToT9('G'))
        assertEquals('5', RustPhoneBridge.charToT9('J'))
        assertEquals('6', RustPhoneBridge.charToT9('M'))
        assertEquals('7', RustPhoneBridge.charToT9('P'))
        assertEquals('8', RustPhoneBridge.charToT9('T'))
        assertEquals('9', RustPhoneBridge.charToT9('W'))
        assertEquals('0', RustPhoneBridge.charToT9('0'))
    }

    @Test
    fun testTextToT9Conversion() {
        assertEquals("666", RustPhoneBridge.textToT9("MOM"))
        assertEquals("5646", RustPhoneBridge.textToT9("JOHN"))
    }

    @Test
    fun testT9PredictiveSearchWithOffsetsAndInitials() {
        val contacts = listOf(
            Triple("1", "Mom", "+1 (555) 019-2831"),
            Triple("2", "Alex Johnson", "+1 (555) 234-5678"),
            Triple("3", "Sarah Miller", "+1 (555) 876-5432"),
            Triple("4", "Aaron James", "+1 (555) 111-2222")
        )

        // Substring match
        val matches = RustPhoneBridge.searchT9("66", contacts)
        assertTrue(matches.isNotEmpty())
        assertEquals("Mom", matches.first().name)

        // Offset spanning across space ("Alex Johnson": 'x' at index 3, 'J' at index 5 -> "95")
        val crossSpace = RustPhoneBridge.searchT9("95", contacts)
        val alexCross = crossSpace.firstOrNull { it.contactId == "2" }
        assertNotNull(alexCross)
        assertEquals(3, alexCross!!.matchStart)
        assertEquals(3, alexCross.matchLength) // "x J"

        // Word initials match ("25" -> A(2), J(5) in "Aaron James")
        val initials = RustPhoneBridge.searchT9("25", contacts)
        assertTrue(initials.any { it.contactId == "4" && it.matchType == "initials" })
    }

    @Test
    fun testPhoneNumberFormattingAndUssdPreservation() {
        assertEquals("(555) 123-4567", RustPhoneBridge.formatPhoneNumber("5551234567"))
        assertEquals("+1 (555) 123-4567", RustPhoneBridge.formatPhoneNumber("15551234567"))
        // USSD/MMI codes must remain untouched
        assertEquals("*#06#", RustPhoneBridge.formatPhoneNumber("*#06#"))
        assertEquals("*86", RustPhoneBridge.formatPhoneNumber("*86"))
    }

    @Test
    fun testNormalizeForMatch() {
        assertEquals("5552345678", RustPhoneBridge.normalizeForMatch("+1 (555) 234-5678"))
        assertEquals("5552345678", RustPhoneBridge.normalizeForMatch("555-234-5678"))
    }

    @Test
    fun testDtmfPcmSynthesis() {
        val pcm = RustPhoneBridge.generateDtmfPcm16('5', sampleRate = 8000, durationMs = 100)
        assertNotNull(pcm)
        assertEquals(1600, pcm.size)
    }

    @Test
    fun testTelephonyAndConnectionDefaults() {
        val status = com.example.telecom.TelephonyStatus()
        assertEquals("Idle", status.osCallStateLabel)
        assertEquals(false, status.isSimReady)
        assertTrue(com.example.telecom.PhoneConnectionService.ACCOUNT_ID.isNotBlank())
    }
}
