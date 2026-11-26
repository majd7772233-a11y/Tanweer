package com.magd.tanweer.ui.screens

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.magd.tanweer.data.model.HomeworkItem
import com.magd.tanweer.ui.components.HOMEWORK_OFFICIAL_BADGE_TEST_TAG
import com.magd.tanweer.ui.components.HomeworkCardHeading
import com.magd.tanweer.ui.theme.TanweerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeworkOfficialBadgeUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun officialBadgeRemainsVisibleWithLongArabicTitleRtlAndEnlargedText() {
        val homework = sampleHomework()
        val headingText = "${homework.subjectName}: ${homework.title}"

        composeRule.setContent {
            TanweerTheme(fontScale = 1.3333f) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    HomeworkCardHeading(
                        homework = homework,
                        modifier = Modifier.width(220.dp)
                    )
                }
            }
        }

        composeRule.onNodeWithText(headingText).assertIsDisplayed()
        composeRule.onNodeWithTag(HOMEWORK_OFFICIAL_BADGE_TEST_TAG).assertIsDisplayed()
    }

    private fun sampleHomework() = HomeworkItem(
        id = "homework-official-ui-test",
        groupId = "group-1",
        studyDate = "2026-11-20",
        dueDate = "2026-11-25",
        subjectId = "arabic",
        title = "واجب شامل طويل جداً لمراجعة الوحدة الدراسية والتدريبات الإضافية مع قراءة النص وتحليل الأفكار والإجابة عن جميع الأسئلة بدقة",
        subjectName = "اللغة العربية",
        isOfficial = true
    )
}
