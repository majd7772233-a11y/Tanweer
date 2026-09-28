package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BookItem
import com.example.ui.theme.*

@Composable
fun Canvas3DBookCard(
    book: BookItem,
    isDownloaded: Boolean = false,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Smooth press and hover physics
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "bookScale"
    )

    // Animated holographic sheen shimmer
    val infiniteTransition = rememberInfiniteTransition(label = "bookSheen")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -100f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sheenOffset"
    )

    // Color theme based on subject
    val (primaryColor, secondaryColor, accentGold) = remember(book.subjectId) {
        getBookThemeColors(book.subjectId)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(MidnightSurface)
            .border(
                1.dp,
                if (isDownloaded) EmeraldGreen.copy(alpha = 0.5f) else GlassBorderSubtle,
                RoundedCornerShape(20.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) {
                onDownload()
            }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 3D Canvas Book Model
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(138.dp)
                    .shadow(12.dp, RoundedCornerShape(10.dp), spotColor = primaryColor.copy(alpha = 0.5f))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val spineWidth = 14f
                    val pagesThickness = 10f

                    // 1. Draw 3D Page Ridge Layer (Paper Pages on the left for RTL)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFAF2E1),
                                Color(0xFFE2D4BB),
                                Color(0xFFC7B798),
                                Color(0xFFFAF2E1)
                            )
                        ),
                        topLeft = Offset(0f, 4f),
                        size = Size(w - spineWidth, h - 8f),
                        cornerRadius = CornerRadius(4f, 4f)
                    )

                    // Page horizontal ridge texture lines
                    for (i in 1..8) {
                        val lineY = 6f + (i * ((h - 12f) / 9f))
                        drawLine(
                            color = Color(0x338A7352),
                            start = Offset(2f, lineY),
                            end = Offset(w - spineWidth - 4f, lineY),
                            strokeWidth = 1f
                        )
                    }

                    // 2. Draw Main Hardcover Front
                    val coverPath = Path().apply {
                        moveTo(pagesThickness, 0f)
                        lineTo(w, 0f)
                        lineTo(w, h)
                        lineTo(pagesThickness, h)
                        close()
                    }

                    drawPath(
                        path = coverPath,
                        brush = Brush.linearGradient(
                            colors = listOf(primaryColor, secondaryColor, primaryColor.copy(alpha = 0.9f)),
                            start = Offset(pagesThickness, 0f),
                            end = Offset(w, h)
                        )
                    )

                    // 3. Golden Geometric Framing on Cover
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(accentGold, accentGold.copy(alpha = 0.3f), accentGold)
                        ),
                        topLeft = Offset(pagesThickness + 6f, 6f),
                        size = Size(w - pagesThickness - 18f, h - 12f),
                        cornerRadius = CornerRadius(6f, 6f),
                        style = Stroke(width = 1.5f)
                    )

                    // Decorative corner diamonds
                    val diamondSize = 4f
                    drawCircle(
                        color = accentGold,
                        radius = diamondSize,
                        center = Offset(pagesThickness + 12f, 12f)
                    )
                    drawCircle(
                        color = accentGold,
                        radius = diamondSize,
                        center = Offset(w - 18f, 12f)
                    )
                    drawCircle(
                        color = accentGold,
                        radius = diamondSize,
                        center = Offset(pagesThickness + 12f, h - 12f)
                    )
                    drawCircle(
                        color = accentGold,
                        radius = diamondSize,
                        center = Offset(w - 18f, h - 12f)
                    )

                    // 4. Draw 3D Spine (Right side for Arabic book layout)
                    val spineRect = RectOffset(w - spineWidth, 0f, spineWidth, h)
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0x66000000), // Crease Shadow
                                Color(0x22FFFFFF), // Highlight ridge
                                primaryColor,
                                Color(0x88000000)  // Outer dark rim
                            ),
                            startX = spineRect.x,
                            endX = spineRect.x + spineRect.w
                        ),
                        topLeft = Offset(spineRect.x, spineRect.y),
                        size = Size(spineRect.w, spineRect.h)
                    )

                    // Spine golden rib lines
                    val ribY1 = h * 0.25f
                    val ribY2 = h * 0.75f
                    drawLine(
                        color = accentGold.copy(alpha = 0.8f),
                        start = Offset(w - spineWidth, ribY1),
                        end = Offset(w, ribY1),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = accentGold.copy(alpha = 0.8f),
                        start = Offset(w - spineWidth, ribY2),
                        end = Offset(w, ribY2),
                        strokeWidth = 2f
                    )

                    // 5. Holographic Light Sheen Sweep Animation
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x55FFFFFF),
                                Color(0x99FFFFFF),
                                Color(0x55FFFFFF),
                                Color.Transparent
                            ),
                            start = Offset(shimmerOffset - 60f, 0f),
                            end = Offset(shimmerOffset + 60f, h)
                        ),
                        start = Offset(pagesThickness, 0f),
                        end = Offset(w, h),
                        strokeWidth = 35f
                    )
                }

                // Center Icon Emblem
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(end = 6.dp)
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0x33000000))
                        .border(1.dp, accentGold.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = book.subjectIcon.ifEmpty { "📕" },
                        fontSize = 22.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Book Details & Actions
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassPill(
                        text = book.subjectName,
                        color = primaryColor,
                        bgColor = primaryColor.copy(alpha = 0.15f)
                    )

                    if (isDownloaded) {
                        GlassPill(
                            text = "محمل أوفلاين ✅",
                            color = EmeraldGreen,
                            bgColor = EmeraldGreen.copy(alpha = 0.2f)
                        )
                    } else {
                        Text(
                            text = "${book.fileSizeMb} MB",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = book.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = book.edition ?: "إصدار رسمي - تنوير",
                    fontSize = 12.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )

                // Open Book in Internal Reader Button
                GlassButton(
                    text = if (isDownloaded) "فتح ومطالعة مباشرة 📖" else "تحميل ومطالعة الكتاب 📖",
                    icon = if (isDownloaded) Icons.Default.MenuBook else Icons.Default.CloudDownload,
                    color = if (isDownloaded) EmeraldGreen else primaryColor,
                    textColor = TextOnAccent,
                    onClick = onDownload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                )
            }
        }
    }
}

@Composable
fun CanvasBookshelfSpine(
    book: BookItem,
    isDownloaded: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (primaryColor, secondaryColor, accentGold) = remember(book.subjectId) {
        getBookThemeColors(book.subjectId)
    }

    // Spine pull-out animation
    val liftOffset by animateFloatAsState(
        targetValue = if (isSelected) -18f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "spineLift"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .offset(y = liftOffset.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .width(46.dp)
                .height(190.dp)
                .shadow(
                    elevation = if (isSelected) 16.dp else 6.dp,
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                    spotColor = primaryColor.copy(alpha = 0.6f)
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // 1. Spine Leather Texture Background
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0x66000000),
                            primaryColor,
                            secondaryColor,
                            primaryColor,
                            Color(0x88000000)
                        )
                    ),
                    size = size,
                    cornerRadius = CornerRadius(4f, 4f)
                )

                // 2. Embossed Golden Bands Top and Bottom
                val bandHeight = 6f
                drawRect(color = accentGold, topLeft = Offset(0f, 16f), size = Size(w, bandHeight))
                drawRect(color = accentGold, topLeft = Offset(0f, 26f), size = Size(w, 2f))

                drawRect(color = accentGold, topLeft = Offset(0f, h - 30f), size = Size(w, 2f))
                drawRect(color = accentGold, topLeft = Offset(0f, h - 22f), size = Size(w, bandHeight))

                // 3. Highlight Crease
                drawLine(
                    color = Color(0x44FFFFFF),
                    start = Offset(w * 0.3f, 4f),
                    end = Offset(w * 0.3f, h - 4f),
                    strokeWidth = 2f
                )

                // 4. Downloaded checkmark badge on spine
                if (isDownloaded) {
                    drawCircle(
                        color = Color(0xFF00E676),
                        radius = 6f,
                        center = Offset(w / 2f, h - 46f)
                    )
                }
            }

            // Vertical Subject Icon & Text
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 34.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = book.subjectIcon.ifEmpty { "📕" },
                    fontSize = 16.sp
                )

                Text(
                    text = book.subjectName.take(12),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 4,
                    textAlign = TextAlign.Center,
                    lineHeight = 12.sp,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = if (book.title.contains("2") || book.title.contains("ثاني")) "جـ2" else if (book.title.contains("1") || book.title.contains("أول")) "جـ1" else "كتاب",
                    color = accentGold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private data class RectOffset(val x: Float, val y: Float, val w: Float, val h: Float)

fun getBookThemeColors(subjectId: String): Triple<Color, Color, Color> {
    val s = subjectId.lowercase()
    return when {
        s.contains("quran") -> Triple(Color(0xFF00897B), Color(0xFF004D40), Color(0xFFFFD54F))
        s.contains("islam") || s.contains("sirah") || s.contains("hadith") || s.contains("eman") ->
            Triple(Color(0xFF0288D1), Color(0xFF01579B), Color(0xFFFFCA28))
        s.contains("arabic") || s.contains("syntax") || s.contains("literature") || s.contains("reading") ->
            Triple(Color(0xFFE65100), Color(0xFFBF360C), Color(0xFFFFE082))
        s.contains("math") -> Triple(Color(0xFF1565C0), Color(0xFF0D47A1), Color(0xFF80D8FF))
        s.contains("science") -> Triple(Color(0xFF2E7D32), Color(0xFF1B5E20), Color(0xFFA5D6A7))
        s.contains("chem") -> Triple(Color(0xFF00838F), Color(0xFF006064), Color(0xFF80DEEA))
        s.contains("phy") -> Triple(Color(0xFF6A1B9A), Color(0xFF4A148C), Color(0xFFCE93D8))
        s.contains("bio") -> Triple(Color(0xFF2E7D32), Color(0xFF1B5E20), Color(0xFF81C784))
        s.contains("eng") -> Triple(Color(0xFFC2185B), Color(0xFF880E4F), Color(0xFFFF80AB))
        s.contains("geo") -> Triple(Color(0xFFF57F17), Color(0xFFE65100), Color(0xFFFFE082))
        s.contains("his") -> Triple(Color(0xFF8D6E63), Color(0xFF4E342E), Color(0xFFFFCC80))
        s.contains("civic") || s.contains("yemen") -> Triple(Color(0xFFD32F2F), Color(0xFFB71C1C), Color(0xFFFFD54F))
        s.contains("comp") -> Triple(Color(0xFF0288D1), Color(0xFF00838F), Color(0xFF80D8FF))
        else -> Triple(Color(0xFF37474F), Color(0xFF212121), Color(0xFFB0BEC5))
    }
}
