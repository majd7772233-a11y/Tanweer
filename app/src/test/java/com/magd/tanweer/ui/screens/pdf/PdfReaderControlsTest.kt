package com.magd.tanweer.ui.screens.pdf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.magd.tanweer.data.model.BookItem
import com.magd.tanweer.ui.theme.TanweerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ar")
class PdfReaderControlsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun assertMinimumTouchTarget(contentDescription: String) {
        val node = composeRule.onNodeWithContentDescription(contentDescription)
        node.assertWidthIsAtLeast(48.dp)
        node.assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun topReaderBarKeepsPrimaryTargetsAccessibleAtCompactAndCommonWidths() {
        val width = mutableStateOf(360.dp)
        var settingsOpenCount = 0

        composeRule.setContent {
            TanweerTheme {
                Box(Modifier.width(width.value)) {
                    TopReaderBar(
                        book = BookItem(
                            id = "reader-test",
                            gradeId = 10,
                            subjectId = "biology",
                            title = "عنوان كتاب تجريبي طويل لاختبار المساحة"
                        ),
                        currentPage = 24,
                        totalPages = 320,
                        sessionReadingSeconds = 125,
                        isDrawingMode = false,
                        onBack = {},
                        onToggleDrawing = {},
                        onOpenSettings = { settingsOpenCount++ },
                        onOpenBookmarks = {},
                        onOpenNotes = {},
                        onOpenStudyTools = {},
                        onOpenThumbnails = {},
                        onOpenBookInfo = {}
                    )
                }
            }
        }

        listOf(360, 393, 411).forEach { widthDp ->
            composeRule.runOnIdle { width.value = widthDp.dp }
            composeRule.waitForIdle()
            assertMinimumTouchTarget("رجوع للمكتبة")
            assertMinimumTouchTarget("المزيد من أدوات القراءة")
        }

        composeRule.onNodeWithContentDescription("المزيد من أدوات القراءة").performClick()
        composeRule.onNodeWithText("أدوات الدراسة").assertIsDisplayed()
        composeRule.onNodeWithText("تشغيل وضع الرسم").assertIsDisplayed()
        composeRule.onNodeWithText("الفهرس المصغر").assertIsDisplayed()
        composeRule.onNodeWithText("الإشارات المرجعية").assertIsDisplayed()
        composeRule.onNodeWithText("الملاحظات").assertIsDisplayed()
        composeRule.onNodeWithText("الإعدادات وثيمات القراءة")
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, settingsOpenCount)
        }
    }

    @Test
    fun bottomReaderBarKeepsEveryActionAtLeast48dpAcrossPhoneWidths() {
        val width = mutableStateOf(360.dp)
        var rotateCount = 0
        var resetZoomCount = 0

        composeRule.setContent {
            TanweerTheme {
                Box(Modifier.width(width.value)) {
                    BottomReaderBar(
                        currentPage = 24,
                        totalPages = 320,
                        zoomScale = 1.5f,
                        pageTurnMode = PageTurnMode.CANVAS_CURL_3D,
                        readingTheme = ReadingTheme.DAY,
                        isAutoScrollActive = false,
                        isFocusRulerActive = false,
                        onPageChange = {},
                        onPrevPage = {},
                        onNextPage = {},
                        onOpenJumpDialog = {},
                        onResetZoom = { resetZoomCount++ },
                        onRotate = { rotateCount++ },
                        onToggleAutoScroll = {},
                        onToggleFocusRuler = {}
                    )
                }
            }
        }

        val targets = listOf(
            "السابق",
            "التالي",
            "إعادة ضبط التكبير",
            "تدوير الصفحة",
            "مسطرة التركيز القرائي",
            "التقليب التلقائي",
            "الانتقال إلى صفحة"
        )
        listOf(360, 393, 411).forEach { widthDp ->
            composeRule.runOnIdle { width.value = widthDp.dp }
            composeRule.waitForIdle()
            targets.forEach(::assertMinimumTouchTarget)
        }

        composeRule.onNodeWithContentDescription("إعادة ضبط التكبير").performClick()
        composeRule.onNodeWithContentDescription("تدوير الصفحة").performClick()
        composeRule.runOnIdle {
            assertEquals(1, resetZoomCount)
            assertEquals(1, rotateCount)
        }
    }
}
