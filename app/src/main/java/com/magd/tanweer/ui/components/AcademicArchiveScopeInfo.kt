package com.magd.tanweer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.magd.tanweer.R
import com.magd.tanweer.ui.theme.AppTypography
import com.magd.tanweer.ui.theme.EmeraldGreen
import com.magd.tanweer.ui.theme.PurpleAccent
import com.magd.tanweer.ui.theme.TextSecondary

/** Makes the account-wide personal view and selected-group view scopes explicit. */
@Composable
fun AcademicArchiveScopeInfo(
    isPersonalView: Boolean,
    modifier: Modifier = Modifier
) {
    val title = if (isPersonalView) {
        R.string.academic_archive_personal_scope_title
    } else {
        R.string.academic_archive_group_scope_title
    }
    val description = if (isPersonalView) {
        R.string.academic_archive_personal_scope_description
    } else {
        R.string.academic_archive_group_scope_description
    }
    val accent = if (isPersonalView) PurpleAccent else EmeraldGreen

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = stringResource(title),
            style = AppTypography.Badge.copy(color = accent)
        )
        Text(
            text = stringResource(description),
            style = AppTypography.Body.copy(color = TextSecondary)
        )
    }
}
