package com.magd.tanweer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.magd.tanweer.ui.theme.CyanAccent
import com.magd.tanweer.ui.theme.GlassSurfaceLight
import com.magd.tanweer.ui.theme.TextOnAccent
import com.magd.tanweer.ui.theme.TextSecondary

/**
 * A horizontal filter rail with subtle logical-edge fades whenever more items
 * remain outside the viewport. The fades are decorative and never consume taps.
 */
@Composable
fun HorizontalFilterStrip(
    state: LazyListState,
    edgeColor: Color,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
    itemSpacing: Dp = 6.dp,
    content: LazyListScope.() -> Unit
) {
    val layoutDirection = LocalLayoutDirection.current
    LazyRow(
        state = state,
        modifier = modifier.drawWithContent {
            drawContent()
            val fadeWidth = minOf(20.dp.toPx(), size.width / 3f)
            if (fadeWidth <= 0f) return@drawWithContent
            val transparent = edgeColor.copy(alpha = 0f)

            if (layoutDirection == LayoutDirection.Ltr) {
                if (state.canScrollBackward) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(edgeColor, transparent),
                            startX = 0f,
                            endX = fadeWidth
                        ),
                        topLeft = Offset.Zero,
                        size = Size(fadeWidth, size.height)
                    )
                }
                if (state.canScrollForward) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(transparent, edgeColor),
                            startX = size.width - fadeWidth,
                            endX = size.width
                        ),
                        topLeft = Offset(size.width - fadeWidth, 0f),
                        size = Size(fadeWidth, size.height)
                    )
                }
            } else {
                if (state.canScrollBackward) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(transparent, edgeColor),
                            startX = size.width - fadeWidth,
                            endX = size.width
                        ),
                        topLeft = Offset(size.width - fadeWidth, 0f),
                        size = Size(fadeWidth, size.height)
                    )
                }
                if (state.canScrollForward) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(edgeColor, transparent),
                            startX = 0f,
                            endX = fadeWidth
                        ),
                        topLeft = Offset.Zero,
                        size = Size(fadeWidth, size.height)
                    )
                }
            }
        },
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * FilterChip wrapper with a consistent 48dp minimum touch target.
 *
 * Material3 supplies the selected state, role and merged click semantics,
 * keeping icon, label and optional count in one accessible control.
 */
@Composable
fun GlassFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = GlassSurfaceLight,
    selectedContainerColor: Color = CyanAccent,
    labelColor: Color = TextSecondary,
    selectedLabelColor: Color = TextOnAccent,
    label: @Composable () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        label = label,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = containerColor,
            labelColor = labelColor,
            selectedContainerColor = selectedContainerColor,
            selectedLabelColor = selectedLabelColor
        )
    )
}
