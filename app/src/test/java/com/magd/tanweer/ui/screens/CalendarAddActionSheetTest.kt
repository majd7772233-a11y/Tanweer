package com.magd.tanweer.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
class CalendarAddActionSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allSchoolActionsIncludingHolidayRemainReachableAndClickableOnCompactViewport() {
        val clicked = mutableListOf<String>()

        composeRule.setContent {
            TanweerTheme {
                Box(Modifier.width(360.dp).height(340.dp)) {
                    CalendarAddActionSheetContent(
                        dateLabel = "25 نوفمبر 2026",
                        canDeclareSchoolHoliday = true,
                        onAddLesson = { clicked += "lesson" },
                        onAddHomework = { clicked += "homework" },
                        onAddExam = { clicked += "exam" },
                        onAddEvent = { clicked += "event" },
                        onDeclareSchoolHoliday = { clicked += "holiday" },
                        modifier = Modifier.testTag("calendar_add_action_list")
                    )
                }
            }
        }

        val actionTags = listOf(
            "calendar_add_lesson_action" to "lesson",
            "calendar_add_homework_action" to "homework",
            "calendar_add_exam_action" to "exam",
            "calendar_add_event_action" to "event",
            "calendar_add_holiday_action" to "holiday"
        )
        actionTags.forEach { (tag, action) ->
            composeRule.onNodeWithTag(tag)
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
        }

        composeRule.runOnIdle {
            assertEquals(actionTags.map { it.second }, clicked.toList())
        }
    }

    @Test
    fun schoolHolidayActionIsNotExposedToOtherRolesButEventsRemainScrollable() {
        composeRule.setContent {
            TanweerTheme {
                Box(Modifier.width(360.dp).height(340.dp)) {
                    CalendarAddActionSheetContent(
                        dateLabel = "25 نوفمبر 2026",
                        canDeclareSchoolHoliday = false,
                        onAddLesson = {},
                        onAddHomework = {},
                        onAddExam = {},
                        onAddEvent = {},
                        onDeclareSchoolHoliday = {},
                        modifier = Modifier.testTag("calendar_add_action_list")
                    )
                }
            }
        }

        composeRule.onNodeWithTag("calendar_add_holiday_action").assertDoesNotExist()
        composeRule.onNodeWithTag("calendar_add_event_action")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
