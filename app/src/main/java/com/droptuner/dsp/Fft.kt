package com.droptuner.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** In-place iterative radix-2 FFT over separate real/imaginary arrays of a fixed power-of-two [size]. */
class Fft(val size: Int) {
    private val bits = Integer.numberOfTrailingZeros(size)
    private val cosTable = DoubleArray(size / 2) { cos(2 * PI * it / size) }
    private val sinTable = DoubleArray(size / 2) { sin(2 * PI * it / size) }
    private val reversed = IntArray(size) { Integer.reverse(it) ushr (32 - bits) }

    init {
        require(size >= 2 && size and (size - 1) == 0) { "FFT size must be a power of two, got $size" }
    }

    /** Forward transform uses e^(−2πi·kn/N); [inverse] also divides by N. */
    fun transform(re: DoubleArray, im: DoubleArray, inverse: Boolean = false) {
        for (i in 0 until size) {
            val j = reversed[i]
            if (j > i) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }
        val sign = if (inverse) 1.0 else -1.0
        var length = 2
        while (length <= size) {
            val half = length / 2
            val step = size / length
            for (start in 0 until size step length) {
                for (k in 0 until half) {
                    val c = cosTable[k * step]
                    val s = sign * sinTable[k * step]
                    val a = start + k
                    val b = a + half
                    val tRe = re[b] * c - im[b] * s
                    val tIm = re[b] * s + im[b] * c
                    re[b] = re[a] - tRe
                    im[b] = im[a] - tIm
                    re[a] += tRe
                    im[a] += tIm
                }
            }
            length *= 2
        }
        if (inverse) {
            for (i in 0 until size) {
                re[i] /= size
                im[i] /= size
            }
        }
    }
}
