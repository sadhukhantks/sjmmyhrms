package com.example.myhrms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScaleReadingParseTest {
    @Test
    fun parsesScaleReadings() {
        assertEquals("452.5", SpgDoffpicActivity.parseReading("452.5 kg"))
        assertEquals("452.5", SpgDoffpicActivity.parseReading("4 5 2 . 5"))   // fragmented 7-seg
        assertEquals("8452.5", SpgDoffpicActivity.parseReading("8.452.5"))    // stray dot collapsed
        assertEquals("452", SpgDoffpicActivity.parseReading("452."))
        assertEquals("452.5", SpgDoffpicActivity.parseReading("452.5."))     // glowing kg lamp -> stray dot
        assertEquals("9.5", SpgDoffpicActivity.parseReading("9.5"))
        assertEquals("1526.2", SpgDoffpicActivity.parseReading("1526.2"))
        assertEquals("120", SpgDoffpicActivity.parseReading("120"))
        assertNull(SpgDoffpicActivity.parseReading("2"))       // lone-digit fragment
        assertNull(SpgDoffpicActivity.parseReading("25"))      // two-digit fragment
        assertNull(SpgDoffpicActivity.parseReading("52568"))   // implausibly heavy
        assertNull(SpgDoffpicActivity.parseReading("0.0"))
        assertNull(SpgDoffpicActivity.parseReading(""))
        assertNull(SpgDoffpicActivity.parseReading("ERR"))
        assertNull(SpgDoffpicActivity.parseReading("8888.8"))  // idle/ghost pattern
        assertNull(SpgDoffpicActivity.parseReading("888"))     // idle/ghost pattern
        assertEquals("988.8", SpgDoffpicActivity.parseReading("988.8"))
    }
}
