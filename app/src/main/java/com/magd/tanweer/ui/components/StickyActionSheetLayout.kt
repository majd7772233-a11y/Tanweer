package com.magd.tanweer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.magd.tanweer.R
import com.magd.tanweer.ui.theme.MidnightSurface
import com.magd.tanweer.ui.theme.TextMuted

/**
 * Keeps the primary actions visible while allowing long sheet details to scroll independently.
 * The footer stays outside the scroll container, so reaching the last field never hides the CTA.
 */
@Composable
fun StickyActionSheetLayout(
    modifier: Modifier = Modifier,
    maxHeightFraction: Float = 0.9f,
    bodyContent: @Composable ColumnScope.() -> Unit,
    footerContent: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(maxHeightFraction)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .testTag("sticky_action_sheet_body"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = bodyContent
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sticky_action_sheet_footer"),
            color = MidnightSurface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.sticky_sheet_scroll_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                footerContent()
            }
        }
    }
}
