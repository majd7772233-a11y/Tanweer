package com.magd.tanweer.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import com.magd.tanweer.ui.theme.TanweerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ar")
class HubActionCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hubActionCardStaysAccessibleAndClickableAtCommonPhoneWidths() {
        val width = mutableStateOf(360.dp)
        var clickCount = 0
        val title = "جدول الحصص الأسبوعي"
        val subtitle = "استعراض جدول الشعبة وتقديم مقترحات تعديل الحصص والتصويت الجماعي عليها"
        val badge = "تفاعلي 📅"

        composeRule.setContent {
            TanweerTheme {
                Box(Modifier.width(width.value)) {
                    HubActionCard(
                        title = title,
                        subtitle = subtitle,
                        badge = badge,
                        badgeColor = Color(0xFF35D6A2),
                        icon = Icons.Default.Schedule,
                        accentColor = Color(0xFF35D6A2),
                        onClick = { clickCount++ },
                        modifier = Modifier.testTag("hub_action_card")
                    )
                }
            }
        }

        listOf(360, 393, 411).forEach { widthDp ->
            composeRule.runOnIdle { width.value = widthDp.dp }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("hub_action_card")
                .assertIsDisplayed()
                .assertWidthIsAtLeast(widthDp.dp)
                .assertHeightIsAtLeast(80.dp)
            composeRule.onNodeWithText(title).assertIsDisplayed()
            composeRule.onNodeWithText(badge).assertIsDisplayed()
        }

        composeRule.onNodeWithTag("hub_action_card").performClick()
        composeRule.runOnIdle {
            assertEquals(1, clickCount)
        }
    }
}
