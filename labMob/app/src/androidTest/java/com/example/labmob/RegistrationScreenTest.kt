package com.example.labmob

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegistrationScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun quickDateAndRegistrationProduceExpectedProfile() {
        composeRule.onNodeWithText("ЕЩЁ НЕ КОНЕЦ.").assertIsDisplayed()
        composeRule.onNodeWithTag("intro_message").performClick()
        composeRule.onNodeWithText("ВЫБОРА НЕТ.").assertIsDisplayed()
        composeRule.onNodeWithTag("intro_message").performClick()

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

        composeRule.onNodeWithText("ДОСЬЕ ПРИНЯТО")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("player_result")
            .assertTextContains("Иван Иванов", substring = true)
    }
}
