package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.TanweerDatabase
import com.example.data.model.*
import com.example.data.remote.ConnectionStatus
import com.example.data.repository.TanweerRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class NavigationTab {
    TODAY,
    CALENDAR,
    HOMEWORK,
    EXAMS,
    ISSUES,
    MORE
}

enum class SubScreen {
    NONE,
    GROUPS,
    LIBRARY,
    PDF_VIEWER,
    SCHEDULE,
    TIMELINE,
    PROFILE,
    SEARCH,
    WHAT_DID_I_MISS
}

class TanweerViewModel(application: Application) : AndroidViewModel(application) {
    private val db = TanweerDatabase.getInstance(application)
    val repository = TanweerRepository(db)

    val currentUser = repository.currentUser.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    private val _currentTab = MutableStateFlow(NavigationTab.TODAY)
    val currentTab: StateFlow<NavigationTab> = _currentTab.asStateFlow()

    private val _subScreen = MutableStateFlow(SubScreen.NONE)
    val subScreen: StateFlow<SubScreen> = _subScreen.asStateFlow()

    private val _activeReadingBook = MutableStateFlow<BookItem?>(null)
    val activeReadingBook: StateFlow<BookItem?> = _activeReadingBook.asStateFlow()

    private val _selectedGroupId = MutableStateFlow("")
    val selectedGroupId: StateFlow<String> = _selectedGroupId.asStateFlow()

    private val _selectedDate = MutableStateFlow(getTodayDateString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _isUploadModalOpen = MutableStateFlow(false)
    val isUploadModalOpen: StateFlow<Boolean> = _isUploadModalOpen.asStateFlow()

    private val _uploadInitialSubjectId = MutableStateFlow<String?>(null)
    val uploadInitialSubjectId: StateFlow<String?> = _uploadInitialSubjectId.asStateFlow()

    private val _recoveryCodeDialog = MutableStateFlow<String?>(null)
    val recoveryCodeDialog: StateFlow<String?> = _recoveryCodeDialog.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSyncingBooks = MutableStateFlow(false)
    val isSyncingBooks: StateFlow<Boolean> = _isSyncingBooks.asStateFlow()

    val chatConnectionStatus: StateFlow<ConnectionStatus> = repository.chatManager.status

    init {
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user != null) {
                    if (_selectedGroupId.value.isEmpty()) {
                        _selectedGroupId.value = user.defaultGroupId
                    }
                    // Sync books for user grade from GitHub Releases on start
                    syncBooks(user.gradeId)
                }
            }
        }
    }

    fun setTab(tab: NavigationTab) {
        _currentTab.value = tab
        _subScreen.value = SubScreen.NONE
    }

    fun setSubScreen(screen: SubScreen) {
        _subScreen.value = screen
    }

    fun openBookInPdfReader(book: BookItem) {
        _activeReadingBook.value = book
        _subScreen.value = SubScreen.PDF_VIEWER
    }

    fun closePdfReader() {
        _activeReadingBook.value = null
        _subScreen.value = SubScreen.LIBRARY
    }

    fun setSelectedGroup(groupId: String) {
        _selectedGroupId.value = groupId
    }

    fun setSelectedDate(date: String) {
        _selectedDate.value = date
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun openUploadDialog(subjectId: String? = null) {
        _uploadInitialSubjectId.value = subjectId
        _isUploadModalOpen.value = true
    }

    fun closeUploadDialog() {
        _isUploadModalOpen.value = false
        _uploadInitialSubjectId.value = null
    }

    fun dismissRecoveryDialog() {
        _recoveryCodeDialog.value = null
    }

    fun syncBooks(gradeId: Int? = null, onResult: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            _isSyncingBooks.value = true
            val result = repository.syncBooksFromGitHub(gradeId)
            _isSyncingBooks.value = false
            result.onSuccess { count ->
                onResult?.invoke(true, "تمت مزامنة $count كتاباً دراسياً بنجاح من مستودع tanweer-books ✨")
            }.onFailure { err ->
                onResult?.invoke(false, err.message ?: "تعذرت مزامنة الكتب من GitHub")
            }
        }
    }

    fun connectGroupChat(groupId: String) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            repository.connectToGroupChat(groupId, user.id)
        }
    }

    fun addScheduleSlot(
        dayOfWeek: Int,
        slotOrder: Int,
        subjectId: String,
        subjectName: String,
        subjectIcon: String,
        colorHex: String?,
        startTime: String? = null,
        endTime: String? = null
    ) {
        val user = currentUser.value ?: return
        val groupId = _selectedGroupId.value.ifEmpty { user.defaultGroupId }
        viewModelScope.launch {
            repository.addScheduleSlot(
                groupId = groupId,
                dayOfWeek = dayOfWeek,
                slotOrder = slotOrder,
                subjectId = subjectId,
                subjectName = subjectName,
                subjectIcon = subjectIcon,
                colorHex = colorHex,
                startTime = startTime,
                endTime = endTime
            )
        }
    }

    fun deleteScheduleSlot(dayOfWeek: Int, slotOrder: Int) {
        val user = currentUser.value ?: return
        val groupId = _selectedGroupId.value.ifEmpty { user.defaultGroupId }
        viewModelScope.launch {
            repository.deleteScheduleSlot(groupId, dayOfWeek, slotOrder)
        }
    }

    fun deleteScheduleSlotById(slotId: String) {
        viewModelScope.launch {
            repository.deleteScheduleSlotById(slotId)
        }
    }

    fun getSubjectsForCurrentGrade(): List<com.example.data.model.SubjectItem> {
        val gradeId = currentUser.value?.gradeId ?: 10
        return SchoolHierarchy.getSubjectsForGrade(gradeId)
    }

    fun getScheduleSubjectsForCurrentGrade(): List<com.example.data.model.SubjectItem> {
        val gradeId = currentUser.value?.gradeId ?: 10
        val baseSubjects = SchoolHierarchy.getSubjectsForGrade(gradeId)
            .filter { it.id != "computer" && !it.name.contains("حاسوب") }
        val peSubject = com.example.data.model.SubjectItem("pe", "التربية الرياضية", "🏃‍♂️", "#FF5252")
        val activitySubject = com.example.data.model.SubjectItem("activity", "النشاط اللاصفي", "🎨", "#AB47BC")
        val list = baseSubjects.toMutableList()
        if (list.none { it.id == "pe" }) {
            list.add(peSubject)
        }
        if (list.none { it.id == "activity" }) {
            list.add(activitySubject)
        }
        return list
    }

    fun deleteExam(examId: String) {
        viewModelScope.launch {
            repository.deleteExam(examId)
        }
    }

    fun register(
        fullName: String,
        phone: String,
        pass: String,
        gradeId: Int,
        sectionId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.register(fullName, phone, pass, gradeId, sectionId)
            result.onSuccess { pair ->
                _selectedGroupId.value = pair.first.defaultGroupId
                if (pair.second.isNotEmpty()) {
                    _recoveryCodeDialog.value = pair.second
                }
                syncBooks(pair.first.gradeId)
                onSuccess()
            }.onFailure { err ->
                onError(err.message ?: "فشل إنشاء الحساب")
            }
        }
    }

    fun login(
        phone: String,
        pass: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.login(phone, pass)
            result.onSuccess { user ->
                _selectedGroupId.value = user.defaultGroupId
                syncBooks(user.gradeId)
                onSuccess()
            }.onFailure { err ->
                onError(err.message ?: "فشل تسجيل الدخول")
            }
        }
    }

    fun loginWithRecoveryCode(
        code: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.loginWithRecoveryCode(code)
            result.onSuccess { user ->
                _selectedGroupId.value = user.defaultGroupId
                syncBooks(user.gradeId)
                onSuccess()
            }.onFailure { err ->
                onError(err.message ?: "فشل تسجيل الدخول بالرقم السري")
            }
        }
    }

    fun updateProfile(
        fullName: String?,
        gradeId: Int?,
        sectionId: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.updateProfile(fullName, gradeId, sectionId)
            result.onSuccess { user ->
                _selectedGroupId.value = user.defaultGroupId
                syncBooks(user.gradeId)
                onSuccess()
            }.onFailure { err ->
                onError(err.message ?: "فشل تحديث البيانات")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _selectedGroupId.value = ""
        }
    }

    fun addLesson(
        subjectId: String,
        title: String,
        description: String?,
        date: String = _selectedDate.value
    ) {
        val user = currentUser.value ?: return
        val groupId = _selectedGroupId.value.ifEmpty { user.defaultGroupId }
        viewModelScope.launch {
            val gradeName = SchoolHierarchy.getGradeName(user.gradeId)
            val secAr = SchoolHierarchy.getSectionArabicName(user.sectionId)
            repository.addLesson(
                groupId = groupId,
                date = date,
                subjectId = subjectId,
                title = title,
                description = description,
                authorName = user.fullName,
                authorGradeSection = "$gradeName — $secAr"
            )
            closeUploadDialog()
        }
    }

    fun addHomework(
        subjectId: String,
        title: String,
        details: String?,
        pageNumbers: String?,
        questionNumbers: String?,
        dueDate: String
    ) {
        val user = currentUser.value ?: return
        val groupId = _selectedGroupId.value.ifEmpty { user.defaultGroupId }
        viewModelScope.launch {
            repository.addHomework(
                groupId = groupId,
                studyDate = _selectedDate.value,
                dueDate = dueDate,
                subjectId = subjectId,
                title = title,
                details = details,
                pageNumbers = pageNumbers,
                questionNumbers = questionNumbers
            )
        }
    }

    fun toggleHomework(homeworkId: String, currentStatus: Boolean) {
        viewModelScope.launch {
            repository.toggleHomeworkCompletion(homeworkId, currentStatus)
        }
    }

    fun addExam(
        subjectId: String,
        title: String,
        requiredChapters: String?,
        notes: String?,
        examDate: String
    ) {
        val user = currentUser.value ?: return
        val groupId = _selectedGroupId.value.ifEmpty { user.defaultGroupId }
        viewModelScope.launch {
            repository.addExam(
                groupId = groupId,
                examDate = examDate,
                subjectId = subjectId,
                title = title,
                requiredChapters = requiredChapters,
                notes = notes
            )
        }
    }

    fun addEvent(
        title: String,
        description: String?,
        category: String,
        eventDate: String,
        timeStr: String?,
        location: String?
    ) {
        val user = currentUser.value ?: return
        val groupId = _selectedGroupId.value.ifEmpty { user.defaultGroupId }
        viewModelScope.launch {
            repository.addEvent(
                groupId = groupId,
                eventDate = eventDate,
                timeStr = timeStr,
                title = title,
                description = description,
                category = category,
                location = location
            )
        }
    }

    fun addIssue(
        title: String,
        description: String?,
        subjectId: String? = null,
        homeworkId: String? = null,
        examId: String? = null
    ) {
        val user = currentUser.value ?: return
        val groupId = _selectedGroupId.value.ifEmpty { user.defaultGroupId }
        viewModelScope.launch {
            repository.addIssue(
                groupId = groupId,
                subjectId = subjectId,
                homeworkId = homeworkId,
                examId = examId,
                title = title,
                description = description,
                authorName = user.fullName
            )
        }
    }

    fun sendChatMessage(text: String) {
        val user = currentUser.value ?: return
        val groupId = _selectedGroupId.value.ifEmpty { user.defaultGroupId }
        viewModelScope.launch {
            val gradeName = SchoolHierarchy.getGradeName(user.gradeId)
            val secAr = SchoolHierarchy.getSectionArabicName(user.sectionId)
            repository.sendChatMessage(
                groupId = groupId,
                senderId = user.id,
                senderName = user.fullName,
                senderGradeSection = "$gradeName — $secAr",
                text = text
            )
        }
    }

    companion object {
        fun getTodayDateString(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            return sdf.format(Date())
        }

        fun getDayOfWeekIndex(calendar: Calendar = Calendar.getInstance()): Int {
            return when (calendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SUNDAY -> 0
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 0 // Weekend -> jump to next Sunday
                Calendar.SATURDAY -> 0 // Weekend -> jump to next Sunday
                else -> 0
            }
        }

        fun getNextSchoolDayName(calendar: Calendar = Calendar.getInstance()): String {
            return when (calendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.THURSDAY -> "الأحد القادم (تخطي عطلة الجمعة والسبت)"
                Calendar.FRIDAY -> "الأحد الدراسي القادم"
                Calendar.SATURDAY -> "غدًا الأحد"
                Calendar.SUNDAY -> "غدًا الإثنين"
                Calendar.MONDAY -> "غدًا الثلاثاء"
                Calendar.TUESDAY -> "غدًا الأربعاء"
                Calendar.WEDNESDAY -> "غدًا الخميس"
                else -> "اليوم الدراسي القادم"
            }
        }

        fun getFormattedArabicDate(dateStr: String): String {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val d = sdf.parse(dateStr) ?: return dateStr
                val cal = Calendar.getInstance().apply { time = d }
                val dayName = when (cal.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.SUNDAY -> "الأحد"
                    Calendar.MONDAY -> "الإثنين"
                    Calendar.TUESDAY -> "الثلاثاء"
                    Calendar.WEDNESDAY -> "الأربعاء"
                    Calendar.THURSDAY -> "الخميس"
                    Calendar.FRIDAY -> "الجمعة"
                    Calendar.SATURDAY -> "السبت"
                    else -> ""
                }
                val monthName = when (cal.get(Calendar.MONTH)) {
                    Calendar.JANUARY -> "يناير"
                    Calendar.FEBRUARY -> "فبراير"
                    Calendar.MARCH -> "مارس"
                    Calendar.APRIL -> "أبريل"
                    Calendar.MAY -> "مايو"
                    Calendar.JUNE -> "يونيو"
                    Calendar.JULY -> "يوليو"
                    Calendar.AUGUST -> "أغسطس"
                    Calendar.SEPTEMBER -> "سبتمبر"
                    Calendar.OCTOBER -> "أكتوبر"
                    Calendar.NOVEMBER -> "نوفمبر"
                    Calendar.DECEMBER -> "ديسمبر"
                    else -> ""
                }
                return "$dayName ${cal.get(Calendar.DAY_OF_MONTH)} $monthName ${cal.get(Calendar.YEAR)}"
            } catch (_: Exception) {
                return dateStr
            }
        }
    }
}
