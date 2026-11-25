package com.magd.tanweer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyboardAwareFormDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun backWithVisibleImeHidesKeyboardWithoutDismissingForm() {
        var hideCount = 0
        var dismissCount = 0

        handleFormDialogBackPress(
            imeVisible = true,
            hideKeyboard = { hideCount++ },
            dismiss = { dismissCount++ }
        )

        assertEquals(1, hideCount)
        assertEquals(0, dismissCount)
    }

    @Test
    fun backWithoutImeDismissesForm() {
        var hideCount = 0
        var dismissCount = 0

        handleFormDialogBackPress(
            imeVisible = false,
            hideKeyboard = { hideCount++ },
            dismiss = { dismissCount++ }
        )

        assertEquals(0, hideCount)
        assertEquals(1, dismissCount)
    }

    @Test
    fun compactFormKeepsSubmitActionVisibleAndAllowsScrollingToLastField() {
        var submitted = false

        composeRule.setContent {
            TanweerTheme {
                Column(
                    modifier = Modifier
                        .width(360.dp)
                        .height(340.dp)
                ) {
                    KeyboardAwareFormScrollColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(8) { index ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .testTag("form_field_$index")
                            ) {
                                Text("Field $index")
                            }
                        }
                    }

                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("form_submit_action"),
                        onClick = { submitted = true }
                    ) {
                        Text("Save")
                    }
                }
            }
        }

        composeRule.onNodeWithTag("form_submit_action")
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithTag("form_field_7")
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithTag("form_submit_action").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(submitted) }
    }
}
