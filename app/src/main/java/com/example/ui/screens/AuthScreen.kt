package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SchoolHierarchy
import com.example.ui.TanweerViewModel
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassTextField
import com.example.ui.theme.*
import com.example.util.ArabicNameValidator
import com.example.util.PasswordStrengthAnalyzer
import com.example.util.PasswordStrengthLevel
import com.example.util.YemenPhoneValidator

enum class AuthMode {
    REGISTER,
    LOGIN_PHONE_PASS,
    LOGIN_RECOVERY_CODE
}

@Composable
fun AuthScreen(
    viewModel: TanweerViewModel
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var authMode by remember { mutableStateOf(AuthMode.REGISTER) }

    var fullName by remember { mutableStateOf("") }
    var rawPhoneDigits by remember { mutableStateOf("") } // strictly 9 digits e.g. "777123456"
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var recoveryCodeInput by remember { mutableStateOf("") }

    var selectedGradeId by remember { mutableIntStateOf(11) } // Default 2nd Secondary
    var selectedSectionId by remember { mutableStateOf("B") } // Default Section B
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Live Validation States
    val nameValidation = remember(fullName) {
        if (fullName.isEmpty()) null else ArabicNameValidator.validate(fullName)
    }

    val phoneValidation = remember(rawPhoneDigits) {
        if (rawPhoneDigits.isEmpty()) null else YemenPhoneValidator.formatAndValidate(rawPhoneDigits)
    }

    val passwordAnalysis = remember(password) {
        if (password.isEmpty()) null else PasswordStrengthAnalyzer.analyze(password)
    }

    val currentGrade = SchoolHierarchy.grades.find { it.id == selectedGradeId } ?: SchoolHierarchy.grades[4]

    // If grade changes, ensure section is valid
    LaunchedEffect(selectedGradeId) {
        if (!currentGrade.allowedSections.contains(selectedSectionId)) {
            selectedSectionId = currentGrade.allowedSections.firstOrNull() ?: "A"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // App Brand Header with Canvas Glow
            Text(
                text = "✨ تـنـويـر",
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                color = CyanAccent,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "النظام الدراسي الجماعي لطلاب المدرسة",
                fontSize = 14.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "«كل يوم له محتواه، وكل مادة لها مكان، ولا يضيع درس»",
                fontSize = 12.sp,
                color = WarmAmber,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Auth Mode Selector Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MidnightSurface)
                    .border(1.dp, GlassBorderSubtle, RoundedCornerShape(16.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Register Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (authMode == AuthMode.REGISTER) CyanAccent else Color.Transparent)
                        .clickable {
                            authMode = AuthMode.REGISTER
                            errorMessage = null
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "طالب جديد 👤",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (authMode == AuthMode.REGISTER) TextOnAccent else TextSecondary
                    )
                }

                // Phone/Pass Login Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (authMode == AuthMode.LOGIN_PHONE_PASS) CyanAccent else Color.Transparent)
                        .clickable {
                            authMode = AuthMode.LOGIN_PHONE_PASS
                            errorMessage = null
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "تسجيل دخول 🔐",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (authMode == AuthMode.LOGIN_PHONE_PASS) TextOnAccent else TextSecondary
                    )
                }

                // Recovery Code Login Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (authMode == AuthMode.LOGIN_RECOVERY_CODE) CyanAccent else Color.Transparent)
                        .clickable {
                            authMode = AuthMode.LOGIN_RECOVERY_CODE
                            errorMessage = null
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "بالرمز السري 🔑",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (authMode == AuthMode.LOGIN_RECOVERY_CODE) TextOnAccent else TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Error Banner
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (errorMessage != null) {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        backgroundColor = Color(0x33FF3366),
                        borderColor = RubyRed
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(text = "⚠️", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage!!,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Main Auth Form
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = GlassSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (authMode) {
                        AuthMode.REGISTER -> {
                            // 1. Full Name with Smart Arabic/English Validator
                            Column {
                                Text(
                                    text = "الاسم الثلاثي للطالب (عربي أو إنجليزي):",
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                GlassTextField(
                                    value = fullName,
                                    onValueChange = { fullName = it },
                                    label = "",
                                    placeholder = "مثال: أحمد محمد علي أو Ahmed Mohammed Ali",
                                    leadingIcon = Icons.Default.Person,
                                    isError = nameValidation != null && !nameValidation.isValid,
                                    errorMessage = nameValidation?.errorMessage
                                )
                                if (nameValidation?.isValid == true) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "الاسم صالح ومطابق للشروط (${if (nameValidation.detectedLanguage == "AR") "لغة عربية 🇾🇪" else "لغة إنجليزية 🇬🇧"})",
                                            color = EmeraldGreen,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // 2. Fixed Yemen Phone Input (+967 locked, exactly 7XX XXX XXX)
                            YemenPhoneInputField(
                                phoneDigits = rawPhoneDigits,
                                onPhoneChange = { digits ->
                                    rawPhoneDigits = digits
                                },
                                phoneValidation = phoneValidation
                            )

                            // 3. Password Field with Canvas Eye Icon & Animated Strength Meter
                            CanvasPasswordInputField(
                                password = password,
                                onPasswordChange = { password = it },
                                isPasswordVisible = isPasswordVisible,
                                onToggleVisibility = { isPasswordVisible = !isPasswordVisible },
                                analysis = passwordAnalysis
                            )

                            // 4. Grade and Section Selector
                            Column {
                                Text(
                                    text = "الصف الدراسي والشعبة:",
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )

                                // Grade selector row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        7 to "سابع",
                                        8 to "ثامن",
                                        9 to "تاسع",
                                        10 to "أول ث",
                                        11 to "ثاني ث",
                                        12 to "ثالث ث"
                                    ).forEach { (gId, gLabel) ->
                                        val isSelected = selectedGradeId == gId
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isSelected) CyanGlow else MidnightSurface)
                                                .border(1.dp, if (isSelected) CyanAccent else GlassBorderSubtle, RoundedCornerShape(10.dp))
                                                .clickable { selectedGradeId = gId }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = gLabel,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) CyanAccent else TextPrimary
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Section selector row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "الشعبة:",
                                        fontSize = 12.sp,
                                        color = TextMuted
                                    )
                                    currentGrade.allowedSections.forEach { secCode ->
                                        val isSelected = selectedSectionId == secCode
                                        val secName = SchoolHierarchy.getSectionArabicName(secCode)
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) CyanAccent else GlassSurfaceLight)
                                                .border(1.dp, if (isSelected) CyanAccent else GlassBorderSubtle, CircleShape)
                                                .clickable { selectedSectionId = secCode },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = secName,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) TextOnAccent else TextPrimary
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Submit Button
                            val isRegisterReady = (nameValidation?.isValid == true) &&
                                    (phoneValidation?.isValid == true) &&
                                    (passwordAnalysis?.isAcceptable == true)

                            GlassButton(
                                text = if (isLoading) "جاري إنشاء الحساب..." else "إنشاء الحساب والدخول 🚀",
                                enabled = isRegisterReady && !isLoading,
                                onClick = {
                                    focusManager.clearFocus()
                                    isLoading = true
                                    errorMessage = null
                                    viewModel.register(
                                        fullName = fullName.trim(),
                                        phone = phoneValidation!!.fullE164,
                                        pass = password.trim(),
                                        gradeId = selectedGradeId,
                                        sectionId = selectedSectionId,
                                        onSuccess = {
                                            isLoading = false
                                            Toast.makeText(context, "مرحباً بك في تنوير! تم إنشاء حسابك بنجاح ✨", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { err ->
                                            isLoading = false
                                            errorMessage = err
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("submit_register_btn")
                            )
                        }

                        AuthMode.LOGIN_PHONE_PASS -> {
                            // Phone login
                            YemenPhoneInputField(
                                phoneDigits = rawPhoneDigits,
                                onPhoneChange = { rawPhoneDigits = it },
                                phoneValidation = phoneValidation
                            )

                            // Password login with Canvas eye
                            CanvasPasswordInputField(
                                password = password,
                                onPasswordChange = { password = it },
                                isPasswordVisible = isPasswordVisible,
                                onToggleVisibility = { isPasswordVisible = !isPasswordVisible },
                                analysis = null // No strength meter on login
                            )

                            val isLoginReady = rawPhoneDigits.length == 9 && rawPhoneDigits.startsWith("7") && password.isNotBlank()

                            GlassButton(
                                text = if (isLoading) "جاري تسجيل الدخول..." else "تسجيل الدخول 🔓",
                                enabled = isLoginReady && !isLoading,
                                onClick = {
                                    focusManager.clearFocus()
                                    isLoading = true
                                    errorMessage = null
                                    val fullPhone = "+967$rawPhoneDigits"
                                    viewModel.login(
                                        phone = fullPhone,
                                        pass = password.trim(),
                                        onSuccess = {
                                            isLoading = false
                                            Toast.makeText(context, "تم تسجيل الدخول بنجاح! مرحباً بعودتك 🌟", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { err ->
                                            isLoading = false
                                            errorMessage = err
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("submit_login_btn")
                            )
                        }

                        AuthMode.LOGIN_RECOVERY_CODE -> {
                            Column {
                                Text(
                                    text = "الرمز السري للاسترداد (Recovery Code):",
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                GlassTextField(
                                    value = recoveryCodeInput,
                                    onValueChange = { recoveryCodeInput = it.uppercase() },
                                    label = "",
                                    placeholder = "مثال: SEC-8X92-K4M1 أو K4M1",
                                    leadingIcon = Icons.Default.Key
                                )
                                Text(
                                    text = "الرمز السري الذي حصلت عليه عند إنشاء حسابك أول مرة يمكنك من استعادة حسابك مباشرة دون كلمة مرور.",
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            val isRecoveryReady = recoveryCodeInput.trim().length >= 4

                            GlassButton(
                                text = if (isLoading) "جاري التحقق من الرمز..." else "دخول فوري بالرمز السري 🔑",
                                enabled = isRecoveryReady && !isLoading,
                                color = WarmAmber,
                                textColor = TextOnAccent,
                                onClick = {
                                    focusManager.clearFocus()
                                    isLoading = true
                                    errorMessage = null
                                    viewModel.loginWithRecoveryCode(
                                        code = recoveryCodeInput.trim(),
                                        onSuccess = {
                                            isLoading = false
                                            Toast.makeText(context, "تم استعادة وتسجيل الدخول بنجاح! ✨", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { err ->
                                            isLoading = false
                                            errorMessage = err
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("submit_recovery_btn")
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// YEMEN PHONE INPUT FIELD (LOCKED +967 PREFIX, 7XX XXX XXX)
// -------------------------------------------------------------
@Composable
fun YemenPhoneInputField(
    phoneDigits: String,
    onPhoneChange: (String) -> Unit,
    phoneValidation: com.example.util.PhoneValidationResult?,
    modifier: Modifier = Modifier
) {
    val isError = phoneValidation != null && !phoneValidation.isValid && phoneDigits.isNotEmpty()
    val isValid = phoneValidation?.isValid == true

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "رقم الهاتف اليمني (9 أرقام تبدأ بـ 7):",
            fontSize = 13.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // Custom Phone Box with locked +967 and pure formatted digits
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GlassSurface)
                .border(
                    1.dp,
                    when {
                        isError -> RubyRed
                        isValid -> EmeraldGreen
                        else -> GlassBorderSubtle
                    },
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Fixed Country Flag & +967 Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x2200E5FF))
                        .border(1.dp, CyanAccent.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(text = "🇾🇪", fontSize = 15.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "+967",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Digits TextField with Custom Formatter
                BasicTextField(
                    value = phoneDigits,
                    onValueChange = { input ->
                        // Strictly extract only digits, max 9
                        var clean = input.filter { it.isDigit() }.take(9)
                        if (clean.isNotEmpty() && !clean.startsWith("7")) {
                            clean = "7" + clean.take(8)
                        }
                        onPhoneChange(clean)
                    },
                    visualTransformation = YemenPhoneVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    ),
                    cursorBrush = SolidColor(CyanAccent),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("yemen_phone_input"),
                    decorationBox = { innerTextField ->
                        if (phoneDigits.isEmpty()) {
                            Text(
                                text = "7XX XXX XXX (مثال: 777123456)",
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                        }
                        innerTextField()
                    }
                )

                // State Icon
                if (isValid) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "رقم صحيح",
                        tint = EmeraldGreen,
                        modifier = Modifier.size(20.dp)
                    )
                } else if (isError) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "رقم غير مكتمل",
                        tint = RubyRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Live Validation Error or Helper Text
        if (isError && phoneValidation?.errorMessage != null) {
            Text(
                text = phoneValidation.errorMessage,
                color = RubyRed,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        } else if (isValid) {
            Text(
                text = "الرقم مكتمل وصالح: ${phoneValidation.formattedDisplay} ✅",
                color = EmeraldGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }
    }
}

// -------------------------------------------------------------
// VISUAL TRANSFORMATION FOR 7XX XXX XXX (NO CURSOR JUMPING)
// -------------------------------------------------------------
class YemenPhoneVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val formatted = buildString {
            for (i in raw.indices) {
                append(raw[i])
                if ((i == 2 || i == 5) && i != raw.lastIndex) {
                    append(" ")
                }
            }
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return when {
                    offset <= 3 -> offset
                    offset <= 6 -> offset + 1
                    else -> (offset + 2).coerceAtMost(formatted.length)
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                return when {
                    offset <= 3 -> offset
                    offset <= 7 -> offset - 1
                    else -> (offset - 2).coerceAtMost(raw.length)
                }
            }
        }

        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

// -------------------------------------------------------------
// CANVAS PASSWORD INPUT FIELD WITH ANIMATED EYE & STRENGTH GAUGE
// -------------------------------------------------------------
@Composable
fun CanvasPasswordInputField(
    password: String,
    onPasswordChange: (String) -> Unit,
    isPasswordVisible: Boolean,
    onToggleVisibility: () -> Unit,
    analysis: com.example.util.PasswordAnalysisResult?,
    label: String = "كلمة المرور:",
    placeholder: String = "أدخل 8 خانات على الأقل (حروف، أرقام، رموز)",
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // Password Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GlassSurface)
                .border(
                    1.dp,
                    if (analysis != null && analysis.score > 0.7f) EmeraldGreen else GlassBorderSubtle,
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                BasicTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(CyanAccent),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("password_input_field"),
                    decorationBox = { innerTextField ->
                        if (password.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        }
                        innerTextField()
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Canvas Animated Eye Toggle Icon Button
                CanvasAnimatedEyeToggle(
                    isVisible = isPasswordVisible,
                    onClick = onToggleVisibility,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Animated Canvas Password Strength Gauge & Checklist
        if (analysis != null && password.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            CanvasPasswordStrengthMeter(analysis = analysis)
        }
    }
}

// -------------------------------------------------------------
// CANVAS ANIMATED EYE ICON (PUPIL & SLASH ANIMATION)
// -------------------------------------------------------------
@Composable
fun CanvasAnimatedEyeToggle(
    isVisible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val slashProgress by animateFloatAsState(
        targetValue = if (isVisible) 0f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "eyeSlash"
    )

    val pupilScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.4f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "pupilScale"
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(GlassSurfaceLight)
            .clickable { onClick() }
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f

            val eyeColor = if (isVisible) Color(0xFF00E5FF) else Color(0xFF9E9E9E)

            // Outer Eye Arc Profile
            val eyePath = androidx.compose.ui.graphics.Path().apply {
                moveTo(2f, cy)
                quadraticTo(cx, cy - h * 0.42f, w - 2f, cy)
                quadraticTo(cx, cy + h * 0.42f, 2f, cy)
                close()
            }

            drawPath(
                path = eyePath,
                color = eyeColor,
                style = Stroke(width = 2f, cap = StrokeCap.Round)
            )

            // Pupil Circle in Center
            if (pupilScale > 0.1f) {
                drawCircle(
                    color = eyeColor,
                    radius = (w * 0.18f) * pupilScale,
                    center = Offset(cx, cy)
                )
                if (isVisible) {
                    // White eye gleam
                    drawCircle(
                        color = Color.White,
                        radius = w * 0.06f,
                        center = Offset(cx - 2f, cy - 2f)
                    )
                }
            }

            // Slash line through eye when hidden
            if (slashProgress > 0.05f) {
                val start = Offset(4f, 4f)
                val currentEnd = Offset(
                    4f + (w - 8f) * slashProgress,
                    4f + (h - 8f) * slashProgress
                )
                drawLine(
                    color = Color(0xFFFF3366),
                    start = start,
                    end = currentEnd,
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

// -------------------------------------------------------------
// CANVAS PASSWORD STRENGTH METER (ANIMATED GAUGE & METRICS)
// -------------------------------------------------------------
@Composable
fun CanvasPasswordStrengthMeter(
    analysis: com.example.util.PasswordAnalysisResult
) {
    val animatedScore by animateFloatAsState(
        targetValue = analysis.score,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "strengthScore"
    )

    val meterColor = remember(analysis.level) {
        Color(analysis.level.colorHex)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "قوة كلمة المرور: ${analysis.level.label}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = meterColor
            )
            Text(
                text = "${(animatedScore * 100).toInt()}%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = meterColor
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Canvas Multi-Segment Strength Bar
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        ) {
            val w = size.width
            val h = size.height
            val segments = 5
            val gap = 4f
            val segWidth = (w - (segments - 1) * gap) / segments

            for (i in 0 until segments) {
                val segStart = i * (segWidth + gap)
                val segThreshold = (i + 1).toFloat() / segments
                val isFilled = animatedScore >= (i.toFloat() / segments + 0.05f)

                val brush = if (isFilled) {
                    Brush.horizontalGradient(
                        colors = listOf(meterColor.copy(alpha = 0.7f), meterColor),
                        startX = segStart,
                        endX = segStart + segWidth
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF1B2433), Color(0xFF1B2433))
                    )
                }

                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(segStart, 0f),
                    size = Size(segWidth, h),
                    cornerRadius = CornerRadius(4f, 4f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Strength Checklist Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StrengthPill(text = "8+ أحرف", isPassed = analysis.hasMinLength, modifier = Modifier.weight(1f))
            StrengthPill(text = "حروف أبجدية", isPassed = analysis.hasLetters, modifier = Modifier.weight(1f))
            StrengthPill(text = "أرقام (0-9)", isPassed = analysis.hasNumbers, modifier = Modifier.weight(1f))
            StrengthPill(text = "رموز (@#$)", isPassed = analysis.hasSymbols, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = analysis.feedbackMessage,
            fontSize = 11.sp,
            color = if (analysis.isAcceptable) EmeraldGreen else TextSecondary,
            modifier = Modifier.padding(start = 2.dp)
        )
    }
}

@Composable
private fun StrengthPill(
    text: String,
    isPassed: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPassed) Color(0x2200E676) else Color(0x221B2433))
            .border(
                1.dp,
                if (isPassed) EmeraldGreen.copy(alpha = 0.6f) else GlassBorderSubtle,
                RoundedCornerShape(8.dp)
            )
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isPassed) "✓ $text" else "• $text",
            fontSize = 10.sp,
            fontWeight = if (isPassed) FontWeight.Bold else FontWeight.Normal,
            color = if (isPassed) EmeraldGreen else TextMuted,
            textAlign = TextAlign.Center
        )
    }
}
