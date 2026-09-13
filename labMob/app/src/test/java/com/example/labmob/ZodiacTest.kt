package com.example.labmob

import org.junit.Assert.assertEquals
import org.junit.Test

class ZodiacTest {
    @Test
    fun zodiacBoundariesAreCalculatedCorrectly() {
        assertEquals(ZodiacSign.CAPRICORN, zodiacFor(1, 19))
        assertEquals(ZodiacSign.AQUARIUS, zodiacFor(1, 20))
        assertEquals(ZodiacSign.PISCES, zodiacFor(3, 20))
        assertEquals(ZodiacSign.ARIES, zodiacFor(3, 21))
        assertEquals(ZodiacSign.VIRGO, zodiacFor(9, 22))
        assertEquals(ZodiacSign.LIBRA, zodiacFor(9, 23))
        assertEquals(ZodiacSign.SAGITTARIUS, zodiacFor(12, 21))
        assertEquals(ZodiacSign.CAPRICORN, zodiacFor(12, 22))
    }
}
