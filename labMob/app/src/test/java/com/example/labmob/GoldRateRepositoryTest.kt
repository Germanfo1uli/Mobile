package com.example.labmob

import org.junit.Assert.assertEquals
import org.junit.Test

class GoldRateRepositoryTest {
    @Test
    fun parsesGoldRateReturnedByBackend() {
        val rate = GoldRateRepository().parseBackendGold(
            """{"gold":{"rublesPerGram":11568.93,"date":"26.09.2026"}}""",
        )

        assertEquals(11568.93, rate.rublesPerGram, 0.001)
        assertEquals("26.09.2026", rate.date)
    }
}
