package com.magd.tanweer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.magd.tanweer.R
import com.magd.tanweer.data.model.HomeworkItem
import com.magd.tanweer.ui.theme.AppTypography
import com.magd.tanweer.ui.theme.PurpleAccent
import com.magd.tanweer.ui.theme.TextMuted
import com.magd.tanweer.ui.theme.TextPrimary
import com.magd.tanweer.ui.theme.WarmAmber
import com.magd.tanweer.util.HomeworkTaskKind
import com.magd.tanweer.util.HomeworkTaskTypePolicy

const val HOMEWORK_OFFICIAL_BADGE_TEST_TAG = "homework_official_badge"

/** A width-constrained title followed by independent metadata rows. */
@Composable
fun HomeworkCardHeading(
    homework: HomeworkItem,
    modifier: Modifier = Modifier
) {
    val taskKind = HomeworkTaskTypePolicy.classify(homework.taskType)
    val taskKindLabel = when (taskKind) {
        HomeworkTaskKind.HOMEWORK -> R.string.homework_type_label_homework
        HomeworkTaskKind.TASK -> R.string.homework_type_label_project
        HomeworkTaskKind.UNKNOWN -> R.string.homework_type_label_other
    }
    val taskKindColor = when (taskKind) {
        HomeworkTaskKind.HOMEWORK -> WarmAmber
        HomeworkTaskKind.TASK -> PurpleAccent
        HomeworkTaskKind.UNKNOWN -> TextMuted
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "${homework.subjectName}: ${homework.title}",
            modifier = Modifier.fillMaxWidth(),
            style = AppTypography.Title.copy(color = TextPrimary),
            softWrap = true
        )

        if (taskKind != HomeworkTaskKind.HOMEWORK) {
            Row(modifier = Modifier.fillMaxWidth()) {
                HomeworkMetadataBadge(
                    label = stringResource(taskKindLabel),
                    color = taskKindColor
                )
            }
        }

        if (homework.isOfficial) {
            Row(modifier = Modifier.fillMaxWidth()) {
                HomeworkMetadataBadge(
                    label = stringResource(R.string.homework_official_badge),
                    color = WarmAmber,
                    modifier = Modifier.testTag(HOMEWORK_OFFICIAL_BADGE_TEST_TAG)
                )
            }
        }
    }
}

@Composable
private fun HomeworkMetadataBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(label, style = AppTypography.Badge.copy(color = color))
    }
}
