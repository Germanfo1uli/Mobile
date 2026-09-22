package com.example.labmob

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.compose.setContent
import com.example.labmob.ui.theme.LabMobTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class CourseTabsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun courseSectionsAreAvailableFromMainMenu() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                LabMobTheme(dynamicColor = false) {
                    MainMenuScreen(
                        player = SavedPlayerProfile(
                            id = "123e4567-e89b-42d3-a456-426614174000",
                            fullName = "Тестовый игрок",
                            gender = "Мужской",
                            course = 2,
                            difficulty = 3,
                            birthDateMillis = 946684800000,
                            zodiac = "Телец",
                        ),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("play_locked").assertIsDisplayed().assertIsNotEnabled()

        composeRule.onNodeWithTag("profile_menu_item").performClick()
        composeRule.onNodeWithTag("dossier_screen").assertIsDisplayed()
        composeRule.onNodeWithText("ТЕСТОВЫЙ ИГРОК").assertIsDisplayed()
        composeRule.onNodeWithTag("delete_save_from_profile").performClick()
        composeRule.onNodeWithTag("delete_save_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("ОТМЕНА").performClick()
        composeRule.onNodeWithTag("menu_back").performClick()

        composeRule.onNodeWithTag("rules_menu_item").performClick()
        composeRule.onNodeWithTag("rules_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("menu_back").performClick()

        composeRule.onNodeWithTag("authors_menu_item").performClick()
        composeRule.onNodeWithTag("authors_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("menu_back").performClick()

        composeRule.onNodeWithTag("menu_dashboard")
            .performScrollToNode(hasTestTag("settings_menu_item"))
        composeRule.onNodeWithTag("settings_menu_item").performClick()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("menu_back").performClick()

        composeRule.onNodeWithTag("menu_dashboard")
            .performScrollToNode(hasTestTag("velvet_room_menu_item"))
        composeRule.onNodeWithTag("velvet_room_menu_item").performClick()
        composeRule.onNodeWithTag("velvet_room_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Вы ещё не готовы предстать перед Игорем.").assertIsDisplayed()
    }

    @Test
    fun localSaveCanBeContinuedWithoutRegistration() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                LabMobTheme(dynamicColor = false) {
                    EntryChoiceScreen(
                        savedPlayer = SavedPlayerProfile(
                            "saved-id", "Локальный игрок", "Женский", 3, 4, 946684800000, "Козерог",
                        ),
                        onNewGame = {},
                        onContinue = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("entry_menu").assertIsDisplayed()
        composeRule.onNodeWithText("ЛОКАЛЬНЫЙ ИГРОК").assertIsDisplayed()
        composeRule.onNodeWithTag("continue_game").assertIsDisplayed()
        composeRule.onNodeWithTag("delete_save").performClick()
        composeRule.onNodeWithTag("delete_save_dialog").assertIsDisplayed()
    }
}
