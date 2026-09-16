package com.example.labmob

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.labmob.ui.theme.LabMobTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegistrationScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun quickDateAndRegistrationProduceExpectedProfile() {
        var submittedProfile: PlayerProfile? = null
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                LabMobTheme(dynamicColor = false) {
                    RegistrationScreen(onProfileSubmitted = { submittedProfile = it })
                }
            }
        }

        composeRule.onNodeWithTag("full_name")
            .performTextInput("Иван Иванов")

        composeRule.onNodeWithTag("quick_birth_date")
            .performScrollTo()
            .performClick()
            .performTextInput("17052004")

        composeRule.onNodeWithTag("selected_birth_date").assertTextEquals("17.05.2004")
        composeRule.onNodeWithText("ЗНАК: ТЕЛЕЦ").assertIsDisplayed()

        composeRule.onNodeWithTag("submit_player")
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle {
            assertNotNull(submittedProfile)
            assertEquals("Иван Иванов", submittedProfile?.fullName)
            assertEquals("Телец", submittedProfile?.zodiac?.title)
        }
    }
}
