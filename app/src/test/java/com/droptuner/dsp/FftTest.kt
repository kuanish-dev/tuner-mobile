package com.droptuner.dsp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.random.Random

class FftTest {
    @Test
    fun `cosine lands in its bin`() {
        val n = 64
        val re = DoubleArray(n) { cos(2 * PI * 5 * it / n) }
        val im = DoubleArray(n)
        Fft(n).transform(re, im)
        assertEquals(n / 2.0, re[5], 1e-9)
        assertEquals(n / 2.0, re[n - 5], 1e-9)
        assertEquals(0.0, re[4], 1e-9)
    }

    @Test
    fun `inverse undoes forward`() {
        val n = 1024
        val random = Random(3)
        val original = DoubleArray(n) { random.nextDouble() - 0.5 }
        val re = original.copyOf()
        val im = DoubleArray(n)
        val fft = Fft(n)
        fft.transform(re, im)
        fft.transform(re, im, inverse = true)
        assertArrayEquals(original, re, 1e-12)
        assertArrayEquals(DoubleArray(n), im, 1e-12)
    }
}
