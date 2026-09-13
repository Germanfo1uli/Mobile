package com.example.labmob

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class CourseTabsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun courseSectionsAreAvailableFromTabs() {
        composeRule.onNodeWithTag("intro_message").performClick()
        composeRule.onNodeWithTag("intro_message").performClick()

        composeRule.onNodeWithText("ПРАВИЛА").performClick()
        composeRule.onNodeWithTag("rules_screen").assertIsDisplayed()

        composeRule.onNodeWithText("АВТОРЫ").performClick()
        composeRule.onNodeWithTag("authors_screen").assertIsDisplayed()

        composeRule.onNodeWithText("НАСТРОЙКИ").performClick()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        composeRule.onNodeWithText("ДОСЬЕ").performClick()
        composeRule.onNodeWithTag("full_name").assertIsDisplayed()
    }
}
