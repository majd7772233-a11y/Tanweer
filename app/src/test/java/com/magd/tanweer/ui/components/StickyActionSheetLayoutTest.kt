package com.magd.tanweer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
class StickyActionSheetLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun footerActionRemainsVisibleWhileLastDetailIsScrolledIntoView() {
        val actionCount = mutableIntStateOf(0)

        composeRule.setContent {
            TanweerTheme {
                Box(Modifier.width(360.dp).height(340.dp)) {
                    StickyActionSheetLayout(
                        modifier = Modifier.fillMaxSize(),
                        maxHeightFraction = 0.9f,
                        bodyContent = {
                            repeat(12) { index ->
                                Text(
                                    text = "Detail ${index + 1}",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .testTag("sticky_body_item_$index")
                                )
                            }
                        },
                        footerContent = {
                            Button(
                                onClick = { actionCount.intValue++ },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("sticky_primary_action")
                            ) {
                                Text("Save")
                            }
                        }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("sticky_action_sheet_footer").assertIsDisplayed()
        composeRule.onNodeWithTag("sticky_primary_action").assertIsDisplayed()
        composeRule.onNodeWithTag("sticky_body_item_0").assertIsDisplayed()

        composeRule.onNodeWithTag("sticky_body_item_11").performScrollTo()
        composeRule.onNodeWithTag("sticky_body_item_11").assertIsDisplayed()
        composeRule.onNodeWithTag("sticky_primary_action").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, actionCount.intValue) }

        composeRule.onNodeWithTag("sticky_primary_action").performClick()
        composeRule.runOnIdle { assertEquals(1, actionCount.intValue) }
    }

    @Test
    fun compactBodyKeepsFooterAndDoesNotRequireScrollingForShortContent() {
        composeRule.setContent {
            TanweerTheme {
                Box(Modifier.width(411.dp).height(891.dp)) {
                    StickyActionSheetLayout(
                        modifier = Modifier.fillMaxSize(),
                        bodyContent = {
                            repeat(3) { index ->
                                Text(
                                    text = "Detail ${index + 1}",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .testTag("short_body_item_$index")
                                )
                            }
                        },
                        footerContent = {
                            Button(
                                onClick = {},
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("short_primary_action")
                            ) {
                                Text("Save")
                            }
                        }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("short_body_item_2").assertIsDisplayed()
        composeRule.onNodeWithTag("short_primary_action").assertIsDisplayed()
    }
}

