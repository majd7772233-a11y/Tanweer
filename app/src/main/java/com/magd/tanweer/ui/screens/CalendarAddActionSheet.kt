package com.magd.tanweer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.magd.tanweer.ui.components.GlassCard
import com.magd.tanweer.ui.components.GlassPill
import com.magd.tanweer.ui.theme.CyanAccent
import com.magd.tanweer.ui.theme.CyanGlow
import com.magd.tanweer.ui.theme.EmeraldGreen
import com.magd.tanweer.ui.theme.GlassSurface
import com.magd.tanweer.ui.theme.MidnightSurface
import com.magd.tanweer.ui.theme.PurpleAccent
import com.magd.tanweer.ui.theme.RubyRed
import com.magd.tanweer.ui.theme.TextPrimary
import com.magd.tanweer.ui.theme.TextSecondary
import com.magd.tanweer.ui.theme.WarmAmber
import com.magd.tanweer.ui.theme.tanweerTextSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarAddActionSheet(
    dateLabel: String,
    canDeclareSchoolHoliday: Boolean,
    onDismissRequest: () -> Unit,
    onAddLesson: () -> Unit,
    onAddHomework: () -> Unit,
    onAddExam: () -> Unit,
    onAddEvent: () -> Unit,
    onDeclareSchoolHoliday: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MidnightSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = CyanAccent) }
    ) {
        CalendarAddActionSheetContent(
            dateLabel = dateLabel,
            canDeclareSchoolHoliday = canDeclareSchoolHoliday,
            onAddLesson = onAddLesson,
            onAddHomework = onAddHomework,
            onAddExam = onAddExam,
            onAddEvent = onAddEvent,
            onDeclareSchoolHoliday = onDeclareSchoolHoliday
        )
    }
}

/**
 * Bounded, vertically scrollable action list used by the calendar's add-day sheet.
 * The sheet opens expanded, while this content keeps an 82% viewport ceiling so
 * the actions remain reachable on short displays and with large font settings.
 */
@Composable
internal fun CalendarAddActionSheetContent(
    dateLabel: String,
    canDeclareSchoolHoliday: Boolean,
    onAddLesson: () -> Unit,
    onAddHomework: () -> Unit,
    onAddExam: () -> Unit,
    onAddEvent: () -> Unit,
    onDeclareSchoolHoliday: () -> Unit,
    modifier: Modifier = Modifier
) {
    val maxContentHeight = LocalConfiguration.current.screenHeightDp.dp * 0.82f
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxContentHeight)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "➕ إضافة وتوثيق ليوم: $dateLabel",
            fontSize = tanweerTextSize(16f),
            fontWeight = FontWeight.Bold,
            color = CyanAccent
        )
        Text(
            text = "اختر نوع النشاط الذي ترغب في إضافته أو توثيقه لهذا التاريخ:",
            fontSize = tanweerTextSize(12f),
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(4.dp))

        GlassCard(
            modifier = Modifier.fillMaxWidth().testTag("calendar_add_lesson_action"),
            backgroundColor = GlassSurface,
            onClick = onAddLesson
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("📷", fontSize = tanweerTextSize(24f))
                Column(modifier = Modifier.weight(1f)) {
                    Text("توثيق درس للشعبة", fontSize = tanweerTextSize(14f), fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("التقاط صور السبورة ومشاركتها مع زملاء الصف", fontSize = tanweerTextSize(11f), color = TextSecondary)
                }
                GlassPill(text = "درس", color = CyanAccent, bgColor = CyanGlow)
            }
        }

        GlassCard(
            modifier = Modifier.fillMaxWidth().testTag("calendar_add_homework_action"),
            backgroundColor = GlassSurface,
            onClick = onAddHomework
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("📝", fontSize = tanweerTextSize(24f))
                Column(modifier = Modifier.weight(1f)) {
                    Text("إضافة واجب أو تكليف مدرسي", fontSize = tanweerTextSize(14f), fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("تسجيل الواجب المطلوب وتاريخ التسليم المعتمد", fontSize = tanweerTextSize(11f), color = TextSecondary)
                }
                GlassPill(text = "واجب", color = WarmAmber, bgColor = WarmAmber.copy(alpha = 0.15f))
            }
        }

        GlassCard(
            modifier = Modifier.fillMaxWidth().testTag("calendar_add_exam_action"),
            backgroundColor = GlassSurface,
            onClick = onAddExam
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("📑", fontSize = tanweerTextSize(24f))
                Column(modifier = Modifier.weight(1f)) {
                    Text("إضافة اختبار أو تسميع دوري", fontSize = tanweerTextSize(14f), fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("تحديد موعد الاختبار والفصول المقررة", fontSize = tanweerTextSize(11f), color = TextSecondary)
                }
                GlassPill(text = "اختبار", color = RubyRed, bgColor = RubyRed.copy(alpha = 0.15f))
            }
        }

        GlassCard(
            modifier = Modifier.fillMaxWidth().testTag("calendar_add_event_action"),
            backgroundColor = GlassSurface,
            onClick = onAddEvent
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("🎪", fontSize = tanweerTextSize(24f))
                Column(modifier = Modifier.weight(1f)) {
                    Text("إضافة فعالية أو مناسبة مدرسية", fontSize = tanweerTextSize(14f), fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("تسجيل مسابقة، عطلة، أو نشاط صفي تفاعلي", fontSize = tanweerTextSize(11f), color = TextSecondary)
                }
                GlassPill(text = "فعالية", color = PurpleAccent, bgColor = PurpleAccent.copy(alpha = 0.15f))
            }
        }

        if (canDeclareSchoolHoliday) {
            GlassCard(
                modifier = Modifier.fillMaxWidth().testTag("calendar_add_holiday_action"),
                backgroundColor = GlassSurface,
                onClick = onDeclareSchoolHoliday
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("🌴", fontSize = tanweerTextSize(24f))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("إعلان إجازة مدرسية رسمية", fontSize = tanweerTextSize(14f), fontWeight = FontWeight.Bold, color = EmeraldGreen)
                        Text("تعيين هذا اليوم كإجازة مع توثيق السبب والمبرر الإلزامي", fontSize = tanweerTextSize(11f), color = TextSecondary)
                    }
                    GlassPill(text = "إجازة", color = EmeraldGreen, bgColor = EmeraldGreen.copy(alpha = 0.15f))
                }
            }
        }
    }
}
