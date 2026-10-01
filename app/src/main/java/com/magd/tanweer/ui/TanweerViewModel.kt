package com.magd.tanweer.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.magd.tanweer.data.local.TanweerDatabase
import com.magd.tanweer.data.model.*
import com.magd.tanweer.data.remote.ConnectionStatus
import com.magd.tanweer.data.repository.SyncResult
import com.magd.tanweer.data.repository.TanweerRepository
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
    SETTINGS,
    SEARCH,
    WHAT_DID_I_MISS
}

class TanweerViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("tanweer_prefs", Context.MODE_PRIVATE)
    private val db = TanweerDatabase.getInstance(application)
    val repository = TanweerRepository(db)

    // App Settings State (Persisted in SharedPreferences)
    // Default Font Scale is 1.0f (Base 12sp default)
    private val _fontSizeScale = MutableStateFlow(prefs.getFloat("font_size_scale", 1.0f))
    val fontSizeScale: StateFlow<Float> = _fontSizeScale.asStateFlow()

    private val _appTheme = MutableStateFlow(prefs.getString("app_theme", "DARK") ?: "DARK")
    val appTheme: StateFlow<String> = _appTheme.asStateFlow()

    private val _autoSyncEnabled = MutableStateFlow(prefs.getBoolean("auto_sync_enabled", true))
    val autoSyncEnabled: StateFlow<Boolean> = _autoSyncEnabled.asStateFlow()

    private val _imageQualitySetting = MutableStateFlow(prefs.getString("image_quality", "BALANCED") ?: "BALANCED")
    val imageQualitySetting: StateFlow<String> = _imageQualitySetting.asStateFlow()

    private val _notifyHomework = MutableStateFlow(prefs.getBoolean("notify_homework", true))
    val notifyHomework: StateFlow<Boolean> = _notifyHomework.asStateFlow()

    private val _notifySchedule = MutableStateFlow(prefs.getBoolean("notify_schedule", true))
    val notifySchedule: StateFlow<Boolean> = _notifySchedule.asStateFlow()

    private val _notifyExams = MutableStateFlow(prefs.getBoolean("notify_exams", true))
    val notifyExams: StateFlow<Boolean> = _notifyExams.asStateFlow()

    private val _hapticsEnabled = MutableStateFlow(prefs.getBoolean("haptics_enabled", true))
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    fun setFontScale(scale: Float) {
        _fontSizeScale.value = scale
        prefs.edit().putFloat("font_size_scale", scale).apply()
    }

    fun setAppTheme(theme: String) {
        _appTheme.value = theme
        prefs.edit().putString("app_theme", theme).apply()
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        _autoSyncEnabled.value = enabled
        prefs.edit().putBoolean("auto_sync_enabled", enabled).apply()
    }

    fun setImageQualitySetting(quality: String) {
        _imageQualitySetting.value = quality
        prefs.edit().putString("image_quality", quality).apply()
    }

    fun setNotifyHomework(notify: Boolean) {
        _notifyHomework.value = notify
        prefs.edit().putBoolean("notify_homework", notify).apply()
    }

    fun setNotifySchedule(notify: Boolean) {
        _notifySchedule.value = notify
        prefs.edit().putBoolean("notify_schedule", notify).apply()
    }

    fun setNotifyExams(notify: Boolean) {
        _notifyExams.value = notify
        prefs.edit().putBoolean("notify_exams", notify).apply()
    }

    fun setHapticsEnabled(enabled: Boolean) {
        _hapticsEnabled.value = enabled
        prefs.edit().putBoolean("haptics_enabled", enabled).apply()
    }

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

    // -------------------------------------------------------------
    // PER-SCREEN SYNC STATES
    // -------------------------------------------------------------
    private val _homeSyncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val homeSyncState: StateFlow<SyncState> = _homeSyncState.asStateFlow()

    private val _scheduleSyncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val scheduleSyncState: StateFlow<SyncState> = _scheduleSyncState.asStateFlow()

    private val _homeworkSyncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val homeworkSyncState: StateFlow<SyncState> = _homeworkSyncState.asStateFlow()

    private val _examsSyncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val examsSyncState: StateFlow<SyncState> = _examsSyncState.asStateFlow()

    private val _calendarSyncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val calendarSyncState: StateFlow<SyncState> = _calendarSyncState.asStateFlow()

    private val _issuesSyncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val issuesSyncState: StateFlow<SyncState> = _issuesSyncState.asStateFlow()

    private val _librarySyncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val librarySyncState: StateFlow<SyncState> = _librarySyncState.asStateFlow()

    val isSyncingBooks: StateFlow<Boolean> = _librarySyncState
        .map { it is SyncState.Loading || it is SyncState.Refreshing }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _groupsSyncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val groupsSyncState: StateFlow<SyncState> = _groupsSyncState.asStateFlow()

    val chatConnectionStatus: StateFlow<ConnectionStatus> = repository.chatManager.status

    init {
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user != null) {
                    if (_selectedGroupId.value.isEmpty()) {
                        _selectedGroupId.value = user.defaultGroupId
                    }
                    syncInitialData(user.defaultGroupId, _selectedDate.value)
                    syncBooks(user.gradeId)
                }
            }
        }
    }

    private fun getActiveGroupId(): String {
        return _selectedGroupId.value.ifEmpty { currentUser.value?.defaultGroupId ?: "" }
    }

    // =========================================================================
    // NAVIGATION & ON-DEMAND SYNCING
    // =========================================================================

    fun setTab(tab: NavigationTab) {
        _currentTab.value = tab
        _subScreen.value = SubScreen.NONE

        val groupId = getActiveGroupId()
        when (tab) {
            NavigationTab.TODAY -> syncHome(groupId, _selectedDate.value, isRefresh = false)
            NavigationTab.HOMEWORK -> syncHomeworks(groupId, isRefresh = false)
            NavigationTab.EXAMS -> syncExams(groupId, isRefresh = false)
            NavigationTab.CALENDAR -> syncCalendar(groupId, isRefresh = false)
            NavigationTab.ISSUES -> syncIssues(groupId, isRefresh = false)
            NavigationTab.MORE -> {}
        }
    }

    fun setSubScreen(screen: SubScreen) {
        _subScreen.value = screen
        val groupId = getActiveGroupId()
        when (screen) {
            SubScreen.SCHEDULE -> syncSchedule(groupId, isRefresh = false)
            SubScreen.GROUPS -> syncGroups(isRefresh = false)
            SubScreen.LIBRARY -> syncBooks(currentUser.value?.gradeId ?: 10, isRefresh = false)
            SubScreen.TIMELINE -> syncHome(groupId, _selectedDate.value, isRefresh = false)
            else -> {}
        }
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
        syncInitialData(groupId, _selectedDate.value)
    }

    fun setSelectedDate(date: String) {
        _selectedDate.value = date
        syncHome(getActiveGroupId(), date, isRefresh = false)
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

    // =========================================================================
    // SYNC METHODS (Server -> Room -> Compose)
    // =========================================================================

    fun syncInitialData(groupId: String, date: String) {
        viewModelScope.launch {
            _homeSyncState.value = SyncState.Loading
            when (val result = repository.syncInitial(groupId, date)) {
                is SyncResult.Success -> {
                    _homeSyncState.value = SyncState.Success()
                    _scheduleSyncState.value = SyncState.Success()
                }
                is SyncResult.Offline -> {
                    _homeSyncState.value = SyncState.OfflineCached()
                }
                is SyncResult.Error -> {
                    _homeSyncState.value = SyncState.Error(result.message, result.isNetworkError)
                }
            }
        }
    }

    fun syncHome(groupId: String = getActiveGroupId(), date: String = _selectedDate.value, isRefresh: Boolean = false) {
        viewModelScope.launch {
            _homeSyncState.value = if (isRefresh) SyncState.Refreshing else SyncState.Loading
            when (val result = repository.syncDay(groupId, date)) {
                is SyncResult.Success -> _homeSyncState.value = SyncState.Success()
                is SyncResult.Offline -> _homeSyncState.value = SyncState.OfflineCached()
                is SyncResult.Error -> _homeSyncState.value = SyncState.Error(result.message, result.isNetworkError)
            }
        }
    }

    fun syncSchedule(groupId: String = getActiveGroupId(), isRefresh: Boolean = false) {
        viewModelScope.launch {
            _scheduleSyncState.value = if (isRefresh) SyncState.Refreshing else SyncState.Loading
            when (val result = repository.syncSchedule(groupId)) {
                is SyncResult.Success -> _scheduleSyncState.value = SyncState.Success()
                is SyncResult.Offline -> _scheduleSyncState.value = SyncState.OfflineCached()
                is SyncResult.Error -> _scheduleSyncState.value = SyncState.Error(result.message, result.isNetworkError)
            }
        }
    }

    fun syncHomeworks(groupId: String = getActiveGroupId(), isRefresh: Boolean = false) {
        viewModelScope.launch {
            _homeworkSyncState.value = if (isRefresh) SyncState.Refreshing else SyncState.Loading
            when (val result = repository.syncHomeworks(groupId)) {
                is SyncResult.Success -> _homeworkSyncState.value = SyncState.Success()
                is SyncResult.Offline -> _homeworkSyncState.value = SyncState.OfflineCached()
                is SyncResult.Error -> _homeworkSyncState.value = SyncState.Error(result.message, result.isNetworkError)
            }
        }
    }

    fun syncExams(groupId: String = getActiveGroupId(), isRefresh: Boolean = false) {
        viewModelScope.launch {
            _examsSyncState.value = if (isRefresh) SyncState.Refreshing else SyncState.Loading
            when (val result = repository.syncExams(groupId)) {
                is SyncResult.Success -> _examsSyncState.value = SyncState.Success()
                is SyncResult.Offline -> _examsSyncState.value = SyncState.OfflineCached()
                is SyncResult.Error -> _examsSyncState.value = SyncState.Error(result.message, result.isNetworkError)
            }
        }
    }

    fun syncCalendar(groupId: String = getActiveGroupId(), isRefresh: Boolean = false) {
        viewModelScope.launch {
            _calendarSyncState.value = if (isRefresh) SyncState.Refreshing else SyncState.Loading
            when (val result = repository.syncEvents(groupId)) {
                is SyncResult.Success -> _calendarSyncState.value = SyncState.Success()
                is SyncResult.Offline -> _calendarSyncState.value = SyncState.OfflineCached()
                is SyncResult.Error -> _calendarSyncState.value = SyncState.Error(result.message, result.isNetworkError)
            }
        }
    }

    fun syncIssues(groupId: String = getActiveGroupId(), isRefresh: Boolean = false) {
        viewModelScope.launch {
            _issuesSyncState.value = if (isRefresh) SyncState.Refreshing else SyncState.Loading
            when (val result = repository.syncIssues(groupId)) {
                is SyncResult.Success -> _issuesSyncState.value = SyncState.Success()
                is SyncResult.Offline -> _issuesSyncState.value = SyncState.OfflineCached()
                is SyncResult.Error -> _issuesSyncState.value = SyncState.Error(result.message, result.isNetworkError)
            }
        }
    }

    fun syncGroups(isRefresh: Boolean = false) {
        viewModelScope.launch {
            _groupsSyncState.value = if (isRefresh) SyncState.Refreshing else SyncState.Loading
            when (val result = repository.syncGroups()) {
                is SyncResult.Success -> _groupsSyncState.value = SyncState.Success()
                is SyncResult.Offline -> _groupsSyncState.value = SyncState.OfflineCached()
                is SyncResult.Error -> _groupsSyncState.value = SyncState.Error(result.message, result.isNetworkError)
            }
        }
    }

    fun syncBooks(gradeId: Int? = null, isRefresh: Boolean = false, onResult: ((Boolean, String) -> Unit)? = null) {
        val targetGrade = gradeId ?: currentUser.value?.gradeId ?: 10
        viewModelScope.launch {
            _librarySyncState.value = if (isRefresh) SyncState.Refreshing else SyncState.Loading
            when (val result = repository.syncBooks(targetGrade)) {
                is SyncResult.Success -> {
                    _librarySyncState.value = SyncState.Success()
                    onResult?.invoke(true, "تمت مزامنة الكتب الدراسية بنجاح ✨")
                }
                is SyncResult.Offline -> {
                    _librarySyncState.value = SyncState.OfflineCached()
                    onResult?.invoke(true, "عرض الكتب المحفوظة أوفلاين")
                }
                is SyncResult.Error -> {
                    _librarySyncState.value = SyncState.Error(result.message, result.isNetworkError)
                    onResult?.invoke(false, result.message)
                }
            }
        }
    }

    // Refresh wrappers called by UI components
    fun refreshHome() = syncHome(isRefresh = true)
    fun refreshSchedule() = syncSchedule(isRefresh = true)
    fun refreshHomeworks() = syncHomeworks(isRefresh = true)
    fun refreshExams() = syncExams(isRefresh = true)
    fun refreshCalendar() = syncCalendar(isRefresh = true)
    fun refreshIssues() = syncIssues(isRefresh = true)
    fun refreshGroups() = syncGroups(isRefresh = true)
    fun refreshBooks() = syncBooks(isRefresh = true)

    // =========================================================================
    // ACTIONS & MUTATIONS
    // =========================================================================

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
        val groupId = getActiveGroupId()
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
        val groupId = getActiveGroupId()
        viewModelScope.launch {
            repository.deleteScheduleSlot(groupId, dayOfWeek, slotOrder)
        }
    }

    fun deleteScheduleSlotById(slotId: String) {
        viewModelScope.launch {
            repository.deleteScheduleSlotById(slotId)
        }
    }

    fun getSubjectsForCurrentGrade(): List<SubjectItem> {
        val gradeId = currentUser.value?.gradeId ?: 10
        return SchoolHierarchy.getSubjectsForGrade(gradeId)
    }

    fun getScheduleSubjectsForCurrentGrade(): List<SubjectItem> {
        val gradeId = currentUser.value?.gradeId ?: 10
        val baseSubjects = SchoolHierarchy.getSubjectsForGrade(gradeId)
            .filter { it.id != "computer" && !it.name.contains("حاسوب") }
        val peSubject = SubjectItem("pe", "التربية الرياضية", "🏃‍♂️", "#FF5252")
        val activitySubject = SubjectItem("activity", "النشاط اللاصفي", "🎨", "#AB47BC")
        val list = baseSubjects.toMutableList()
        if (list.none { it.id == "pe" }) {
            list.add(peSubject)
        }
        if (list.none { it.id == "activity" }) {
            list.add(activitySubject)
        }
        return list
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
                syncInitialData(pair.first.defaultGroupId, _selectedDate.value)
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
                syncInitialData(user.defaultGroupId, _selectedDate.value)
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
                syncInitialData(user.defaultGroupId, _selectedDate.value)
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
                syncInitialData(user.defaultGroupId, _selectedDate.value)
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
            _homeSyncState.value = SyncState.Idle
            _scheduleSyncState.value = SyncState.Idle
            _homeworkSyncState.value = SyncState.Idle
            _examsSyncState.value = SyncState.Idle
            _calendarSyncState.value = SyncState.Idle
            _issuesSyncState.value = SyncState.Idle
            _librarySyncState.value = SyncState.Idle
            _groupsSyncState.value = SyncState.Idle
        }
    }

    fun addLesson(
        subjectId: String,
        title: String,
        description: String?,
        date: String = _selectedDate.value
    ) {
        addLessonWithPages(subjectId, title, description, date, emptyList())
    }

    fun addLessonWithPages(
        subjectId: String,
        title: String,
        description: String?,
        date: String = _selectedDate.value,
        pages: List<com.magd.tanweer.util.ProcessedPageResult>
    ) {
        val user = currentUser.value ?: return
        val groupId = getActiveGroupId()
        viewModelScope.launch {
            val gradeName = SchoolHierarchy.getGradeName(user.gradeId)
            val secAr = SchoolHierarchy.getSectionArabicName(user.sectionId)
            repository.addLessonWithPages(
                groupId = groupId,
                date = date,
                subjectId = subjectId,
                title = title,
                description = description,
                authorName = user.fullName,
                authorGradeSection = "$gradeName — $secAr",
                pages = pages
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
        dueDate: String,
        taskType: String = "HOMEWORK",
        pages: List<com.magd.tanweer.util.ProcessedPageResult> = emptyList(),
        onComplete: (() -> Unit)? = null
    ) {
        val user = currentUser.value ?: return
        val groupId = getActiveGroupId()
        viewModelScope.launch {
            if (pages.isNotEmpty()) {
                repository.addHomeworkWithImages(
                    groupId = groupId,
                    studyDate = _selectedDate.value,
                    dueDate = dueDate,
                    subjectId = subjectId,
                    title = title,
                    details = details,
                    pageNumbers = pageNumbers,
                    questionNumbers = questionNumbers,
                    taskType = taskType,
                    pages = pages
                )
            } else {
                repository.addHomework(
                    groupId = groupId,
                    studyDate = _selectedDate.value,
                    dueDate = dueDate,
                    subjectId = subjectId,
                    title = title,
                    details = details,
                    pageNumbers = pageNumbers,
                    questionNumbers = questionNumbers,
                    taskType = taskType
                )
            }
            onComplete?.invoke()
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
        val groupId = getActiveGroupId()
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

    fun deleteExam(examId: String) {
        viewModelScope.launch {
            repository.deleteExamById(examId)
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
        val groupId = getActiveGroupId()
        viewModelScope.launch {
            repository.addEvent(
                groupId = groupId,
                eventDate = eventDate,
                timeStr = timeStr,
                title = title,
                description = description,
                category = category
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
        val groupId = getActiveGroupId()
        viewModelScope.launch {
            repository.createIssue(
                groupId = groupId,
                title = title,
                description = description,
                subjectId = subjectId,
                authorName = user.fullName,
                homeworkId = homeworkId,
                examId = examId
            )
        }
    }

    fun syncIssueDetails(issueId: String) {
        viewModelScope.launch {
            repository.syncIssueDetails(issueId)
        }
    }

    fun addIssueComment(issueId: String, comment: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.addIssueComment(issueId, comment)
            onDone()
        }
    }

    fun markBestAnswer(issueId: String, commentId: String) {
        viewModelScope.launch {
            repository.markBestAnswer(issueId, commentId)
        }
    }

    fun retrySyncHomework(homeworkId: String) {
        viewModelScope.launch {
            repository.retrySyncHomework(homeworkId)
        }
    }

    fun retrySyncComment(issueId: String, commentId: String, commentText: String) {
        viewModelScope.launch {
            repository.retrySyncIssueComment(issueId, commentId, commentText)
        }
    }

    fun joinGroup(groupId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = repository.joinGroup(groupId)
            result.onSuccess {
                onSuccess()
            }.onFailure {
                onError(it.message ?: "فشل الانضمام للمجموعة")
            }
        }
    }

    fun sendChatMessage(text: String, targetGroupId: String? = null) {
        val user = currentUser.value ?: return
        val groupId = targetGroupId ?: getActiveGroupId()
        val cleanText = text.trim()
        if (cleanText.isBlank() || groupId.isBlank()) return
        viewModelScope.launch {
            val gradeName = SchoolHierarchy.getGradeName(user.gradeId)
            val secAr = SchoolHierarchy.getSectionArabicName(user.sectionId)
            repository.sendChatMessage(
                groupId = groupId,
                senderId = user.id,
                senderName = user.fullName,
                senderGradeSection = "$gradeName — $secAr",
                text = cleanText
            )
        }
    }

    fun retryChatMessage(message: ChatMessageItem) {
        viewModelScope.launch {
            repository.retryChatMessage(message)
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
                Calendar.FRIDAY -> 0
                Calendar.SATURDAY -> 0
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
