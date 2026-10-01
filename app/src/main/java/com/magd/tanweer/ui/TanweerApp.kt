package com.magd.tanweer.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.ui.components.*
import com.magd.tanweer.ui.screens.*
import com.magd.tanweer.ui.screens.pdf.PdfReaderScreen
import com.magd.tanweer.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TanweerApp(
    viewModel: TanweerViewModel
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val subScreen by viewModel.subScreen.collectAsStateWithLifecycle()
    val activeReadingBook by viewModel.activeReadingBook.collectAsStateWithLifecycle()
    val isUploadModalOpen by viewModel.isUploadModalOpen.collectAsStateWithLifecycle()
    val uploadInitialSubjectId by viewModel.uploadInitialSubjectId.collectAsStateWithLifecycle()
    val recoveryCodeDialog by viewModel.recoveryCodeDialog.collectAsStateWithLifecycle()

    var isMoreMenuOpen by remember { mutableStateOf(false) }

    if (currentUser == null) {
        AuthScreen(viewModel = viewModel)
    } else {
        Scaffold(
            bottomBar = {
                if (subScreen != SubScreen.PDF_VIEWER) {
                    GlassBottomNavigationBar(
                        currentTab = currentTab,
                        isSubScreenActive = (subScreen != SubScreen.NONE),
                        onSelectTab = { tab ->
                            // Always reset subScreen and any open sheet when tapping tabs to prevent freezing
                            viewModel.setSubScreen(SubScreen.NONE)
                            if (tab == NavigationTab.MORE) {
                                isMoreMenuOpen = true
                            } else {
                                isMoreMenuOpen = false
                                viewModel.setTab(tab)
                            }
                        }
                    )
                }
            },
            floatingActionButton = {
                if (subScreen == SubScreen.NONE) {
                    FloatingActionButton(
                        onClick = { viewModel.openUploadDialog() },
                        containerColor = CyanAccent,
                        contentColor = TextOnAccent,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(56.dp)
                            .border(2.dp, CyanGlow, CircleShape)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "إضافة وتوثيق", modifier = Modifier.size(28.dp))
                    }
                }
            },
            containerColor = MidnightBackground,
            contentWindowInsets = WindowInsets.safeDrawing
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (subScreen == SubScreen.PDF_VIEWER) Modifier else Modifier.padding(paddingValues)
                    )
                    .background(MidnightBackground)
            ) {
                if (subScreen == SubScreen.PDF_VIEWER && activeReadingBook != null) {
                    PdfReaderScreen(
                        book = activeReadingBook!!,
                        onBack = { viewModel.closePdfReader() }
                    )
                } else if (subScreen != SubScreen.NONE) {
                    BackHandler {
                        viewModel.setSubScreen(SubScreen.NONE)
                    }
                    Column(modifier = Modifier.fillMaxSize()) {
                        // SubScreen Top Header with Back Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.setSubScreen(SubScreen.NONE) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(GlassSurface)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "رجوع",
                                    tint = CyanAccent
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = when (subScreen) {
                                    SubScreen.GROUPS -> "المجموعات والشعب"
                                    SubScreen.LIBRARY -> "مكتبة المناهج والكتب"
                                    SubScreen.SCHEDULE -> "جدول الحصص المدرسي"
                                    SubScreen.TIMELINE -> "رحلة المادة"
                                    SubScreen.PROFILE -> "ملف الطالب والمساهمات"
                                    SubScreen.SETTINGS -> "إعدادات التطبيق"
                                    SubScreen.WHAT_DID_I_MISS -> "ماذا فاتني؟"
                                    else -> ""
                                },
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        when (subScreen) {
                            SubScreen.GROUPS -> GroupsScreen(viewModel = viewModel)
                            SubScreen.LIBRARY -> LibraryScreen(viewModel = viewModel)
                            SubScreen.SCHEDULE -> ScheduleScreen(viewModel = viewModel)
                            SubScreen.TIMELINE -> SubjectTimelineScreen(viewModel = viewModel)
                            SubScreen.PROFILE -> ProfileScreen(viewModel = viewModel)
                            SubScreen.SETTINGS -> SettingsScreen(viewModel = viewModel)
                            else -> HomeScreen(viewModel = viewModel)
                        }
                    }
                } else {
                    when (currentTab) {
                        NavigationTab.TODAY -> HomeScreen(viewModel = viewModel)
                        NavigationTab.CALENDAR -> CalendarScreen(viewModel = viewModel)
                        NavigationTab.HOMEWORK -> HomeworkScreen(viewModel = viewModel)
                        NavigationTab.EXAMS -> ExamsScreen(viewModel = viewModel)
                        NavigationTab.ISSUES -> IssuesScreen(viewModel = viewModel)
                        NavigationTab.MORE -> HomeScreen(viewModel = viewModel)
                    }
                }

                // Upload Lesson Dialog
                if (isUploadModalOpen) {
                    UploadLessonDialog(
                        viewModel = viewModel,
                        initialSubjectId = uploadInitialSubjectId,
                        onDismiss = { viewModel.closeUploadDialog() }
                    )
                }

                // Recovery Code Dialog for Fresh Registration
                if (recoveryCodeDialog != null) {
                    val codeToCopy = recoveryCodeDialog!!
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissRecoveryDialog() },
                        title = {
                            Text(
                                text = "🔑 رقمك السري ورمز الاسترداد (هام جداً)",
                                color = WarmAmber,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "احتفظ بهذا الرقم السري! يمكنك استخدامه لتسجيل الدخول مباشرة إلى حسابك من أي مكان، أو استعادة حسابك إذا نسيت كلمة المرور:",
                                    fontSize = 13.sp,
                                    color = TextSecondary
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MidnightSurface)
                                        .border(1.dp, WarmAmber, RoundedCornerShape(12.dp))
                                        .padding(14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = codeToCopy,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Black,
                                        color = WarmAmber,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                GlassOutlinedButton(
                                    text = "📋 نسخ الرقم السري إلى الحافظة",
                                    icon = Icons.Default.ContentCopy,
                                    borderColor = WarmAmber,
                                    textColor = WarmAmber,
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Tanweer Secret Code", codeToCopy)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "تم نسخ الرقم السري بنجاح 📋", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth().height(42.dp)
                                )
                            }
                        },
                        confirmButton = {
                            GlassButton(
                                text = "تم الحفظ بنجاح والبدء ✨",
                                color = WarmAmber,
                                textColor = TextOnAccent,
                                onClick = { viewModel.dismissRecoveryDialog() }
                            )
                        },
                        containerColor = MidnightSurface,
                        shape = RoundedCornerShape(20.dp)
                    )
                }

                // More Menu Bottom Sheet
                if (isMoreMenuOpen) {
                    ModalBottomSheet(
                        onDismissRequest = { isMoreMenuOpen = false },
                        containerColor = MidnightSurface,
                        dragHandle = { BottomSheetDefaults.DragHandle(color = CyanAccent) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                                .navigationBarsPadding(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "أقسام تـنـويـر الإضافية",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            MoreMenuItem(
                                title = "👥 المجموعات والشعب الدراسية",
                                subtitle = "نقاشات ومجموعات الصف والشعبة",
                                onClick = {
                                    isMoreMenuOpen = false
                                    viewModel.setSubScreen(SubScreen.GROUPS)
                                }
                            )
                            MoreMenuItem(
                                title = "📚 مكتبة الكتب والمناهج",
                                subtitle = "الكتب الدراسية المعتمدة مع قارئ PDF الذكي",
                                onClick = {
                                    isMoreMenuOpen = false
                                    viewModel.setSubScreen(SubScreen.LIBRARY)
                                }
                            )
                            MoreMenuItem(
                                title = "📅 جدول الحصص الأسبوعي",
                                subtitle = "توزيع حصص ومواد الشعبة خلال الأسبوع",
                                onClick = {
                                    isMoreMenuOpen = false
                                    viewModel.setSubScreen(SubScreen.SCHEDULE)
                                }
                            )
                            MoreMenuItem(
                                title = "🗺️ رحلة المادة وخريطة العام",
                                subtitle = "الخط الزمني التراكمي للدروس والمراجعات",
                                onClick = {
                                    isMoreMenuOpen = false
                                    viewModel.setSubScreen(SubScreen.TIMELINE)
                                }
                            )
                            MoreMenuItem(
                                title = "👤 الملف الشخصي والمساهمات",
                                subtitle = "بيانات الطالب وسجل النشاط الدراسي",
                                onClick = {
                                    isMoreMenuOpen = false
                                    viewModel.setSubScreen(SubScreen.PROFILE)
                                }
                            )
                            MoreMenuItem(
                                title = "⚙️ إعدادات التطبيق وتخصيص الخط",
                                subtitle = "حجم الخط (افتراضي 12px)، السمة، والمزامنة",
                                onClick = {
                                    isMoreMenuOpen = false
                                    viewModel.setSubScreen(SubScreen.SETTINGS)
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MoreMenuItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = GlassSurface,
        onClick = onClick
    ) {
        Column {
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(text = subtitle, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
fun GlassBottomNavigationBar(
    currentTab: NavigationTab,
    isSubScreenActive: Boolean,
    onSelectTab: (NavigationTab) -> Unit
) {
    NavigationBar(
        containerColor = MidnightSurface.copy(alpha = 0.95f),
        contentColor = TextPrimary,
        tonalElevation = 8.dp,
        windowInsets = WindowInsets.navigationBars,
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(0.5.dp, GlassBorder))
    ) {
        val navItems = listOf(
            Triple(NavigationTab.TODAY, "اليوم", Icons.Default.Today),
            Triple(NavigationTab.CALENDAR, "التقويم", Icons.Default.CalendarMonth),
            Triple(NavigationTab.HOMEWORK, "الواجبات", Icons.Default.Assignment),
            Triple(NavigationTab.EXAMS, "الاختبارات", Icons.Default.Science),
            Triple(NavigationTab.ISSUES, "استفسارات", Icons.Default.HelpOutline),
            Triple(NavigationTab.MORE, "المزيد", Icons.Default.Menu)
        )

        navItems.forEach { (tab, label, icon) ->
            val isSelected = !isSubScreenActive && (currentTab == tab) && (tab != NavigationTab.MORE)
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelectTab(tab) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = TextOnAccent,
                    selectedTextColor = CyanAccent,
                    indicatorColor = CyanAccent,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextMuted
                ),
                alwaysShowLabel = true
            )
        }
    }
}
