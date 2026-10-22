package com.droptuner.tuner

import com.droptuner.music.Note
import com.droptuner.music.Tunings
import com.droptuner.music.cents
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.pow

class StringMatcherTest {
    private val standard = Tunings.byId("std6-40")!!
    private val dropD = Tunings.byId("drop6-38")!!
    private val matcher = StringMatcher()

    private fun hz(label: String) = Note.parse(label).frequency()

    @Test
    fun `finds the open string being played`() {
        standard.strings.forEachIndexed { i, note ->
            val reading = matcher.match(note.frequency() * 1.002, standard, 440.0, locked = null)
            assertEquals(i, reading.stringIndex)
            assertEquals(3.46, reading.cents, 0.01)
        }
    }

    @Test
    fun `low string heard an octave up still maps to the low string`() {
        val reading = matcher.match(hz("E3"), standard, 440.0, locked = null)
        assertEquals(0, reading.stringIndex)
        assertEquals(0.0, reading.cents, 1e-9)
        assertEquals(Note.parse("E2"), reading.heard)
    }

    @Test
    fun `E on the low string of Drop D is a whole tone sharp`() {
        val reading = matcher.match(hz("E2"), dropD, 440.0, locked = null)
        assertEquals(0, reading.stringIndex)
        assertEquals(200.0, reading.cents, 1e-9)
    }

    @Test
    fun `locked string is measured against that string only`() {
        val reading = matcher.match(150.0, standard, 440.0, locked = 2)
        assertEquals(2, reading.stringIndex)
        assertEquals(cents(150.0, hz("D3")), reading.cents, 1e-9)
    }

    @Test
    fun `reference pitch moves the targets`() {
        val reading = matcher.match(108.0, standard, 432.0, locked = null)
        assertEquals(1, reading.stringIndex)
        assertEquals(0.0, reading.cents, 1e-9)
    }

    @Test
    fun `ambiguous pitch keeps the current string`() {
        matcher.match(hz("A2"), standard, 440.0, locked = null)
        // 260 cents above A2 is 240 below D3: closer to D3, but not convincingly.
        val reading = matcher.match(hz("A2") * 2.0.pow(260 / 1200.0), standard, 440.0, locked = null)
        assertEquals(1, reading.stringIndex)
    }

    @Test
    fun `chromatic mode measures against the nearest note`() {
        val reading = matcher.match(hz("C#5") * 1.001, Tunings.CHROMATIC, 440.0, locked = null)
        assertEquals(null, reading.stringIndex)
        assertEquals(Note.parse("C#5"), reading.target)
        assertEquals(1.73, reading.cents, 0.01)
    }
}
