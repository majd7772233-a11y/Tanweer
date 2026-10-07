package com.magd.tanweer.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.data.model.Role
import com.magd.tanweer.data.model.getRoleEnum
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
    val activeReadingInitialPage by viewModel.activeReadingInitialPage.collectAsStateWithLifecycle()
    val isUploadModalOpen by viewModel.isUploadModalOpen.collectAsStateWithLifecycle()
    val uploadInitialSubjectId by viewModel.uploadInitialSubjectId.collectAsStateWithLifecycle()
    val recoveryCodeDialog by viewModel.recoveryCodeDialog.collectAsStateWithLifecycle()
    val isCheckingAuth by viewModel.isCheckingAuth.collectAsStateWithLifecycle()

    if (isCheckingAuth) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MidnightBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(listOf(CyanAccent, ElectricBlue, PurpleAccent))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📚",
                        fontSize = 34.sp
                    )
                }
                Text(
                    text = "تنوير",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                CircularProgressIndicator(
                    color = CyanAccent,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    } else if (currentUser == null) {
        AuthScreen(viewModel = viewModel)
    } else {
        Scaffold(
            bottomBar = {
                if (subScreen != SubScreen.PDF_VIEWER) {
                    GlassBottomNavigationBar(
                        currentTab = currentTab,
                        subScreen = subScreen,
                        onSelectTab = { tab ->
                            if (tab == NavigationTab.MORE) {
                                if (subScreen == SubScreen.EXTRA_SECTIONS_HUB) {
                                    viewModel.setSubScreen(SubScreen.NONE)
                                } else {
                                    viewModel.setSubScreen(SubScreen.EXTRA_SECTIONS_HUB)
                                }
                            } else {
                                viewModel.setSubScreen(SubScreen.NONE)
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
                        initialPageIndex = activeReadingInitialPage,
                        onBack = { viewModel.closePdfReader() }
                    )
                } else if (subScreen != SubScreen.NONE) {
                    BackHandler {
                        viewModel.navigateBack()
                    }
                    Column(modifier = Modifier.fillMaxSize()) {
                        // SubScreen Top Header with Back Button (omitted if on Hub itself)
                        if (subScreen != SubScreen.EXTRA_SECTIONS_HUB) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { viewModel.navigateBack() },
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
                                        SubScreen.SEARCH -> "البحث الشامل"
                                        SubScreen.SUBJECT_KNOWLEDGE_BASE -> "مساحة المادة الموحدة"
                                        SubScreen.ACADEMIC_HISTORY -> "الأرشيف الأكاديمي"
                                        SubScreen.TEACHER_DASHBOARD -> "مركز التعليم للأستاذ 🎓"
                                        SubScreen.MODERATOR_DASHBOARD -> "مركز إشراف الشعبة 🛡️"
                                        SubScreen.ADMIN_DASHBOARD -> "إدارة المدرسة 👑"
                                        SubScreen.COMMUNITY_DECISIONS -> "القرارات والتصويتات الجماعية 🗳️"
                                        SubScreen.EVENTS -> "الفعاليات والأنشطة المدرسية 🎪"
                                        SubScreen.VERSION_CHECK -> "فحص التحديثات والإصدار 🚀"
                                        SubScreen.STORAGE_MANAGER -> "إدارة تخزين ومساحة الكتب 💾"
                                        else -> ""
                                    },
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                        }

                        when (subScreen) {
                            SubScreen.EXTRA_SECTIONS_HUB -> ExtraSectionsHubScreen(viewModel = viewModel)
                            SubScreen.GROUPS -> GroupsScreen(viewModel = viewModel)
                            SubScreen.LIBRARY, SubScreen.STORAGE_MANAGER -> LibraryScreen(viewModel = viewModel)
                            SubScreen.SCHEDULE -> ScheduleScreen(viewModel = viewModel)
                            SubScreen.TIMELINE -> SubjectTimelineScreen(viewModel = viewModel)
                            SubScreen.PROFILE -> ProfileScreen(viewModel = viewModel)
                            SubScreen.SETTINGS -> SettingsScreen(viewModel = viewModel)
                            SubScreen.WHAT_DID_I_MISS -> WhatDidIMissScreen(viewModel = viewModel)
                            SubScreen.SEARCH -> SearchScreen(viewModel = viewModel)
                            SubScreen.SUBJECT_KNOWLEDGE_BASE -> ClassKnowledgeBaseScreen(viewModel = viewModel)
                            SubScreen.ACADEMIC_HISTORY -> AcademicHistoryScreen(viewModel = viewModel)
                            SubScreen.TEACHER_DASHBOARD -> TeacherDashboardScreen(viewModel = viewModel)
                            SubScreen.MODERATOR_DASHBOARD -> ModeratorDashboardScreen(viewModel = viewModel)
                            SubScreen.ADMIN_DASHBOARD -> AdminDashboardScreen(viewModel = viewModel)
                            SubScreen.COMMUNITY_DECISIONS -> CommunityDecisionsScreen(viewModel = viewModel)
                            SubScreen.EVENTS -> EventsScreen(viewModel = viewModel)
                            SubScreen.VERSION_CHECK -> VersionCheckScreen(viewModel = viewModel)
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
            }
        }
    }
}

@Composable
fun GlassBottomNavigationBar(
    currentTab: NavigationTab,
    subScreen: SubScreen,
    onSelectTab: (NavigationTab) -> Unit
) {
    val isSubScreenActive = (subScreen != SubScreen.NONE)

    // Compute dynamic label & icon for the 6th tab (clean text and icon without emojis)
    val (moreLabel, moreIcon) = when (subScreen) {
        SubScreen.LIBRARY -> "المكتبة" to Icons.Default.LocalLibrary
        SubScreen.COMMUNITY_DECISIONS -> "القرارات" to Icons.Default.HowToVote
        SubScreen.EVENTS -> "الفعاليات" to Icons.Default.Event
        SubScreen.GROUPS -> "الشعب" to Icons.Default.Groups
        SubScreen.SCHEDULE -> "الجدول" to Icons.Default.Schedule
        SubScreen.TIMELINE -> "الرحلة" to Icons.Default.Timeline
        SubScreen.WHAT_DID_I_MISS -> "ماذا فاتني" to Icons.Default.WorkHistory
        SubScreen.SEARCH -> "البحث" to Icons.Default.Search
        SubScreen.TEACHER_DASHBOARD -> "الأستاذ" to Icons.Default.School
        SubScreen.MODERATOR_DASHBOARD -> "الإشراف" to Icons.Default.Shield
        SubScreen.ADMIN_DASHBOARD -> "الإدارة" to Icons.Default.AdminPanelSettings
        SubScreen.PROFILE -> "الملف الشخصي" to Icons.Default.Person
        SubScreen.SETTINGS -> "الإعدادات" to Icons.Default.Settings
        SubScreen.ACADEMIC_HISTORY -> "الأرشيف" to Icons.Default.Archive
        SubScreen.SUBJECT_KNOWLEDGE_BASE -> "المادة" to Icons.Default.MenuBook
        SubScreen.VERSION_CHECK -> "التحديثات" to Icons.Default.SystemUpdate
        SubScreen.STORAGE_MANAGER -> "التخزين" to Icons.Default.SdCard
        SubScreen.EXTRA_SECTIONS_HUB, SubScreen.NONE, SubScreen.PDF_VIEWER -> "الأقسام" to Icons.Default.DashboardCustomize
        else -> "الأقسام" to Icons.Default.DashboardCustomize
    }

    NavigationBar(
        containerColor = MidnightSurface.copy(alpha = 0.95f),
        contentColor = TextPrimary,
        tonalElevation = 8.dp,
        windowInsets = WindowInsets.navigationBars,
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(0.5.dp, GlassBorder))
    ) {
        val staticNavItems = listOf(
            Triple(NavigationTab.TODAY, "اليوم", Icons.Default.Today),
            Triple(NavigationTab.CALENDAR, "التقويم", Icons.Default.CalendarMonth),
            Triple(NavigationTab.HOMEWORK, "الواجبات", Icons.Default.Assignment),
            Triple(NavigationTab.EXAMS, "الاختبارات", Icons.Default.Science),
            Triple(NavigationTab.ISSUES, "استفسارات", Icons.Default.HelpOutline)
        )

        staticNavItems.forEach { (tab, label, icon) ->
            val isSelected = !isSubScreenActive && (currentTab == tab)
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

        // 6th Dynamic Morphing Tab with Slick Animation
        val isMoreSelected = isSubScreenActive
        NavigationBarItem(
            selected = isMoreSelected,
            onClick = { onSelectTab(NavigationTab.MORE) },
            icon = {
                AnimatedContent(
                    targetState = moreIcon,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(220, delayMillis = 50)) +
                            scaleIn(initialScale = 0.8f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)))
                            .togetherWith(fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.8f))
                    },
                    label = "DynamicNavIcon"
                ) { targetIcon ->
                    Icon(
                        imageVector = targetIcon,
                        contentDescription = moreLabel,
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            label = {
                AnimatedContent(
                    targetState = moreLabel,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(200)).togetherWith(fadeOut(animationSpec = tween(150)))
                    },
                    label = "DynamicNavLabel"
                ) { targetLabel ->
                    Text(
                        text = targetLabel,
                        fontSize = 10.sp,
                        fontWeight = if (isMoreSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = TextOnAccent,
                selectedTextColor = if (subScreen == SubScreen.EXTRA_SECTIONS_HUB) CyanAccent else WarmAmber,
                indicatorColor = if (subScreen == SubScreen.EXTRA_SECTIONS_HUB) CyanAccent else WarmAmber,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextMuted
            ),
            alwaysShowLabel = true
        )
    }
}
