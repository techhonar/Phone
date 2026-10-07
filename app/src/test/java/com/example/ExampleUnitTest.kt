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
        val t9Mom = RustPhoneBridge.textToT9("MOM")
        assertEquals("666", t9Mom)

        val t9John = RustPhoneBridge.textToT9("JOHN")
        assertEquals("5646", t9John)
    }

    @Test
    fun testT9PredictiveSearch() {
        val contacts = listOf(
            Triple("1", "Mom ❤️", "+1 (555) 019-2831"),
            Triple("2", "Alex Johnson", "+1 (555) 234-5678"),
            Triple("3", "Sarah Miller", "+1 (555) 876-5432")
        )

        // Typing 66 matches "Mom"
        val matches = RustPhoneBridge.searchT9("66", contacts)
        assertTrue(matches.isNotEmpty())
        assertEquals("Mom ❤️", matches.first().name)

        // Typing 253 matches "Alex" (2=A, 5=L, 3=E)
        val alexMatches = RustPhoneBridge.searchT9("253", contacts)
        assertTrue(alexMatches.isNotEmpty())
        assertEquals("Alex Johnson", alexMatches.first().name)
    }

    @Test
    fun testPhoneNumberFormatting() {
        val formatted10 = RustPhoneBridge.formatPhoneNumber("5551234567")
        assertEquals("(555) 123-4567", formatted10)

        val formatted11 = RustPhoneBridge.formatPhoneNumber("15551234567")
        assertEquals("+1 (555) 123-4567", formatted11)
    }

    @Test
    fun testDtmfPcmSynthesis() {
        val pcm = RustPhoneBridge.generateDtmfPcm16('5', sampleRate = 8000, durationMs = 100)
        assertNotNull(pcm)
        // 8000 samples/sec * 0.1 sec * 2 bytes/sample = 1600 bytes
        assertEquals(1600, pcm.size)
    }
}
