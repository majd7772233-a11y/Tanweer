package com.magd.tanweer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.magd.tanweer.ui.theme.MidnightSurface
import com.magd.tanweer.ui.theme.TanweerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ar")
class HorizontalFilterStripTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun filterRailRemainsScrollableAndFilterChipsExposeSelectedStateAcrossPhoneWidths() {
        val width = mutableStateOf(360.dp)
        val selectedIndex = mutableStateOf(0)
        var clickCount = 0
        val labels = listOf("الكل", "اليوم", "غداً", "الأسبوع", "متأخر", "المكتملة")

        composeRule.setContent {
            TanweerTheme {
                Box(Modifier.width(width.value)) {
                    val state = rememberLazyListState()
                    HorizontalFilterStrip(
                        state = state,
                        edgeColor = MidnightSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .testTag("filter_strip"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        itemSpacing = 6.dp
                    ) {
                        items(labels.size) { index ->
                            GlassFilterChip(
                                selected = selectedIndex.value == index,
                                onClick = {
                                    selectedIndex.value = index
                                    clickCount++
                                },
                                modifier = Modifier.testTag("filter_chip_$index"),
                                containerColor = MidnightSurface,
                                selectedContainerColor = com.magd.tanweer.ui.theme.CyanAccent,
                                label = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("📋", modifier = Modifier.clearAndSetSemantics {})
                                        Text(labels[index])
                                        if (selectedIndex.value == index) {
                                            Text("✓", modifier = Modifier.clearAndSetSemantics {})
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        listOf(360, 393, 411).forEach { widthDp ->
            composeRule.runOnIdle {
                width.value = widthDp.dp
                selectedIndex.value = 0
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("filter_chip_0")
                .assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp)
                .assertIsSelected()
                .assertTextContains("الكل")
            composeRule.onNodeWithTag("filter_chip_1").performClick()
            composeRule.onNodeWithTag("filter_chip_0").assertIsNotSelected()
            composeRule.onNodeWithTag("filter_chip_1").assertIsSelected()

            composeRule.onNodeWithTag("filter_strip").performScrollToIndex(5)
            composeRule.onNodeWithTag("filter_chip_5").assertIsDisplayed()
            composeRule.onNodeWithTag("filter_chip_5").performClick()
            composeRule.onNodeWithTag("filter_chip_5").assertIsSelected()
            composeRule.onNodeWithTag("filter_strip").performScrollToIndex(0)
            composeRule.waitForIdle()
        }

        composeRule.runOnIdle {
            assertEquals(6, clickCount)
        }
    }
}
