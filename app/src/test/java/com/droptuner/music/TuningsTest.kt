package com.droptuner.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TuningsTest {
    private fun notes(labels: String) = labels.split(" ").map { Note.parse(it) }

    private fun named(name: String, strings: Int = 6) =
        Tunings.all.single { it.name == name && it.strings.size == strings }.strings

    @Test
    fun `well-known tunings`() {
        assertEquals(notes("E2 A2 D3 G3 B3 E4"), named("E Standard"))
        assertEquals(notes("D#2 G#2 C#3 F#3 A#3 D#4"), named("D#/Eb Standard"))
        assertEquals(notes("D2 A2 D3 G3 B3 E4"), named("Drop D"))
        assertEquals(notes("C#2 G#2 C#3 F#3 A#3 D#4"), named("Drop C#/Db"))
        assertEquals(notes("C2 G2 C3 F3 A3 D4"), named("Drop C"))
        assertEquals(notes("B1 F#2 B2 E3 G#3 C#4"), named("Drop B"))
        assertEquals(notes("A1 E2 A2 D3 F#3 B3"), named("Drop A"))
        assertEquals(notes("B1 E2 A2 D3 G3 B3 E4"), named("B Standard", 7))
        assertEquals(notes("A1 E2 A2 D3 G3 B3 E4"), named("Drop A", 7))
        assertEquals(notes("F#1 B1 E2 A2 D3 G3 B3 E4"), named("F#/Gb Standard", 8))
        assertEquals(notes("E1 B1 E2 A2 D3 G3 B3 E4"), named("Drop E", 8))
        assertEquals(notes("D2 G2 D3 G3 B3 D4"), named("Open G"))
    }

    @Test
    fun `all the 6-string drops`() {
        val drops = Tunings.all.filter { it.family == TuningFamily.DROP && it.strings.size == 6 }.map { it.name }
        assertEquals(
            listOf("Drop D", "Drop C#/Db", "Drop C", "Drop B", "Drop A#/Bb", "Drop A", "Drop G#/Ab", "Drop G", "Drop F#/Gb"),
            drops,
        )
    }

    @Test
    fun `every drop is a fifth on the bottom and standard above`() {
        for (tuning in Tunings.all.filter { it.family == TuningFamily.DROP }) {
            val lowest = tuning.strings.first()
            assertEquals(tuning.name, 7, tuning.strings[1].midi - lowest.midi)
            // Upper strings are the standard tuning one whole tone above the drop note.
            assertEquals(tuning.name, Tunings.standard(lowest + 2, tuning.strings.size).drop(1), tuning.strings.drop(1))
        }
    }

    @Test
    fun `ids are unique and every string is in the detector range`() {
        assertEquals(Tunings.all.size, Tunings.all.map { it.id }.toSet().size)
        assertTrue(Tunings.all.all { it.strings.first().frequency() > 30.0 })
        assertEquals(Tunings.CHROMATIC, Tunings.byId("chromatic"))
        assertTrue(Tunings.CHROMATIC.isChromatic && Tunings.all.none { it.isChromatic })
    }
}
