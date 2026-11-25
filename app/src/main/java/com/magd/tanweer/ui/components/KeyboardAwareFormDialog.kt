package com.magd.tanweer.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal fun handleFormDialogBackPress(
    imeVisible: Boolean,
    hideKeyboard: () -> Unit,
    dismiss: () -> Unit
) {
    if (imeVisible) hideKeyboard() else dismiss()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyboardAwareFormDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable BoxScope.() -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        BackHandler(enabled = true) {
            handleFormDialogBackPress(
                imeVisible = imeVisible,
                hideKeyboard = { keyboardController?.hide() },
                dismiss = onDismissRequest
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeContent),
            content = content
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyboardAwareFormScrollColumn(
    modifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .imeNestedScroll()
            .verticalScroll(state),
        verticalArrangement = verticalArrangement,
        content = content
    )
}
