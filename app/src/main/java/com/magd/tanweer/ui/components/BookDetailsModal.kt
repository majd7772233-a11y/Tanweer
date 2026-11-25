package com.magd.tanweer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.magd.tanweer.ui.theme.CyanAccent
import com.magd.tanweer.ui.theme.GlassBorderSubtle
import com.magd.tanweer.ui.theme.MidnightSurface

/**
 * A full-window modal container for the library book details.
 *
 * Keeping the scrim and sheet in a Dialog window prevents touches outside the
 * sheet from reaching the library underneath it. The sheet content is scrollable
 * and applies navigation-bar and IME insets before laying out the primary CTA.
 */
@Composable
fun BookDetailsModal(
    onDismissRequest: () -> Unit,
    dismissContentDescription: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrimInteractions = remember { MutableInteractionSource() }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.66f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("book_details_scrim")
                    .clickable(
                        interactionSource = scrimInteractions,
                        indication = null,
                        onClick = onDismissRequest
                    )
                    .semantics {
                        contentDescription = dismissContentDescription
                        role = Role.Button
                    }
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = 720.dp)
                    .testTag("book_details_sheet"),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MidnightSurface,
                contentColor = Color.White,
                shadowElevation = 20.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderSubtle)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 720.dp)
                        .navigationBarsPadding()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        // A tap on non-interactive sheet content is consumed here;
                        // it must never fall through to the dismissing scrim.
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {})
                        }
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .testTag("book_details_sheet_content"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(CyanAccent.copy(alpha = 0.8f))
                            .testTag("book_details_drag_handle")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("book_details_close")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = dismissContentDescription,
                                tint = CyanAccent
                            )
                        }
                    }

                    content()
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
