package com.magd.tanweer.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.magd.tanweer.ui.theme.TanweerTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ar")
class SettingsToggleAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun notificationSwitchesExposeTheirTitleDescriptionAndCurrentState() {
        data class NotificationToggle(
            val icon: androidx.compose.ui.graphics.vector.ImageVector,
            val title: String,
            val description: String
        )

        val toggles = listOf(
            NotificationToggle(
                Icons.Default.Assignment,
                "تذكيرات تسليم الواجبات",
                "تنبيه قبل موعد الاستحقاق"
            ),
            NotificationToggle(
                Icons.Default.CalendarToday,
                "تذكير جدول الحصص الصباحي",
                "إشعار صباحي بحصص اليوم الدراسي"
            ),
            NotificationToggle(
                Icons.Default.Alarm,
                "العد التنازلي للاختبارات",
                "تذكير بمواعيد الاختبارات الشهرية والنهائية"
            )
        )
        val checkedStates = mutableStateListOf(false, false, false)

        composeRule.setContent {
            TanweerTheme {
                Column {
                    toggles.forEachIndexed { index, toggle ->
                        SettingsToggleRow(
                            icon = toggle.icon,
                            title = toggle.title,
                            subtitle = toggle.description,
                            checked = checkedStates[index],
                            onCheckedChange = { checkedStates[index] = it }
                        )
                    }
                }
            }
        }

        toggles.forEach { toggle ->
            val label = "${toggle.title}. ${toggle.description}"
            val switch = composeRule.onNodeWithContentDescription(label)
            switch.assertIsDisplayed()
                .assertIsOff()
                .assert(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.StateDescription,
                        "غير مفعّل"
                    )
                )
        }

        val homeworkSwitch = composeRule.onNodeWithContentDescription(
            "تذكيرات تسليم الواجبات. تنبيه قبل موعد الاستحقاق"
        )
        homeworkSwitch.performClick()
        homeworkSwitch.assertIsOn()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "مفعّل"
                )
            )

        composeRule.runOnIdle {
            assertTrue(checkedStates[0])
            assertFalse(checkedStates[1])
            assertFalse(checkedStates[2])
        }
    }
}
