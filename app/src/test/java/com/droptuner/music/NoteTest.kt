package com.droptuner.music

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteTest {
    @Test
    fun parse() {
        assertEquals(69, Note.parse("A4").midi)
        assertEquals(40, Note.parse("E2").midi)
        assertEquals(30, Note.parse("F#1").midi)
        assertEquals(Note.parse("C#3"), Note.parse("Db3"))
    }

    @Test
    fun frequency() {
        assertEquals(440.0, Note(69).frequency(), 1e-9)
        assertEquals(82.4069, Note.parse("E2").frequency(), 1e-3)
        assertEquals(432.0, Note(69).frequency(a4 = 432.0), 1e-9)
    }

    @Test
    fun names() {
        assertEquals("C#3", Note(49).label)
        assertEquals("C1", Note(24).label)
        assertEquals("D#/Eb", Note(39).nameWithFlat)
        assertEquals("E", Note(40).nameWithFlat)
    }

    @Test
    fun `nearest note and cents`() {
        assertEquals(Note.parse("E4"), Note.nearest(331.0))
        assertEquals(Note.parse("A4"), Note.nearest(432.0, a4 = 432.0))
        assertEquals(1200.0, cents(880.0, 440.0), 1e-9)
        assertEquals(-100.0, cents(Note.parse("D#2").frequency(), Note.parse("E2").frequency()), 1e-9)
    }
}
