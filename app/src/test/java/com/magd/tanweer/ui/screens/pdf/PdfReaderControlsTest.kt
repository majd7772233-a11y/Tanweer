package com.magd.tanweer.ui.screens.pdf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.magd.tanweer.R
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

    private fun localizedString(resourceId: Int): String =
        ApplicationProvider.getApplicationContext<Context>().getString(resourceId)

    private fun assertMinimumTouchTarget(contentDescriptionResourceId: Int) {
        val node = composeRule.onNodeWithContentDescription(localizedString(contentDescriptionResourceId))
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
            assertMinimumTouchTarget(R.string.pdf_reader_back_to_library)
            assertMinimumTouchTarget(R.string.pdf_reader_more_tools)
        }

        composeRule.onNodeWithContentDescription(localizedString(R.string.pdf_reader_more_tools)).performClick()
        composeRule.onNodeWithText(localizedString(R.string.pdf_reader_study_tools)).assertIsDisplayed()
        composeRule.onNodeWithText(localizedString(R.string.pdf_reader_enable_drawing)).assertIsDisplayed()
        composeRule.onNodeWithText(localizedString(R.string.pdf_reader_thumbnails)).assertIsDisplayed()
        composeRule.onNodeWithText(localizedString(R.string.pdf_reader_bookmarks)).assertIsDisplayed()
        composeRule.onNodeWithText(localizedString(R.string.pdf_reader_notes)).assertIsDisplayed()
        composeRule.onNodeWithText(localizedString(R.string.pdf_reader_settings))
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
            R.string.pdf_reader_previous_page,
            R.string.pdf_reader_next_page,
            R.string.pdf_reader_reset_zoom,
            R.string.pdf_reader_rotate_page,
            R.string.pdf_reader_focus_ruler,
            R.string.pdf_reader_auto_scroll,
            R.string.pdf_reader_jump_to_page
        )
        listOf(360, 393, 411).forEach { widthDp ->
            composeRule.runOnIdle { width.value = widthDp.dp }
            composeRule.waitForIdle()
            targets.forEach(::assertMinimumTouchTarget)
        }

        composeRule.onNodeWithContentDescription(localizedString(R.string.pdf_reader_reset_zoom)).performClick()
        composeRule.onNodeWithContentDescription(localizedString(R.string.pdf_reader_rotate_page)).performClick()
        composeRule.runOnIdle {
            assertEquals(1, resetZoomCount)
            assertEquals(1, rotateCount)
        }
    }
}
