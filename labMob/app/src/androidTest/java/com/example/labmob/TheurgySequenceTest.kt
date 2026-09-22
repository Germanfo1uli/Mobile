package com.example.labmob

import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TheurgySequenceTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun cutsceneAndVideoSurfaceCanBeShown() {
        val sampledAt = SystemClock.elapsedRealtime()
        val round = HuntRound(
            id = "test-round", finished = false, remainingMilliseconds = 30_000,
            score = 0, hits = 0, misses = 0, targets = emptyList(), bonus = null,
            sampledAtElapsedMs = sampledAt, freezeActive = false,
            theurgyRemainingMilliseconds = 3_800, bonusesCollected = 1,
        )
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent { TheurgySequence(round, SystemClock.elapsedRealtime()) }
        }
        composeRule.onNodeWithTag("theurgy_cutscene").assertExists()
        composeRule.waitForIdle()
    }
}
