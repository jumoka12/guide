package com.ampgames.vidsaver.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Base64LiteTest {

    @Test
    fun `decodes standard base64 with and without padding`() {
        assertEquals("hello", Base64Lite.decodeToString("aGVsbG8="))
        assertEquals("hello", Base64Lite.decodeToString("aGVsbG8"))
        assertEquals("hi", Base64Lite.decodeToString("aGk="))
    }

    @Test
    fun `decodes the url-safe alphabet`() {
        // 0xFB 0xFF encodes as "+/8=" standard and "-_8=" URL-safe.
        assertEquals(listOf(0xFB.toByte(), 0xFF.toByte()), Base64Lite.decode("-_8=")!!.toList())
        assertEquals(listOf(0xFB.toByte(), 0xFF.toByte()), Base64Lite.decode("+/8=")!!.toList())
    }

    @Test
    fun `garbage returns null instead of throwing`() {
        assertNull(Base64Lite.decode("not base64!"))
        assertNull(Base64Lite.decode("é"))
    }

    @Test
    fun `round trips against the JDK encoder`() {
        val text = """{"vencode_tag":"dash_h264-basic-gen2_720p","xpv_asset_id":1234567890}"""
        val encoded = java.util.Base64.getEncoder().encodeToString(text.toByteArray())
        assertEquals(text, Base64Lite.decodeToString(encoded))
    }
}
