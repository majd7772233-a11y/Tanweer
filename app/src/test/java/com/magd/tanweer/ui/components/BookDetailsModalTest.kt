package com.magd.tanweer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.magd.tanweer.ui.components.GlassButton
import com.magd.tanweer.ui.theme.TanweerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookDetailsModalTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun scrimTapDismissesTheModalWithoutActivatingTheLibraryBehindIt() {
        var backgroundClicks = 0
        var dismissRequests = 0

        composeRule.setContent {
            TanweerTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("library_underlay")
                        .clickable { backgroundClicks++ }
                ) {
                    BookDetailsModal(
                        onDismissRequest = { dismissRequests++ },
                        dismissContentDescription = "Close book details"
                    ) {
                        Text("Book details content")
                    }
                }
            }
        }

        composeRule.onNodeWithTag("book_details_scrim").performClick()

        composeRule.runOnIdle {
            assertEquals(1, dismissRequests)
            assertEquals(0, backgroundClicks)
        }
    }

    @Test
    fun tapsInsideTheSheetDoNotDismissItAndThePrimaryActionRemainsClickable() {
        var backgroundClicks = 0
        var dismissRequests = 0
        var openRequests = 0

        composeRule.setContent {
            TanweerTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("library_underlay")
                        .clickable { backgroundClicks++ }
                ) {
                    BookDetailsModal(
                        onDismissRequest = { dismissRequests++ },
                        dismissContentDescription = "Close book details"
                    ) {
                        Text(
                            text = "Book details content",
                            modifier = Modifier.testTag("book_details_text")
                        )
                        GlassButton(
                            text = "Open book",
                            onClick = { openRequests++ },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "book_details_open_button"
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("book_details_text").performTouchInput { click() }
        composeRule.onNodeWithTag("book_details_open_button").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals(0, dismissRequests)
            assertEquals(0, backgroundClicks)
            assertEquals(1, openRequests)
        }
    }
}
