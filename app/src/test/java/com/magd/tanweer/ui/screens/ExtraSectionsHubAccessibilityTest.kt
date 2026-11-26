package com.magd.tanweer.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.magd.tanweer.ui.theme.TanweerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ar")
class ExtraSectionsHubAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun categoryRailShowsScrollCueAndKeepsTheRolesFilterReachableAtPhoneWidths() {
        val width = mutableStateOf(360.dp)
        val selectedCategory = mutableStateOf("ALL")
        val categories = listOf(
            "ALL" to "الكل ✨",
            "ACADEMIC" to "المناهج والتعلم 📚",
            "COMMUNITY" to "المجتمع والحوكمة 🗳️",
            "ROLES" to "مراكز الإدارة 🛡️",
            "SETTINGS" to "النظام والإعدادات ⚙️"
        )

        composeRule.setContent {
            TanweerTheme {
                Box(Modifier.width(width.value)) {
                    HubCategoryFilterRow(
                        categories = categories,
                        selectedCategoryFilter = selectedCategory.value,
                        onCategorySelected = { selectedCategory.value = it }
                    )
                }
            }
        }

        listOf(360, 393, 411).forEach { widthDp ->
            composeRule.runOnIdle {
                width.value = widthDp.dp
                selectedCategory.value = "ALL"
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("hub_category_scroll_hint").assertIsDisplayed()
            composeRule.onNodeWithTag("hub_category_filter_row").performScrollToIndex(3)
            composeRule.onNodeWithTag("hub_category_chip_ROLES")
                .assertIsDisplayed()
                .performClick()
                .assertIsSelected()
            composeRule.runOnIdle { assertEquals("ROLES", selectedCategory.value) }
            composeRule.onNodeWithTag("hub_category_filter_row").performScrollToIndex(0)
        }
    }

    @Test
    fun hubSearchEmptyStateExplainsNoMatchesAndClearsTheQuery() {
        var clearCount = 0
        composeRule.setContent {
            TanweerTheme {
                Box(Modifier.width(360.dp)) {
                    HubSearchEmptyState(onClear = { clearCount++ })
                }
            }
        }

        composeRule.onNodeWithTag("hub_search_empty_state").assertIsDisplayed()
        composeRule.onNodeWithText("لا توجد أقسام تطابق بحثك").assertIsDisplayed()
        composeRule.onNodeWithTag("hub_search_clear_action").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, clearCount) }
    }

    @Test
    fun lockedStudentRoleCardExplainsTheRequirementAndOnlyOpensTheProfileAction() {
        var profileOpenCount = 0
        composeRule.setContent {
            TanweerTheme {
                Box(Modifier.width(360.dp)) {
                    LockedRoleCenterCard(
                        onOpenProfile = { profileOpenCount++ },
                        modifier = Modifier.testTag("locked_role_center_card")
                    )
                }
            }
        }
        composeRule.onNodeWithTag("locked_role_center_card").assertIsDisplayed()
        composeRule.onNodeWithText("مراكز الرتبة غير متاحة لرتبة الطالب").assertIsDisplayed()
        composeRule.onNodeWithText("يتطلب رتبة مسؤول شعبة أو أعلى").assertIsDisplayed()
        composeRule.onNodeWithText(
            "تظهر هذه المراكز بعد اعتماد رتبة مسؤول شعبة أو أستاذ معتمد أو مدير المدرسة. مساهمتك التعليمية ومشاركتك في الشعبة متاحتان دون هذه الرتبة."
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("hub_role_locked_upgrade_button").performClick()
        composeRule.runOnIdle { assertEquals(1, profileOpenCount) }
    }
}
