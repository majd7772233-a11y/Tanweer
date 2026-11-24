package com.magd.tanweer.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.magd.tanweer.data.local.TanweerDatabase
import com.magd.tanweer.data.model.*
import com.magd.tanweer.data.remote.ConnectionStatus
import com.magd.tanweer.data.remote.NetworkModule
import com.magd.tanweer.data.repository.SyncResult
import com.magd.tanweer.data.repository.TanweerRepository
import com.magd.tanweer.util.AcademicSessionSelection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

enum class GroupSyncSource { CONTENTS, HOME, SCHEDULE, HOMEWORKS, EXAMS, CALENDAR, ISSUES, EVENTS }

enum class SubScreen {
    NONE,
    EXTRA_SECTIONS_HUB,
    GROUPS,
    LIBRARY,
    PDF_VIEWER,
    SCHEDULE,
    TIMELINE,
    PROFILE,
    SETTINGS,
    SEARCH,
    WHAT_DID_I_MISS,
    SUBJECT_KNOWLEDGE_BASE,
    ACADEMIC_HISTORY,
    TEACHER_DASHBOARD,
    MODERATOR_DASHBOARD,
    ADMIN_DASHBOARD,
    COMMUNITY_DECISIONS,
    EVENTS,
    VERSION_CHECK,
    STORAGE_MANAGER
}

class TanweerViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("tanweer_prefs", Context.MODE_PRIVATE)
    private val db = TanweerDatabase.getInstance(application)
    val repository = TanweerRepository(db)

    // App Settings State (Persisted in SharedPreferences)
    // Supported base sizes are 12sp, 14sp and 16sp; normalize legacy values on restore.
    private val _fontSizeScale = MutableStateFlow(
        com.magd.tanweer.ui.theme.normalizeTanweerFontScale(
            prefs.getFloat("font_size_scale", 1.0f)
        )
    )
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

    private val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private var currentActiveUserId: String? = null

    private val _isNewAccountOrFirstLaunch = MutableStateFlow(false)
    val isNewAccountOrFirstLaunch: StateFlow<Boolean> = _isNewAccountOrFirstLaunch.asStateFlow()

    private val _lastOpenDate = MutableStateFlow(todayDateStr)
    val lastOpenDate: StateFlow<String> = _lastOpenDate.asStateFlow()

    private val _shouldShowCatchUpCard = MutableStateFlow(false)
    val shouldShowCatchUpCard: StateFlow<Boolean> = _shouldShowCatchUpCard.asStateFlow()

    private val _previousSessionTimestamp = MutableStateFlow(
        prefs.getLong("last_active_time", System.currentTimeMillis())
    )
    val previousSessionTimestamp: StateFlow<Long> = _previousSessionTimestamp.asStateFlow()

    private val _isCheckingAuth = MutableStateFlow(true)
    val isCheckingAuth: StateFlow<Boolean> = _isCheckingAuth.asStateFlow()
    private val localSessionReady = CompletableDeferred<Unit>()
    @Volatile private var dataGenerationVerified = false

    fun updateUserSessionState(userId: String) {
        if (userId.isBlank()) return
        currentActiveUserId = userId
        val userDateKey = "last_open_date_$userId"
        val storedDate = prefs.getString(userDateKey, null)
        val isFirstForUser = storedDate == null

        _isNewAccountOrFirstLaunch.value = isFirstForUser

        if (isFirstForUser) {
            // New user account on this device: initialize to today and do NOT show absence catch-up card
            _lastOpenDate.value = todayDateStr
            _shouldShowCatchUpCard.value = false
            prefs.edit().putString(userDateKey, todayDateStr).apply()
        } else {
            _lastOpenDate.value = storedDate ?: todayDateStr
            // Show Catch-up card only if user's last recorded visit was before today
            _shouldShowCatchUpCard.value = (storedDate != null && storedDate < todayDateStr)
            // Save today's date for this user
            prefs.edit().putString(userDateKey, todayDateStr).apply()
        }
    }

    private fun restoreAcademicSelection(user: User, resetGroup: Boolean = false) {
        val groupKey = "selected_group_id_${user.id}"
        val savedGroupId = prefs.getString(groupKey, null)
        val groupId = if (resetGroup) {
            AcademicSessionSelection.resolveGroupId(null, user.defaultGroupId)
        } else {
            AcademicSessionSelection.resolveGroupId(savedGroupId, user.defaultGroupId)
        }
        _selectedGroupId.value = groupId

        val dateKey = "selected_study_date_${user.id}"
        val savedDate = prefs.getString(dateKey, null)
        val studyDate = AcademicSessionSelection.resolveStudyDate(savedDate, todayDateStr)
        _selectedDate.value = studyDate

        prefs.edit()
            .putString(groupKey, groupId)
            .putString(dateKey, studyDate)
            .commit()
    }

    private fun persistSelectedGroup(groupId: String) {
        val userId = currentUser.value?.id ?: currentActiveUserId ?: return
        prefs.edit().putString("selected_group_id_${userId}", groupId).commit()
    }

    private fun persistSelectedStudyDate(date: String) {
        val userId = currentUser.value?.id ?: currentActiveUserId ?: return
        prefs.edit().putString("selected_study_date_${userId}", date).commit()
    }

    fun dismissCatchUpCard() {
        _shouldShowCatchUpCard.value = false
        val userId = currentActiveUserId ?: currentUser.value?.id
        if (!userId.isNullOrBlank()) {
            prefs.edit().putString("last_open_date_$userId", todayDateStr).apply()
        }
        prefs.edit().putString("last_open_date", todayDateStr).apply()
    }

    init {
        val lastActive = prefs.getLong("last_active_time", System.currentTimeMillis())
        _previousSessionTimestamp.value = lastActive
        prefs.edit()
            .putLong("previous_session_time", lastActive)
            .putLong("last_active_time", System.currentTimeMillis())
            .apply()

        repository.mutationQueue.isAutoSyncEnabled = _autoSyncEnabled.value

        viewModelScope.launch {
            try {
                val user = db.userDao().getUserSync()
                if (user != null) {
                    NetworkModule.setAuthToken(user.token)
                    updateUserSessionState(user.id)
                }
            } catch (e: Exception) {
                android.util.Log.e("TanweerViewModel", "Failed restoring local session", e)
            } finally {
                // Startup synchronization must not race the first local session/token read.
                localSessionReady.complete(Unit)
            }

            // Observe future user changes (e.g., login or account switch).
            // The startup coordinator below decides when the app may leave the splash screen.
            repository.currentUser.collect { u ->
                if (u != null && u.id != currentActiveUserId) {
                    updateUserSessionState(u.id)
                }
            }
        }
    }

    fun setFontScale(scale: Float) {
        val normalizedScale = com.magd.tanweer.ui.theme.normalizeTanweerFontScale(scale)
        _fontSizeScale.value = normalizedScale
        prefs.edit().putFloat("font_size_scale", normalizedScale).apply()
    }

    fun setAppTheme(theme: String) {
        _appTheme.value = theme
        prefs.edit().putString("app_theme", theme).apply()
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        _autoSyncEnabled.value = enabled
        prefs.edit().putBoolean("auto_sync_enabled", enabled).apply()
        repository.mutationQueue.isAutoSyncEnabled = enabled
    }

    fun setImageQualitySetting(quality: String) {
        _imageQualitySetting.value = quality
        prefs.edit().putString("image_quality", quality).apply()
    }

    fun setNotifyHomework(notify: Boolean) {
        _notifyHomework.value = notify
        val app = getApplication<Application>()
        com.magd.tanweer.data.notifications.TanweerNotificationManager
            .setHomeworkNotificationsEnabled(app, notify)
    }

    fun setNotifySchedule(notify: Boolean) {
        _notifySchedule.value = notify
        val app = getApplication<Application>()
        com.magd.tanweer.data.notifications.TanweerNotificationManager
            .setScheduleNotificationsEnabled(app, notify)
    }

    fun setNotifyExams(notify: Boolean) {
        _notifyExams.value = notify
        val app = getApplication<Application>()
        com.magd.tanweer.data.notifications.TanweerNotificationManager
            .setExamNotificationsEnabled(app, notify)
    }

    fun triggerHaptic(type: com.magd.tanweer.util.TanweerHapticManager.HapticType = com.magd.tanweer.util.TanweerHapticManager.HapticType.CLICK) {
        com.magd.tanweer.util.TanweerHapticManager.performHaptic(getApplication(), _hapticsEnabled.value, type)
    }

    fun setHapticsEnabled(enabled: Boolean) {
        _hapticsEnabled.value = enabled
        prefs.edit().putBoolean("haptics_enabled", enabled).apply()
        if (enabled) {
            com.magd.tanweer.util.TanweerHapticManager.performHaptic(
                getApplication(),
                true,
                com.magd.tanweer.util.TanweerHapticManager.HapticType.TOGGLE
            )
        }
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

    private val _activeReadingInitialPage = MutableStateFlow<Int?>(null)
    val activeReadingInitialPage: StateFlow<Int?> = _activeReadingInitialPage.asStateFlow()

    private var pdfReaderReturnDestination: PdfReaderReturnDestination? = null

    private val _selectedGroupId = MutableStateFlow("")
    val selectedGroupId: StateFlow<String> = _selectedGroupId.asStateFlow()

    private val _selectedDate = MutableStateFlow(getTodayDateString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _isUploadModalOpen = MutableStateFlow(false)
    val isUploadModalOpen: StateFlow<Boolean> = _isUploadModalOpen.asStateFlow()

    private val _uploadInitialSubjectId = MutableStateFlow<String?>(null)
    val uploadInitialSubjectId: StateFlow<String?> = _uploadInitialSubjectId.asStateFlow()

    // Add Homework Dialog trigger & initial date from Calendar or context
    private val _isAddHomeworkModalOpen = MutableStateFlow(false)
    val isAddHomeworkModalOpen: StateFlow<Boolean> = _isAddHomeworkModalOpen.asStateFlow()

    private val _initialHomeworkDueDate = MutableStateFlow<String?>(null)
    val initialHomeworkDueDate: StateFlow<String?> = _initialHomeworkDueDate.asStateFlow()

    // Add Exam Dialog trigger & initial date
    private val _isAddExamModalOpen = MutableStateFlow(false)
    val isAddExamModalOpen: StateFlow<Boolean> = _isAddExamModalOpen.asStateFlow()

    private val _initialExamDate = MutableStateFlow<String?>(null)
    val initialExamDate: StateFlow<String?> = _initialExamDate.asStateFlow()

    // Add Event Dialog trigger & initial date
    private val _isAddEventModalOpen = MutableStateFlow(false)
    val isAddEventModalOpen: StateFlow<Boolean> = _isAddEventModalOpen.asStateFlow()

    private val _initialEventDate = MutableStateFlow<String?>(null)
    val initialEventDate: StateFlow<String?> = _initialEventDate.asStateFlow()

    // Deep link target for Groups Chat from Search
    private val _pendingDeepLinkChatGroupId = MutableStateFlow<String?>(null)
    val pendingDeepLinkChatGroupId: StateFlow<String?> = _pendingDeepLinkChatGroupId.asStateFlow()

    private val _pendingDeepLinkMessageId = MutableStateFlow<String?>(null)
    val pendingDeepLinkMessageId: StateFlow<String?> = _pendingDeepLinkMessageId.asStateFlow()

    private val _recoveryCodeDialog = MutableStateFlow<String?>(null)
    val recoveryCodeDialog: StateFlow<String?> = _recoveryCodeDialog.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedKnowledgeBaseSubjectId = MutableStateFlow<String?>(null)
    val selectedKnowledgeBaseSubjectId: StateFlow<String?> = _selectedKnowledgeBaseSubjectId.asStateFlow()

    private val _selectedAcademicYear = MutableStateFlow(
        com.magd.tanweer.util.AcademicYearManager.getCurrentAcademicYear()
    )
    val selectedAcademicYear: StateFlow<String> = _selectedAcademicYear.asStateFlow()

    private val _academicArchiveSyncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val academicArchiveSyncState: StateFlow<SyncState> = _academicArchiveSyncState.asStateFlow()

    fun openSubjectKnowledgeBase(subjectId: String? = null) {
        _selectedKnowledgeBaseSubjectId.value = subjectId
        setSubScreen(SubScreen.SUBJECT_KNOWLEDGE_BASE)
    }

    fun setSelectedAcademicYear(year: String) {
        _selectedAcademicYear.value = year
    }

    fun submitCorrection(
        contentId: String,
        groupId: String,
        fieldName: String,
        originalValue: String,
        proposedValue: String,
        reason: String?,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.submitCorrection(contentId, groupId, fieldName, originalValue, proposedValue, reason)
            if (res.isSuccess) {
                onResult(true, "تم تقديم اقتراح التصحيح بنجاح! وهو قيد المراجعة 📩")
            } else {
                onResult(false, res.exceptionOrNull()?.localizedMessage ?: "تعذر إرسال التصحيح")
            }
        }
    }

    // -------------------------------------------------------------
    // PER-SCREEN SYNC STATES
    // -------------------------------------------------------------
    private val _groupSyncScopeIds = MutableStateFlow<Map<GroupSyncSource, String>>(emptyMap())
    val groupSyncScopeIds: StateFlow<Map<GroupSyncSource, String>> = _groupSyncScopeIds.asStateFlow()
    private val groupSyncGenerations = mutableMapOf<GroupSyncSource, Int>()

    private val _sectionContentsSyncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val sectionContentsSyncState: StateFlow<SyncState> = _sectionContentsSyncState.asStateFlow()

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
    private val _calendarSyncMonthScope = MutableStateFlow<String?>(null)
    val calendarSyncMonthScope: StateFlow<String?> = _calendarSyncMonthScope.asStateFlow()

    @Volatile private var dayDetailsSyncRequestGeneration = 0
    private val _dayDetailsSyncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val dayDetailsSyncState: StateFlow<SyncState> = _dayDetailsSyncState.asStateFlow()
    private val _dayDetailsSyncGroupId = MutableStateFlow<String?>(null)
    val dayDetailsSyncGroupId: StateFlow<String?> = _dayDetailsSyncGroupId.asStateFlow()
    private val _dayDetailsSyncDate = MutableStateFlow<String?>(null)
    val dayDetailsSyncDate: StateFlow<String?> = _dayDetailsSyncDate.asStateFlow()

    private val _issuesSyncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val issuesSyncState: StateFlow<SyncState> = _issuesSyncState.asStateFlow()

    private val _eventsSyncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val eventsSyncState: StateFlow<SyncState> = _eventsSyncState.asStateFlow()

    private val _librarySyncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val librarySyncState: StateFlow<SyncState> = _librarySyncState.asStateFlow()
    private val _librarySyncGradeId = MutableStateFlow<Int?>(null)
    val librarySyncGradeId: StateFlow<Int?> = _librarySyncGradeId.asStateFlow()
    @Volatile private var librarySyncRequestGeneration = 0

    val isSyncingBooks: StateFlow<Boolean> = _librarySyncState
        .map { it is SyncState.Loading || it is SyncState.Refreshing }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _groupsSyncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val groupsSyncState: StateFlow<SyncState> = _groupsSyncState.asStateFlow()

    @Volatile private var chatHistoryRequestGeneration = 0L
    private val _chatHistorySyncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val chatHistorySyncState: StateFlow<SyncState> = _chatHistorySyncState.asStateFlow()
    private val _chatHistorySyncGroupId = MutableStateFlow<String?>(null)
    val chatHistorySyncGroupId: StateFlow<String?> = _chatHistorySyncGroupId.asStateFlow()

    @Volatile private var searchChatHistoryRequestGeneration = 0L
    private val _searchChatHistorySyncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val searchChatHistorySyncState: StateFlow<SyncState> = _searchChatHistorySyncState.asStateFlow()
    private val _searchChatHistorySyncGroupId = MutableStateFlow<String?>(null)
    val searchChatHistorySyncGroupId: StateFlow<String?> = _searchChatHistorySyncGroupId.asStateFlow()

    val chatConnectionStatus: StateFlow<ConnectionStatus> = repository.chatManager.status
    private val dataGenerationReady = CompletableDeferred<Unit>()
    private val dataGenerationMutex = Mutex()
    private var hasSeenInitialResume = false
    private var lastForegroundGenerationCheckAt = 0L

    init {
        repository.mutationQueue.isDrainAllowed = false

        viewModelScope.launch {
            localSessionReady.await()
            try {
                verifyServerDataGeneration()
            } catch (e: Exception) {
                android.util.Log.w("TanweerViewModel", "Server data-generation verification failed; local cache is retained", e)
            } finally {
                dataGenerationReady.complete(Unit)
            }
        }

        viewModelScope.launch {
            localSessionReady.await()
            dataGenerationReady.await()
            currentUser
                .distinctUntilChangedBy { it?.id }
                .collect { user ->
                    if (user == null) {
                        dataGenerationVerified = false
                        repository.mutationQueue.isDrainAllowed = false
                        resetUserScopedUiState()
                        _isCheckingAuth.value = false
                    } else {
                        _isCheckingAuth.value = true
                        restoreAcademicSelection(user)
                        try {
                            if (!dataGenerationVerified) {
                                val purged = verifyServerDataGeneration()
                                if (purged) {
                                    _isCheckingAuth.value = false
                                    return@collect
                                }
                            }

                            if (dataGenerationVerified) {
                                repository.mutationQueue.isDrainAllowed = true
                                val outboxResult = repository.processOutboxQueue()
                                outboxResult.exceptionOrNull()?.let {
                                    android.util.Log.w("TanweerViewModel", "Startup outbox drain was incomplete", it)
                                }
                            } else {
                                repository.mutationQueue.isDrainAllowed = false
                                android.util.Log.w("TanweerViewModel", "Skipping startup outbox drain because server data generation was not verified")
                            }

                            val syncResult = repository.syncInitial(getActiveGroupId(), _selectedDate.value)
                            val refreshedUser = db.userDao().getUserSync()
                            val activeGroupIds = db.groupDao().getMyGroupsSync().map { it.id }
                            val authorizedGroupId = AcademicSessionSelection.resolveAuthorizedGroupId(
                                savedGroupId = _selectedGroupId.value,
                                defaultGroupId = refreshedUser?.defaultGroupId ?: user.defaultGroupId,
                                availableGroupIds = activeGroupIds
                            )
                            _selectedGroupId.value = authorizedGroupId
                            prefs.edit().putString("selected_group_id_${user.id}", authorizedGroupId).commit()

                            refreshStartupAuxiliaryData(user, authorizedGroupId, activeGroupIds)
                            applyStartupSyncResult(syncResult, authorizedGroupId, refreshedUser?.gradeId ?: user.gradeId)
                        } catch (e: Exception) {
                            android.util.Log.e("TanweerViewModel", "Startup server reconciliation failed", e)
                            applyStartupSyncResult(
                                SyncResult.Offline("تعذر الاتصال بالخادم؛ يتم عرض النسخة المحفوظة محليًا"),
                                getActiveGroupId().ifBlank { user.defaultGroupId },
                                user.gradeId
                            )
                        } finally {
                            repository.mutationQueue.isDrainAllowed = dataGenerationVerified
                            _isCheckingAuth.value = false
                            if (_autoSyncEnabled.value && dataGenerationVerified) {
                                repository.mutationQueue.start()
                                repository.mutationQueue.triggerDrain()
                            }
                        }
                    }
                }
        }
    }

    fun onAppResumed() {
        val now = System.currentTimeMillis()
        if (!hasSeenInitialResume) {
            hasSeenInitialResume = true
            lastForegroundGenerationCheckAt = now
            return
        }
        if (now - lastForegroundGenerationCheckAt < 10_000L) return
        lastForegroundGenerationCheckAt = now

        viewModelScope.launch {
            localSessionReady.await()
            dataGenerationReady.await()
            if (db.userDao().getUserSync() == null) return@launch
            // Keep the current Compose tree mounted while the background consistency
            // check runs. Replacing the root with the startup splash here discards
            // open dialogs and their in-progress drafts after Photo Picker / Camera.
            try {
                val purged = verifyServerDataGeneration()
                if (!purged && dataGenerationVerified && _autoSyncEnabled.value) {
                    repository.mutationQueue.triggerDrain()
                }
            } catch (e: Exception) {
                android.util.Log.w("TanweerViewModel", "Foreground data-generation verification failed", e)
            }
        }
    }

    private suspend fun verifyServerDataGeneration(): Boolean = dataGenerationMutex.withLock {
        if (db.userDao().getUserSync() == null) {
            dataGenerationVerified = false
            repository.mutationQueue.isDrainAllowed = false
            return@withLock false
        }
        repository.mutationQueue.isDrainAllowed = false
        repository.mutationQueue.stopAndAwait()
        val versionResult = repository.getSystemVersion()
        val generation = versionResult.getOrNull()?.dataGeneration
        if (!versionResult.isSuccess || generation.isNullOrBlank()) {
            dataGenerationVerified = false
            return@withLock false
        }
        val purged = repository.checkAndApplyDataGeneration(getApplication(), generation)
        if (purged) {
            dataGenerationVerified = false
            resetUserScopedUiState()
            return@withLock true
        }
        dataGenerationVerified = true
        repository.mutationQueue.isDrainAllowed = true
        repository.mutationQueue.start()
        false
    }

    private fun resetUserScopedUiState() {
        currentActiveUserId = null
        _shouldShowCatchUpCard.value = false
        _lastOpenDate.value = todayDateStr
        _isNewAccountOrFirstLaunch.value = false
        _previousSessionTimestamp.value = System.currentTimeMillis()
        _selectedGroupId.value = ""
        _selectedDate.value = todayDateStr
        _currentTab.value = NavigationTab.TODAY
        _subScreen.value = SubScreen.NONE
        _activeReadingBook.value = null
        _activeReadingInitialPage.value = null
        pdfReaderReturnDestination = null
        _isUploadModalOpen.value = false
        _uploadInitialSubjectId.value = null
        _isAddHomeworkModalOpen.value = false
        _initialHomeworkDueDate.value = null
        _isAddExamModalOpen.value = false
        _initialExamDate.value = null
        _isAddEventModalOpen.value = false
        _initialEventDate.value = null
        _pendingDeepLinkChatGroupId.value = null
        _pendingDeepLinkMessageId.value = null
        _recoveryCodeDialog.value = null
        _searchQuery.value = ""
        _selectedKnowledgeBaseSubjectId.value = null
        _selectedAcademicYear.value = com.magd.tanweer.util.AcademicYearManager.getCurrentAcademicYear()
        _academicArchiveSyncState.value = SyncState.Idle
        _homeSyncState.value = SyncState.Idle
        _scheduleSyncState.value = SyncState.Idle
        _homeworkSyncState.value = SyncState.Idle
        _examsSyncState.value = SyncState.Idle
        _calendarSyncState.value = SyncState.Idle
        _calendarSyncMonthScope.value = null
        dayDetailsSyncRequestGeneration++
        _dayDetailsSyncState.value = SyncState.Idle
        _dayDetailsSyncGroupId.value = null
        _dayDetailsSyncDate.value = null
        _issuesSyncState.value = SyncState.Idle
        _eventsSyncState.value = SyncState.Idle
        librarySyncRequestGeneration++
        _librarySyncState.value = SyncState.Idle
        _librarySyncGradeId.value = null
        _groupsSyncState.value = SyncState.Idle
        chatHistoryRequestGeneration++
        _chatHistorySyncState.value = SyncState.Idle
        _chatHistorySyncGroupId.value = null

        searchChatHistoryRequestGeneration++
        _searchChatHistorySyncState.value = SyncState.Idle
        _searchChatHistorySyncGroupId.value = null
        scheduleMetaRequestGeneration++
        scheduleMetaSourceGroupId = null
        _scheduleMetaGroupId.value = null
        _scheduleMetaSnapshotGroupId.value = null
        _scheduleMetaSyncState.value = SyncState.Idle
        _scheduleMeta.value = null
        _scheduleVersion.value = null
        scheduleProposalsRequestGeneration++
        scheduleProposalsSourceGroupId = null
        _scheduleProposalsGroupId.value = null
        _scheduleProposalsSnapshotGroupId.value = null
        _scheduleProposalsSyncState.value = SyncState.Idle
        _scheduleProposals.value = emptyList()
        groupMembersRequestGeneration++
        groupMembersSourceGroupId = null
        _groupMembersSnapshotGroupId.value = null
        groupCorrectionsRequestGeneration++
        groupCorrectionsSourceGroupId = null
        _groupCorrectionsSnapshotGroupId.value = null
        communityDecisionsRequestGeneration++
        communityDecisionsSourceGroupId = null
        teacherDashboardRequestGeneration++
        moderatorDashboardRequestGeneration++
        adminDashboardRequestGeneration++
        _groupMembersState.value = null
        _groupMembersError.value = null
        _isGroupMembersLoading.value = false
        _teacherDashboard.value = null
        _teacherDashboardError.value = null
        _moderatorDashboard.value = null
        _moderatorDashboardError.value = null
        _adminDashboard.value = null
        _adminDashboardError.value = null
        _groupCorrectionsError.value = null
        _communityDecisionsError.value = null
        _isGroupCorrectionsLoading.value = false
        _isCommunityDecisionsLoading.value = false
        _isTeacherDashboardLoading.value = false
        _isModeratorDashboardLoading.value = false
        _isAdminDashboardLoading.value = false
        _myNotifications.value = emptyList()
        _groupCorrections.value = emptyList()
        _communityDecisions.value = emptyList()
        _communityDecisionsSnapshotGroupId.value = null
        _myRoleRequests.value = emptyList()
        _groupSyncScopeIds.value = emptyMap()
    }

    private suspend fun refreshStartupAuxiliaryData(user: User, groupId: String, activeGroupIds: List<String>) = coroutineScope {
        val tasks = mutableListOf<kotlinx.coroutines.Deferred<Unit>>()

        tasks += async(Dispatchers.IO) {
            _myRoleRequests.value = repository.getMyRoleRequests()
        }

        // Pull recent chat history for every server-confirmed active membership.
        // No WebSocket is opened until the user enters that group's chat.
        activeGroupIds.distinct().forEach { activeGroupId ->
            tasks += async(Dispatchers.IO) {
                repository.syncGroupChatHistory(activeGroupId)
                Unit
            }
        }

        if (groupId.isNotBlank()) {
            if (groupMembersSourceGroupId != groupId) {
                groupMembersSourceGroupId = groupId
                _groupMembersState.value = null
                _groupMembersSnapshotGroupId.value = null
            }
            val membersRequestId = ++groupMembersRequestGeneration
            _groupMembersError.value = null
            _isGroupMembersLoading.value = true
            tasks += async(Dispatchers.IO) {
                try {
                    repository.getGroupMembers(groupId)
                        .onSuccess { data ->
                            if (membersRequestId == groupMembersRequestGeneration && getActiveGroupId() == groupId) {
                                if (data.success) {
                                    _groupMembersState.value = data
                                    _groupMembersSnapshotGroupId.value = groupId
                                } else _groupMembersError.value = "تعذر تحميل قائمة أعضاء المجموعة من الخادم."
                            }
                        }
                        .onFailure { error ->
                            if (membersRequestId == groupMembersRequestGeneration && getActiveGroupId() == groupId) {
                                _groupMembersError.value = error.message ?: "تعذر تحميل أعضاء المجموعة."
                            }
                        }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (membersRequestId == groupMembersRequestGeneration && getActiveGroupId() == groupId) {
                        _groupMembersError.value = error.message ?: "تعذر تحميل أعضاء المجموعة."
                    }
                } finally {
                    if (membersRequestId == groupMembersRequestGeneration) _isGroupMembersLoading.value = false
                }
                Unit
            }

            tasks += async(Dispatchers.IO) {
                repository.getScheduleProposals(groupId).onSuccess { proposals ->
                    if (getActiveGroupId() == groupId) _scheduleProposals.value = proposals
                }
                Unit
            }

            if (groupCorrectionsSourceGroupId != groupId) {
                groupCorrectionsSourceGroupId = groupId
                _groupCorrectionsSnapshotGroupId.value = null
                _groupCorrections.value = emptyList()
            }
            val correctionsRequestId = ++groupCorrectionsRequestGeneration
            _groupCorrectionsError.value = null
            _isGroupCorrectionsLoading.value = true
            tasks += async(Dispatchers.IO) {
                try {
                    repository.getGroupCorrections(groupId)
                        .onSuccess { corrections ->
                            if (correctionsRequestId == groupCorrectionsRequestGeneration && getActiveGroupId() == groupId) {
                                _groupCorrections.value = corrections
                                _groupCorrectionsSnapshotGroupId.value = groupId
                            }
                        }
                        .onFailure { error ->
                            if (correctionsRequestId == groupCorrectionsRequestGeneration && getActiveGroupId() == groupId) {
                                _groupCorrectionsError.value = error.message ?: "تعذر تحميل طلبات التصويب"
                            }
                        }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (correctionsRequestId == groupCorrectionsRequestGeneration && getActiveGroupId() == groupId) {
                        _groupCorrectionsError.value = error.message ?: "تعذر تحميل طلبات التصويب"
                    }
                } finally {
                    if (correctionsRequestId == groupCorrectionsRequestGeneration) _isGroupCorrectionsLoading.value = false
                }
                Unit
            }

            if (communityDecisionsSourceGroupId != groupId) {
                communityDecisionsSourceGroupId = groupId
                _communityDecisions.value = emptyList()
            }
            val decisionsRequestId = ++communityDecisionsRequestGeneration
            _communityDecisionsError.value = null
            _isCommunityDecisionsLoading.value = true
            tasks += async(Dispatchers.IO) {
                try {
                    repository.getCommunityDecisions(groupId)
                        .onSuccess { decisions ->
                            if (decisionsRequestId == communityDecisionsRequestGeneration && getActiveGroupId() == groupId) {
                                _communityDecisions.value = decisions
                            }
                        }
                        .onFailure { error ->
                            if (decisionsRequestId == communityDecisionsRequestGeneration && getActiveGroupId() == groupId) {
                                _communityDecisionsError.value = error.message ?: "تعذر تحميل القرارات الجماعية"
                            }
                        }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (decisionsRequestId == communityDecisionsRequestGeneration && getActiveGroupId() == groupId) {
                        _communityDecisionsError.value = error.message ?: "تعذر تحميل القرارات الجماعية"
                    }
                } finally {
                    if (decisionsRequestId == communityDecisionsRequestGeneration) _isCommunityDecisionsLoading.value = false
                }
                Unit
            }

            tasks += async(Dispatchers.IO) {
                repository.getScheduleMetaAndSlots(groupId).onSuccess { schedule ->
                    if (getActiveGroupId() == groupId) {
                        _scheduleMeta.value = schedule.meta
                        _scheduleVersion.value = schedule.version
                    }
                }
                Unit
            }
        }

        when (user.role.uppercase(Locale.ROOT)) {
            "TEACHER" -> {
                val requestId = ++teacherDashboardRequestGeneration
                _teacherDashboardError.value = null
                _isTeacherDashboardLoading.value = true
                tasks += async(Dispatchers.IO) { runTeacherDashboardRequest(user, requestId) }
            }
            "MODERATOR" -> {
                val requestId = ++moderatorDashboardRequestGeneration
                _moderatorDashboardError.value = null
                _isModeratorDashboardLoading.value = true
                tasks += async(Dispatchers.IO) { runModeratorDashboardRequest(user, requestId) }
            }
            "ADMIN" -> {
                val requestId = ++adminDashboardRequestGeneration
                _adminDashboardError.value = null
                _isAdminDashboardLoading.value = true
                tasks += async(Dispatchers.IO) { runAdminDashboardRequest(user, requestId) }
            }
        }

        tasks.awaitAll()
    }

    private fun applyStartupSyncResult(result: SyncResult, groupId: String, gradeId: Int?) {
        val resolvedState = when (result) {
            is SyncResult.Success -> SyncState.Success()
            is SyncResult.Offline -> SyncState.OfflineCached()
            is SyncResult.Error -> SyncState.Error(result.message, result.isNetworkError)
        }

        // syncInitial returns Success only when the server-backed startup snapshots complete.
        // Bind that result to the resolved active group so a previous group's result cannot
        // authorize this group's empty-state UI.
        if (groupId.isNotBlank()) {
            listOf(
                GroupSyncSource.CONTENTS,
                GroupSyncSource.HOME,
                GroupSyncSource.SCHEDULE,
                GroupSyncSource.HOMEWORKS,
                GroupSyncSource.EXAMS,
                GroupSyncSource.CALENDAR,
                GroupSyncSource.ISSUES,
                GroupSyncSource.EVENTS
            ).forEach { source -> beginGroupSync(source, groupId) }
        }

        _sectionContentsSyncState.value = resolvedState
        _homeSyncState.value = resolvedState
        _scheduleSyncState.value = resolvedState
        _homeworkSyncState.value = resolvedState
        _examsSyncState.value = resolvedState
        _calendarSyncState.value = resolvedState
        _issuesSyncState.value = resolvedState
        _eventsSyncState.value = resolvedState
        _academicArchiveSyncState.value = resolvedState
        _librarySyncState.value = resolvedState
        _librarySyncGradeId.value = gradeId
        _groupsSyncState.value = resolvedState
    }
    private fun getActiveGroupId(): String = _selectedGroupId.value

    // =========================================================================
    // NAVIGATION & ON-DEMAND SYNCING
    // =========================================================================

    private fun syncTabOnEntry(tab: NavigationTab) {
        val groupId = getActiveGroupId().ifBlank { currentUser.value?.defaultGroupId.orEmpty() }
        when (tab) {
            NavigationTab.TODAY -> {
                syncHome(groupId, _selectedDate.value, isRefresh = true)
                syncSchedule(groupId, isRefresh = true)
                syncHomeworks(groupId, isRefresh = true)
                syncExams(groupId, isRefresh = true)
                syncEvents(groupId, isRefresh = true)
            }
            NavigationTab.CALENDAR -> {
                syncCalendar(groupId, isRefresh = true)
                syncEvents(groupId, isRefresh = true)
            }
            NavigationTab.HOMEWORK -> syncHomeworks(groupId, isRefresh = true)
            NavigationTab.EXAMS -> syncExams(groupId, isRefresh = true)
            NavigationTab.ISSUES -> syncIssues(groupId, isRefresh = true)
            NavigationTab.MORE -> {
                syncGroups(isRefresh = true)
                syncEvents(groupId, isRefresh = true)
            }
        }
    }

    private fun syncSubScreenOnEntry(screen: SubScreen) {
        val groupId = getActiveGroupId().ifBlank { currentUser.value?.defaultGroupId.orEmpty() }
        when (screen) {
            SubScreen.NONE -> syncTabOnEntry(_currentTab.value)
            SubScreen.EXTRA_SECTIONS_HUB -> {
                syncGroups(isRefresh = true)
                syncEvents(groupId, isRefresh = true)
            }
            SubScreen.SCHEDULE -> {
                syncSchedule(groupId, isRefresh = true)
                loadScheduleData()
            }
            SubScreen.GROUPS -> syncGroups(isRefresh = true)
            SubScreen.LIBRARY, SubScreen.STORAGE_MANAGER ->
                syncBooks(currentUser.value?.gradeId ?: 10, isRefresh = true)

            SubScreen.TIMELINE -> {
                syncSectionContents(groupId, isRefresh = true)
                syncHomeworks(groupId, isRefresh = true)
                syncExams(groupId, isRefresh = true)
            }

            SubScreen.SUBJECT_KNOWLEDGE_BASE -> {
                syncSectionContents(groupId, isRefresh = true)
                syncHomeworks(groupId, isRefresh = true)
                syncExams(groupId, isRefresh = true)
                // The unified subject workspace also displays the class's
                // questions/discussions; refresh them on entry as well.
                syncIssues(groupId, isRefresh = true)
            }

            SubScreen.ACADEMIC_HISTORY -> syncAcademicArchive(isRefresh = true)

            SubScreen.SEARCH -> {
                syncSectionContents(groupId, isRefresh = true)
                syncHomeworks(groupId, isRefresh = true)
                syncExams(groupId, isRefresh = true)
                syncIssues(groupId, isRefresh = true)
                syncEvents(groupId, isRefresh = true)
                syncBooks(currentUser.value?.gradeId ?: 10, isRefresh = true)
                syncGroups(isRefresh = true)
            }

            SubScreen.WHAT_DID_I_MISS -> {
                syncSectionContents(groupId, isRefresh = true)
                syncHomeworks(groupId, isRefresh = true)
                syncExams(groupId, isRefresh = true)
                syncEvents(groupId, isRefresh = true)
                syncSchedule(groupId, isRefresh = true)
            }

            SubScreen.EVENTS -> syncEvents(groupId, isRefresh = true)

            SubScreen.COMMUNITY_DECISIONS -> {
                loadCommunityDecisions(groupId)
                loadScheduleProposals()
            }

            SubScreen.TEACHER_DASHBOARD -> loadTeacherDashboard()
            SubScreen.MODERATOR_DASHBOARD -> {
                loadModeratorDashboard()
                loadGroupCorrections(groupId)
                loadCommunityDecisions(groupId)
                loadGroupMembers(groupId)
                loadScheduleProposals()
            }
            SubScreen.ADMIN_DASHBOARD -> loadAdminDashboard()

            SubScreen.PROFILE, SubScreen.SETTINGS -> viewModelScope.launch {
                repository.syncProfile()
            }

            SubScreen.VERSION_CHECK -> viewModelScope.launch {
                repository.getSystemVersion()
            }

            SubScreen.PDF_VIEWER -> Unit
        }
    }

    private fun syncCurrentDestinationOnEntry() {
        if (_subScreen.value == SubScreen.NONE) {
            syncTabOnEntry(_currentTab.value)
        } else {
            syncSubScreenOnEntry(_subScreen.value)
        }
    }

    fun setTab(tab: NavigationTab) {
        triggerHaptic(com.magd.tanweer.util.TanweerHapticManager.HapticType.CLICK)
        _currentTab.value = tab
        _subScreen.value = SubScreen.NONE
        syncTabOnEntry(tab)
    }

    fun setSubScreen(screen: SubScreen) {
        triggerHaptic(com.magd.tanweer.util.TanweerHapticManager.HapticType.CLICK)
        _subScreen.value = screen
        syncSubScreenOnEntry(screen)
    }

    fun navigateBack() {
        if (_activeReadingBook.value != null) {
            closePdfReader()
            return
        }
        val previousScreen = _subScreen.value
        when (_subScreen.value) {
            SubScreen.NONE -> {
                // Already on root screen
            }
            SubScreen.EXTRA_SECTIONS_HUB -> {
                _subScreen.value = SubScreen.NONE
            }
            SubScreen.TIMELINE,
            SubScreen.SEARCH,
            SubScreen.WHAT_DID_I_MISS,
            SubScreen.SUBJECT_KNOWLEDGE_BASE,
            SubScreen.ACADEMIC_HISTORY,
            SubScreen.TEACHER_DASHBOARD,
            SubScreen.MODERATOR_DASHBOARD,
            SubScreen.ADMIN_DASHBOARD,
            SubScreen.COMMUNITY_DECISIONS,
            SubScreen.EVENTS,
            SubScreen.VERSION_CHECK,
            SubScreen.STORAGE_MANAGER -> {
                _subScreen.value = SubScreen.EXTRA_SECTIONS_HUB
            }
            SubScreen.GROUPS,
            SubScreen.LIBRARY,
            SubScreen.SCHEDULE,
            SubScreen.PROFILE,
            SubScreen.SETTINGS -> {
                _subScreen.value = SubScreen.NONE
            }
            SubScreen.PDF_VIEWER -> {
                closePdfReader()
            }
        }
        if (previousScreen != SubScreen.PDF_VIEWER && _subScreen.value != previousScreen) {
            syncCurrentDestinationOnEntry()
        }
    }

    fun openBookInPdfReader(book: BookItem, targetPage: Int? = null) {
        if (pdfReaderReturnDestination == null) {
            pdfReaderReturnDestination = PdfReaderReturnDestination(
                subScreen = _subScreen.value,
                currentTab = _currentTab.value,
                knowledgeBaseSubjectId = _selectedKnowledgeBaseSubjectId.value
            )
        }
        _activeReadingBook.value = book
        _activeReadingInitialPage.value = targetPage
        _subScreen.value = SubScreen.PDF_VIEWER
    }

    fun closePdfReader() {
        val returnDestination = PdfReaderNavigationPolicy.resolve(
            origin = pdfReaderReturnDestination,
            fallbackTab = _currentTab.value
        )
        _activeReadingBook.value = null
        _activeReadingInitialPage.value = null
        pdfReaderReturnDestination = null
        _currentTab.value = returnDestination.currentTab
        _selectedKnowledgeBaseSubjectId.value = returnDestination.knowledgeBaseSubjectId
        _subScreen.value = returnDestination.subScreen

        if (returnDestination.subScreen == SubScreen.NONE) {
            syncTabOnEntry(returnDestination.currentTab)
        } else {
            syncSubScreenOnEntry(returnDestination.subScreen)
        }
    }
    fun setSelectedGroup(groupId: String) {
        if (groupId.isBlank()) return
        _selectedGroupId.value = groupId
        persistSelectedGroup(groupId)
        // Re-run the active destination's source syncs for the newly selected group.
        // A previous group's Success state must not authorize an empty state here.
        syncCurrentDestinationOnEntry()
    }

    fun setSelectedDate(date: String) {
        if (!AcademicSessionSelection.isValidIsoDate(date)) return
        _selectedDate.value = date
        persistSelectedStudyDate(date)
        syncHome(getActiveGroupId(), date, isRefresh = true)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun openUploadDialog(subjectId: String? = null) {
        triggerHaptic(com.magd.tanweer.util.TanweerHapticManager.HapticType.CLICK)
        _uploadInitialSubjectId.value = subjectId
        _isUploadModalOpen.value = true
    }

    fun closeUploadDialog() {
        triggerHaptic(com.magd.tanweer.util.TanweerHapticManager.HapticType.CLICK)
        _isUploadModalOpen.value = false
        _uploadInitialSubjectId.value = null
    }

    fun openAddHomeworkDialog(initialDate: String? = null) {
        triggerHaptic(com.magd.tanweer.util.TanweerHapticManager.HapticType.CLICK)
        _initialHomeworkDueDate.value = initialDate ?: _selectedDate.value
        _isAddHomeworkModalOpen.value = true
        setTab(NavigationTab.HOMEWORK)
    }

    fun closeAddHomeworkDialog() {
        triggerHaptic(com.magd.tanweer.util.TanweerHapticManager.HapticType.CLICK)
        _isAddHomeworkModalOpen.value = false
        _initialHomeworkDueDate.value = null
    }

    fun openAddExamDialog(initialDate: String? = null) {
        triggerHaptic(com.magd.tanweer.util.TanweerHapticManager.HapticType.CLICK)
        _initialExamDate.value = initialDate ?: _selectedDate.value
        _isAddExamModalOpen.value = true
        setTab(NavigationTab.EXAMS)
    }

    fun closeAddExamDialog() {
        triggerHaptic(com.magd.tanweer.util.TanweerHapticManager.HapticType.CLICK)
        _isAddExamModalOpen.value = false
        _initialExamDate.value = null
    }

    fun openAddEventDialog(initialDate: String? = null) {
        _initialEventDate.value = initialDate ?: _selectedDate.value
        _isAddEventModalOpen.value = true
        setSubScreen(SubScreen.EVENTS)
    }

    fun closeAddEventDialog() {
        _isAddEventModalOpen.value = false
        _initialEventDate.value = null
    }

    fun dismissRecoveryDialog() {
        _recoveryCodeDialog.value = null
    }

    // =========================================================================
    // SYNC METHODS (Server -> Room -> Compose)
    // =========================================================================

    @Synchronized
    private fun beginGroupSync(source: GroupSyncSource, groupId: String): Int {
        val requestId = (groupSyncGenerations[source] ?: 0) + 1
        groupSyncGenerations[source] = requestId
        _groupSyncScopeIds.value = _groupSyncScopeIds.value + (source to groupId)
        return requestId
    }

    @Synchronized
    private fun isCurrentGroupSync(source: GroupSyncSource, groupId: String, requestId: Int): Boolean =
        groupSyncGenerations[source] == requestId && _groupSyncScopeIds.value[source] == groupId

    private fun launchGroupScopedSync(
        source: GroupSyncSource,
        groupId: String,
        state: MutableStateFlow<SyncState>,
        isRefresh: Boolean,
        dataLabel: String,
        syncOperation: suspend () -> SyncResult
    ) {
        if (!_autoSyncEnabled.value && !isRefresh) return

        val requestId = beginGroupSync(source, groupId)
        if (groupId.isBlank()) {
            state.value = SyncState.Error("لا توجد شعبة نشطة لمزامنة $dataLabel", false)
            return
        }

        state.value = if (isRefresh) SyncState.Refreshing else SyncState.Loading
        viewModelScope.launch {
            try {
                val result = syncOperation()
                if (!isCurrentGroupSync(source, groupId, requestId)) return@launch
                state.value = when (result) {
                    is SyncResult.Success -> SyncState.Success()
                    is SyncResult.Offline -> SyncState.OfflineCached()
                    is SyncResult.Error -> SyncState.Error(result.message, result.isNetworkError)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrentGroupSync(source, groupId, requestId)) {
                    state.value = SyncState.Error(error.message ?: "تعذر مزامنة $dataLabel", false)
                }
            }
        }
    }

    /**
     * Refreshes the active group's complete lesson snapshot for screens whose content
     * spans more than the currently selected day (timeline, search and knowledge base).
     */
    fun syncSectionContents(groupId: String = getActiveGroupId(), isRefresh: Boolean = false) {
        launchGroupScopedSync(
            source = GroupSyncSource.CONTENTS,
            groupId = groupId,
            state = _sectionContentsSyncState,
            isRefresh = isRefresh,
            dataLabel = "الدروس"
        ) { repository.syncContents(groupId) }
    }

    /**
     * Pulls the account-wide academic archive and the snapshots for every active group.
     * This is intentionally separate from day-scoped sync so historical years stay current.
     */
    fun syncAcademicArchive(isRefresh: Boolean = false) {
        if (!_autoSyncEnabled.value && !isRefresh) return
        viewModelScope.launch {
            _academicArchiveSyncState.value =
                if (isRefresh) SyncState.Refreshing else SyncState.Loading
            when (val result = repository.syncStudentAcademicArchive(_selectedDate.value.take(7))) {
                is SyncResult.Success -> _academicArchiveSyncState.value = SyncState.Success()
                is SyncResult.Offline -> _academicArchiveSyncState.value = SyncState.OfflineCached()
                is SyncResult.Error -> _academicArchiveSyncState.value =
                    SyncState.Error(result.message, result.isNetworkError)
            }
        }
    }

    fun syncInitialData(groupId: String, date: String, isExplicitAction: Boolean = false) {
        if (!_autoSyncEnabled.value && !isExplicitAction) return
        val homeRequestId = beginGroupSync(GroupSyncSource.HOME, groupId)
        val scheduleRequestId = beginGroupSync(GroupSyncSource.SCHEDULE, groupId)
        if (groupId.isBlank()) {
            _homeSyncState.value = SyncState.Error("لا توجد شعبة نشطة لمزامنة بيانات اليوم", false)
            _scheduleSyncState.value = SyncState.Error("لا توجد شعبة نشطة لمزامنة الجدول", false)
            return
        }
        _homeSyncState.value = SyncState.Loading
        _scheduleSyncState.value = SyncState.Loading
        viewModelScope.launch {
            try {
                when (val result = repository.syncInitial(groupId, date)) {
                    is SyncResult.Success -> {
                        if (isCurrentGroupSync(GroupSyncSource.HOME, groupId, homeRequestId)) {
                            _homeSyncState.value = SyncState.Success()
                        }
                        if (isCurrentGroupSync(GroupSyncSource.SCHEDULE, groupId, scheduleRequestId)) {
                            _scheduleSyncState.value = SyncState.Success()
                        }
                    }
                    is SyncResult.Offline -> {
                        if (isCurrentGroupSync(GroupSyncSource.HOME, groupId, homeRequestId)) {
                            _homeSyncState.value = SyncState.OfflineCached()
                        }
                        if (isCurrentGroupSync(GroupSyncSource.SCHEDULE, groupId, scheduleRequestId)) {
                            _scheduleSyncState.value = SyncState.OfflineCached()
                        }
                    }
                    is SyncResult.Error -> {
                        if (isCurrentGroupSync(GroupSyncSource.HOME, groupId, homeRequestId)) {
                            _homeSyncState.value = SyncState.Error(result.message, result.isNetworkError)
                        }
                        if (isCurrentGroupSync(GroupSyncSource.SCHEDULE, groupId, scheduleRequestId)) {
                            _scheduleSyncState.value = SyncState.Error(result.message, result.isNetworkError)
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrentGroupSync(GroupSyncSource.HOME, groupId, homeRequestId)) {
                    _homeSyncState.value = SyncState.Error(error.message ?: "تعذر مزامنة بيانات اليوم", false)
                }
                if (isCurrentGroupSync(GroupSyncSource.SCHEDULE, groupId, scheduleRequestId)) {
                    _scheduleSyncState.value = SyncState.Error(error.message ?: "تعذر مزامنة الجدول", false)
                }
            }
        }
    }

    fun syncHome(groupId: String = getActiveGroupId(), date: String = _selectedDate.value, isRefresh: Boolean = false) {
        launchGroupScopedSync(
            source = GroupSyncSource.HOME,
            groupId = groupId,
            state = _homeSyncState,
            isRefresh = isRefresh,
            dataLabel = "بيانات اليوم"
        ) { repository.syncDay(groupId, date) }
    }

    fun syncSchedule(groupId: String = getActiveGroupId(), isRefresh: Boolean = false) {
        launchGroupScopedSync(
            source = GroupSyncSource.SCHEDULE,
            groupId = groupId,
            state = _scheduleSyncState,
            isRefresh = isRefresh,
            dataLabel = "الجدول"
        ) { repository.syncSchedule(groupId) }
    }

    fun syncHomeworks(groupId: String = getActiveGroupId(), isRefresh: Boolean = false) {
        launchGroupScopedSync(
            source = GroupSyncSource.HOMEWORKS,
            groupId = groupId,
            state = _homeworkSyncState,
            isRefresh = isRefresh,
            dataLabel = "الواجبات"
        ) { repository.syncHomeworks(groupId) }
    }

    fun syncExams(groupId: String = getActiveGroupId(), isRefresh: Boolean = false) {
        launchGroupScopedSync(
            source = GroupSyncSource.EXAMS,
            groupId = groupId,
            state = _examsSyncState,
            isRefresh = isRefresh,
            dataLabel = "الاختبارات"
        ) { repository.syncExams(groupId) }
    }

    fun syncCalendar(groupId: String = getActiveGroupId(), isRefresh: Boolean = false) {
        val month = _calendarSyncMonthScope.value
            ?: SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        syncCalendarMonth(groupId, month, isRefresh)
    }

    fun syncCalendarMonth(groupId: String = getActiveGroupId(), month: String, isRefresh: Boolean = false) {
        val requestedMonth = month.trim()
        val monthChanged = _calendarSyncMonthScope.value != requestedMonth
        if (monthChanged) {
            _calendarSyncMonthScope.value = requestedMonth.takeIf { it.isNotBlank() }
            // A success for another month cannot confirm this month's contents.
            _calendarSyncState.value = SyncState.Idle
        }
        if (!_autoSyncEnabled.value && !isRefresh) {
            // Invalidate an older request even when automatic sync is disabled.
            if (monthChanged) beginGroupSync(GroupSyncSource.CALENDAR, groupId)
            return
        }
        launchGroupScopedSync(
            source = GroupSyncSource.CALENDAR,
            groupId = groupId,
            state = _calendarSyncState,
            isRefresh = isRefresh,
            dataLabel = "التقويم"
        ) { repository.syncCalendarMonth(groupId, requestedMonth) }
    }

    fun syncDayDetails(groupId: String = getActiveGroupId(), date: String) {
        if (groupId.isBlank() || date.isBlank()) {
            dayDetailsSyncRequestGeneration++
            _dayDetailsSyncGroupId.value = null
            _dayDetailsSyncDate.value = null
            _dayDetailsSyncState.value = SyncState.Idle
            return
        }

        val requestId = ++dayDetailsSyncRequestGeneration
        _dayDetailsSyncGroupId.value = groupId
        _dayDetailsSyncDate.value = date
        _dayDetailsSyncState.value = SyncState.Loading

        viewModelScope.launch {
            try {
                when (val result = repository.syncDay(groupId, date)) {
                    is SyncResult.Success -> {
                        if (requestId == dayDetailsSyncRequestGeneration) {
                            _dayDetailsSyncState.value = SyncState.Success()
                        }
                    }
                    is SyncResult.Offline -> {
                        if (requestId == dayDetailsSyncRequestGeneration) {
                            _dayDetailsSyncState.value = SyncState.OfflineCached()
                        }
                    }
                    is SyncResult.Error -> {
                        if (requestId == dayDetailsSyncRequestGeneration) {
                            _dayDetailsSyncState.value = SyncState.Error(result.message, result.isNetworkError)
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (requestId == dayDetailsSyncRequestGeneration) {
                    _dayDetailsSyncState.value = SyncState.Error(getApplication<Application>().getString(com.magd.tanweer.R.string.calendar_day_sync_failed), false)
                }
            }
        }
    }

    fun toggleExamCompletion(examId: String, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.toggleExamCompletion(examId, isCompleted)
        }
    }

    fun syncIssues(groupId: String = getActiveGroupId(), isRefresh: Boolean = false) {
        launchGroupScopedSync(
            source = GroupSyncSource.ISSUES,
            groupId = groupId,
            state = _issuesSyncState,
            isRefresh = isRefresh,
            dataLabel = "الاستفسارات"
        ) { repository.syncIssues(groupId) }
    }

    fun syncEvents(groupId: String = getActiveGroupId(), isRefresh: Boolean = false) {
        launchGroupScopedSync(
            source = GroupSyncSource.EVENTS,
            groupId = groupId,
            state = _eventsSyncState,
            isRefresh = isRefresh,
            dataLabel = "الفعاليات"
        ) { repository.syncEvents(groupId) }
    }

    fun syncGroups(isRefresh: Boolean = false) {
        if (!_autoSyncEnabled.value && !isRefresh) return
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
        if (!_autoSyncEnabled.value && !isRefresh && onResult == null) return
        val targetGrade = gradeId ?: currentUser.value?.gradeId ?: 10
        val requestId = ++librarySyncRequestGeneration
        viewModelScope.launch {
            _librarySyncGradeId.value = targetGrade
            _librarySyncState.value = if (isRefresh) SyncState.Refreshing else SyncState.Loading
            try {
                when (val result = repository.syncBooks(targetGrade, forceRefresh = isRefresh)) {
                    is SyncResult.Success -> if (requestId == librarySyncRequestGeneration) {
                        _librarySyncState.value = SyncState.Success()
                        onResult?.invoke(true, "تمت مزامنة الكتب الدراسية بنجاح ✨")
                    }
                    is SyncResult.Offline -> if (requestId == librarySyncRequestGeneration) {
                        _librarySyncState.value = SyncState.OfflineCached()
                        onResult?.invoke(true, "عرض الكتب المحفوظة أوفلاين")
                    }
                    is SyncResult.Error -> if (requestId == librarySyncRequestGeneration) {
                        _librarySyncState.value = SyncState.Error(result.message, result.isNetworkError)
                        onResult?.invoke(false, result.message)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (requestId == librarySyncRequestGeneration) {
                    val message = error.message ?: "تعذر مزامنة كتب هذا الصف"
                    _librarySyncState.value = SyncState.Error(message, false)
                    onResult?.invoke(false, message)
                }
            }
        }
    }

    // Refresh wrappers called by UI components
    fun refreshSectionContents() = syncSectionContents(isRefresh = true)
    fun refreshHome() = syncHome(isRefresh = true)
    fun refreshSchedule() = syncSchedule(isRefresh = true)
    fun refreshHomeworks() = syncHomeworks(isRefresh = true)
    fun refreshExams() = syncExams(isRefresh = true)
    fun refreshCalendar() = syncCalendar(isRefresh = true)
    fun refreshIssues() = syncIssues(isRefresh = true)
    fun refreshEvents() = syncEvents(isRefresh = true)
    fun refreshGroups() = syncGroups(isRefresh = true)
    fun refreshBooks() = syncBooks(isRefresh = true)

    // =========================================================================
    // ACTIONS & MUTATIONS
    // =========================================================================

    fun navigateToGroupChatMessage(groupId: String, messageId: String) {
        _pendingDeepLinkChatGroupId.value = groupId
        _pendingDeepLinkMessageId.value = messageId
        setSubScreen(SubScreen.GROUPS)
    }

    fun clearPendingDeepLink() {
        _pendingDeepLinkChatGroupId.value = null
        _pendingDeepLinkMessageId.value = null
    }

    fun connectGroupChat(groupId: String) {
        val user = currentUser.value ?: return
        val requestedGroupId = groupId.trim()
        if (requestedGroupId.isBlank()) return
        val requestId = ++chatHistoryRequestGeneration
        _chatHistorySyncGroupId.value = requestedGroupId
        _chatHistorySyncState.value = SyncState.Loading
        viewModelScope.launch {
            try {
                val result = repository.connectToGroupChat(requestedGroupId, user.id) { reconciled ->
                    if (requestId == chatHistoryRequestGeneration &&
                        _chatHistorySyncGroupId.value == requestedGroupId
                    ) {
                        _chatHistorySyncState.value = when (reconciled) {
                            is SyncResult.Success -> SyncState.Success()
                            is SyncResult.Offline -> SyncState.OfflineCached()
                            is SyncResult.Error -> SyncState.Error(reconciled.message, reconciled.isNetworkError)
                        }
                    }
                }
                if (requestId != chatHistoryRequestGeneration || _chatHistorySyncGroupId.value != requestedGroupId) return@launch
                _chatHistorySyncState.value = when (result) {
                    is SyncResult.Success -> SyncState.Success()
                    is SyncResult.Offline -> SyncState.OfflineCached()
                    is SyncResult.Error -> SyncState.Error(result.message, result.isNetworkError)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (requestId == chatHistoryRequestGeneration && _chatHistorySyncGroupId.value == requestedGroupId) {
                    _chatHistorySyncState.value = SyncState.Error(
                        error.message ?: getApplication<Application>().getString(com.magd.tanweer.R.string.chat_history_sync_failed), false
                    )
                }
            }
        }
    }

    /**
     * Refreshes the locally indexed chat history for Search without opening a WebSocket.
     * This state is separate from the active chat connection so a search refresh cannot
     * invalidate or replace the connection lifecycle owned by the Groups screen.
     */
    fun refreshSearchChatHistory(groupId: String = getActiveGroupId()) {
        val requestedGroupId = groupId.trim()
        if (requestedGroupId.isBlank()) return

        val requestId = ++searchChatHistoryRequestGeneration
        val hadSnapshot = _searchChatHistorySyncGroupId.value == requestedGroupId &&
            (_searchChatHistorySyncState.value is SyncState.Success ||
                _searchChatHistorySyncState.value is SyncState.OfflineCached)
        _searchChatHistorySyncGroupId.value = requestedGroupId
        _searchChatHistorySyncState.value =
            if (hadSnapshot) SyncState.Refreshing else SyncState.Loading

        viewModelScope.launch {
            try {
                val result = repository.syncGroupChatHistory(requestedGroupId)
                if (requestId != searchChatHistoryRequestGeneration ||
                    _searchChatHistorySyncGroupId.value != requestedGroupId
                ) return@launch

                _searchChatHistorySyncState.value = when (result) {
                    is SyncResult.Success -> SyncState.Success()
                    is SyncResult.Offline -> SyncState.OfflineCached()
                    is SyncResult.Error -> SyncState.Error(result.message, result.isNetworkError)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (requestId == searchChatHistoryRequestGeneration &&
                    _searchChatHistorySyncGroupId.value == requestedGroupId
                ) {
                    _searchChatHistorySyncState.value = SyncState.Error(
                        error.message ?: getApplication<Application>().getString(
                            com.magd.tanweer.R.string.chat_history_sync_failed
                        ),
                        false
                    )
                }
            }
        }
    }
    fun disconnectGroupChat() {
        chatHistoryRequestGeneration++
        _chatHistorySyncGroupId.value = null
        _chatHistorySyncState.value = SyncState.Idle
        viewModelScope.launch { repository.disconnectFromGroupChat() }
    }

    fun addScheduleSlot(
        dayOfWeek: Int,
        slotOrder: Int,
        subjectId: String,
        subjectName: String,
        subjectIcon: String,
        colorHex: String?,
        startTime: String? = null,
        endTime: String? = null,
        onResult: ((Result<com.magd.tanweer.data.local.ScheduleMutationResult>) -> Unit)? = null
    ) {
        val user = currentUser.value ?: return
        val groupId = getActiveGroupId()
        viewModelScope.launch {
            val res = repository.addScheduleSlot(
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
            res.fold(
                onSuccess = { mutation ->
                    when (mutation) {
                        is com.magd.tanweer.data.local.ScheduleMutationResult.SavedDirectly -> {
                            _scheduleSyncState.value = SyncState.Refreshing
                            when (val syncResult = repository.syncSchedule(groupId)) {
                                is SyncResult.Success -> _scheduleSyncState.value = SyncState.Success()
                                is SyncResult.Offline -> _scheduleSyncState.value = SyncState.OfflineCached()
                                is SyncResult.Error -> _scheduleSyncState.value =
                                    SyncState.Error(syncResult.message, syncResult.isNetworkError)
                            }
                        }
                        is com.magd.tanweer.data.local.ScheduleMutationResult.QueuedOffline -> {
                            _scheduleSyncState.value = SyncState.OfflineCached()
                        }
                        is com.magd.tanweer.data.local.ScheduleMutationResult.SubmittedAsProposal -> {
                            _scheduleSyncState.value = SyncState.Success()
                        }
                    }
                },
                onFailure = { err ->
                    _scheduleSyncState.value = SyncState.Error(err.message ?: "فشل حفظ الحصة في الخادم", false)
                }
            )
            onResult?.invoke(res)
        }
    }

    fun deleteScheduleSlot(
        dayOfWeek: Int,
        slotOrder: Int,
        onResult: ((Result<com.magd.tanweer.data.local.ScheduleSlotDeleteResult>) -> Unit)? = null
    ) {
        val user = currentUser.value ?: return
        val groupId = getActiveGroupId()
        viewModelScope.launch {
            val res = repository.deleteScheduleSlot(groupId, dayOfWeek, slotOrder)
            res.fold(
                onSuccess = { deleteRes ->
                    triggerHaptic(com.magd.tanweer.util.TanweerHapticManager.HapticType.CLICK)
                    if (deleteRes.isSynced) {
                        _scheduleSyncState.value = SyncState.Refreshing
                        when (val syncResult = repository.syncSchedule(groupId)) {
                            is SyncResult.Success -> _scheduleSyncState.value = SyncState.Success()
                            is SyncResult.Offline -> _scheduleSyncState.value = SyncState.OfflineCached()
                            is SyncResult.Error -> _scheduleSyncState.value =
                                SyncState.Error(syncResult.message, syncResult.isNetworkError)
                        }
                    } else {
                        _scheduleSyncState.value = SyncState.OfflineCached(message = deleteRes.message)
                    }
                },
                onFailure = { err ->
                    triggerHaptic(com.magd.tanweer.util.TanweerHapticManager.HapticType.ERROR)
                    _scheduleSyncState.value = SyncState.Error(err.message ?: "تعذر حذف الحصة من الخادم", false)
                }
            )
            onResult?.invoke(res)
        }
    }

    fun deleteScheduleSlotById(slotId: String, onResult: ((Result<com.magd.tanweer.data.local.ScheduleSlotDeleteResult>) -> Unit)? = null) {
        viewModelScope.launch {
            val res = repository.deleteScheduleSlotById(slotId)
            onResult?.invoke(res)
        }
    }

    @Volatile private var scheduleMetaRequestGeneration = 0
    @Volatile private var scheduleMetaSourceGroupId: String? = null
    private val _scheduleMeta = MutableStateFlow<com.magd.tanweer.data.model.ScheduleMeta?>(null)
    val scheduleMeta: StateFlow<com.magd.tanweer.data.model.ScheduleMeta?> = _scheduleMeta.asStateFlow()
    private val _scheduleMetaGroupId = MutableStateFlow<String?>(null)
    val scheduleMetaGroupId: StateFlow<String?> = _scheduleMetaGroupId.asStateFlow()
    private val _scheduleMetaSnapshotGroupId = MutableStateFlow<String?>(null)
    val scheduleMetaSnapshotGroupId: StateFlow<String?> = _scheduleMetaSnapshotGroupId.asStateFlow()
    private val _scheduleMetaSyncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val scheduleMetaSyncState: StateFlow<SyncState> = _scheduleMetaSyncState.asStateFlow()

    private val _scheduleVersion = MutableStateFlow<com.magd.tanweer.data.model.ScheduleVersionItem?>(null)
    val scheduleVersion: StateFlow<com.magd.tanweer.data.model.ScheduleVersionItem?> = _scheduleVersion.asStateFlow()

    @Volatile private var scheduleProposalsRequestGeneration = 0
    @Volatile private var scheduleProposalsSourceGroupId: String? = null
    private val _scheduleProposals = MutableStateFlow<List<com.magd.tanweer.data.model.ScheduleProposalItem>>(emptyList())
    val scheduleProposals: StateFlow<List<com.magd.tanweer.data.model.ScheduleProposalItem>> = _scheduleProposals.asStateFlow()
    private val _scheduleProposalsGroupId = MutableStateFlow<String?>(null)
    val scheduleProposalsGroupId: StateFlow<String?> = _scheduleProposalsGroupId.asStateFlow()
    private val _scheduleProposalsSnapshotGroupId = MutableStateFlow<String?>(null)
    val scheduleProposalsSnapshotGroupId: StateFlow<String?> = _scheduleProposalsSnapshotGroupId.asStateFlow()
    private val _scheduleProposalsSyncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val scheduleProposalsSyncState: StateFlow<SyncState> = _scheduleProposalsSyncState.asStateFlow()

    @Volatile private var groupMembersRequestGeneration = 0
    @Volatile private var groupMembersSourceGroupId: String? = null
    private val _groupMembersState = MutableStateFlow<com.magd.tanweer.data.model.GroupMembersResponse?>(null)
    val groupMembersState: StateFlow<com.magd.tanweer.data.model.GroupMembersResponse?> = _groupMembersState.asStateFlow()
    private val _groupMembersSnapshotGroupId = MutableStateFlow<String?>(null)
    val groupMembersSnapshotGroupId: StateFlow<String?> = _groupMembersSnapshotGroupId.asStateFlow()
    private val _isGroupMembersLoading = MutableStateFlow(false)
    val isGroupMembersLoading: StateFlow<Boolean> = _isGroupMembersLoading.asStateFlow()
    private val _groupMembersError = MutableStateFlow<String?>(null)
    val groupMembersError: StateFlow<String?> = _groupMembersError.asStateFlow()

    // Dashboard State Flows
    private val _teacherDashboard = MutableStateFlow<TeacherDashboardResponse?>(null)
    val teacherDashboard: StateFlow<TeacherDashboardResponse?> = _teacherDashboard.asStateFlow()
    private val _teacherDashboardError = MutableStateFlow<String?>(null)
    val teacherDashboardError: StateFlow<String?> = _teacherDashboardError.asStateFlow()

    private val _moderatorDashboard = MutableStateFlow<ModeratorDashboardResponse?>(null)
    val moderatorDashboard: StateFlow<ModeratorDashboardResponse?> = _moderatorDashboard.asStateFlow()
    private val _moderatorDashboardError = MutableStateFlow<String?>(null)
    val moderatorDashboardError: StateFlow<String?> = _moderatorDashboardError.asStateFlow()

    private val _adminDashboard = MutableStateFlow<AdminDashboardResponse?>(null)
    val adminDashboard: StateFlow<AdminDashboardResponse?> = _adminDashboard.asStateFlow()
    private val _adminDashboardError = MutableStateFlow<String?>(null)
    val adminDashboardError: StateFlow<String?> = _adminDashboardError.asStateFlow()

    private val _myNotifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val myNotifications: StateFlow<List<AppNotification>> = _myNotifications.asStateFlow()

    @Volatile private var groupCorrectionsRequestGeneration = 0
    @Volatile private var groupCorrectionsSourceGroupId: String? = null
    private val _groupCorrections = MutableStateFlow<List<ContentCorrectionItem>>(emptyList())
    val groupCorrections: StateFlow<List<ContentCorrectionItem>> = _groupCorrections.asStateFlow()
    private val _groupCorrectionsSnapshotGroupId = MutableStateFlow<String?>(null)
    val groupCorrectionsSnapshotGroupId: StateFlow<String?> = _groupCorrectionsSnapshotGroupId.asStateFlow()
    private val _groupCorrectionsError = MutableStateFlow<String?>(null)
    val groupCorrectionsError: StateFlow<String?> = _groupCorrectionsError.asStateFlow()
    private val _isGroupCorrectionsLoading = MutableStateFlow(false)
    val isGroupCorrectionsLoading: StateFlow<Boolean> = _isGroupCorrectionsLoading.asStateFlow()

    @Volatile private var communityDecisionsRequestGeneration = 0
    @Volatile private var communityDecisionsSourceGroupId: String? = null
    private val _communityDecisions = MutableStateFlow<List<CommunityDecisionItem>>(emptyList())
    val communityDecisions: StateFlow<List<CommunityDecisionItem>> = _communityDecisions.asStateFlow()
    private val _communityDecisionsSnapshotGroupId = MutableStateFlow<String?>(null)
    val communityDecisionsSnapshotGroupId: StateFlow<String?> = _communityDecisionsSnapshotGroupId.asStateFlow()
    private val _communityDecisionsError = MutableStateFlow<String?>(null)
    val communityDecisionsError: StateFlow<String?> = _communityDecisionsError.asStateFlow()
    private val _isCommunityDecisionsLoading = MutableStateFlow(false)
    val isCommunityDecisionsLoading: StateFlow<Boolean> = _isCommunityDecisionsLoading.asStateFlow()

    @Volatile private var teacherDashboardRequestGeneration = 0
    @Volatile private var moderatorDashboardRequestGeneration = 0
    @Volatile private var adminDashboardRequestGeneration = 0

    private val _isTeacherDashboardLoading = MutableStateFlow(false)
    val isTeacherDashboardLoading: StateFlow<Boolean> = _isTeacherDashboardLoading.asStateFlow()
    private val _isModeratorDashboardLoading = MutableStateFlow(false)
    val isModeratorDashboardLoading: StateFlow<Boolean> = _isModeratorDashboardLoading.asStateFlow()
    private val _isAdminDashboardLoading = MutableStateFlow(false)
    val isAdminDashboardLoading: StateFlow<Boolean> = _isAdminDashboardLoading.asStateFlow()

    fun loadTeacherDashboard() {
        viewModelScope.launch {
            val user = currentUser.value ?: return@launch
            if (!user.role.equals("TEACHER", ignoreCase = true) || _isTeacherDashboardLoading.value) return@launch
            val requestId = ++teacherDashboardRequestGeneration
            _isTeacherDashboardLoading.value = true
            _teacherDashboardError.value = null
            runTeacherDashboardRequest(user, requestId)
        }
    }

    private suspend fun runTeacherDashboardRequest(user: User, requestId: Int) {
        val isCurrent = {
            isCurrentDashboardRequest(
                requestId,
                teacherDashboardRequestGeneration,
                user.id,
                currentUser.value?.id,
                user.role,
                currentUser.value?.role
            )
        }
        if (!isCurrent()) {
            if (requestId == teacherDashboardRequestGeneration) _isTeacherDashboardLoading.value = false
            return
        }
        try {
            repository.getTeacherDashboard()
                .onSuccess { data -> if (isCurrent()) _teacherDashboard.value = data }
                .onFailure { error ->
                    if (isCurrent()) _teacherDashboardError.value = error.message ?: "تعذر تحميل بيانات مركز المعلم"
                }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (isCurrent()) _teacherDashboardError.value = error.message ?: "تعذر تحميل بيانات مركز المعلم"
        } finally {
            if (requestId == teacherDashboardRequestGeneration) _isTeacherDashboardLoading.value = false
        }
    }

    fun loadModeratorDashboard() {
        viewModelScope.launch {
            val user = currentUser.value ?: return@launch
            if (!user.role.equals("MODERATOR", ignoreCase = true) || _isModeratorDashboardLoading.value) return@launch
            val requestId = ++moderatorDashboardRequestGeneration
            _isModeratorDashboardLoading.value = true
            _moderatorDashboardError.value = null
            runModeratorDashboardRequest(user, requestId)
        }
    }

    private suspend fun runModeratorDashboardRequest(user: User, requestId: Int) {
        val isCurrent = {
            isCurrentDashboardRequest(
                requestId,
                moderatorDashboardRequestGeneration,
                user.id,
                currentUser.value?.id,
                user.role,
                currentUser.value?.role
            )
        }
        if (!isCurrent()) {
            if (requestId == moderatorDashboardRequestGeneration) _isModeratorDashboardLoading.value = false
            return
        }
        try {
            repository.getModeratorDashboard()
                .onSuccess { data -> if (isCurrent()) _moderatorDashboard.value = data }
                .onFailure { error ->
                    if (isCurrent()) _moderatorDashboardError.value = error.message ?: "تعذر تحميل بيانات مركز الإشراف"
                }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (isCurrent()) _moderatorDashboardError.value = error.message ?: "تعذر تحميل بيانات مركز الإشراف"
        } finally {
            if (requestId == moderatorDashboardRequestGeneration) _isModeratorDashboardLoading.value = false
        }
    }

    fun loadAdminDashboard() {
        viewModelScope.launch {
            val user = currentUser.value ?: return@launch
            if (!user.role.equals("ADMIN", ignoreCase = true) || _isAdminDashboardLoading.value) return@launch
            val requestId = ++adminDashboardRequestGeneration
            _isAdminDashboardLoading.value = true
            _adminDashboardError.value = null
            runAdminDashboardRequest(user, requestId)
        }
    }

    private suspend fun runAdminDashboardRequest(user: User, requestId: Int) {
        val isCurrent = {
            isCurrentDashboardRequest(
                requestId,
                adminDashboardRequestGeneration,
                user.id,
                currentUser.value?.id,
                user.role,
                currentUser.value?.role
            )
        }
        if (!isCurrent()) {
            if (requestId == adminDashboardRequestGeneration) _isAdminDashboardLoading.value = false
            return
        }
        try {
            repository.getAdminDashboard()
                .onSuccess { data ->
                    if (isCurrent()) {
                        if (data.success) {
                            _adminDashboard.value = data
                        } else {
                            _adminDashboard.value = null
                            _adminDashboardError.value = "رد الخادم لم يؤكد صلاحية بيانات لوحة الإدارة"
                        }
                    }
                }
                .onFailure { error ->
                    if (isCurrent()) {
                        _adminDashboard.value = null
                        _adminDashboardError.value = error.message ?: "تعذر تحميل بيانات لوحة الإدارة"
                    }
                }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (isCurrent()) {
                _adminDashboard.value = null
                _adminDashboardError.value = error.message ?: "تعذر تحميل بيانات لوحة الإدارة"
            }
        } finally {
            if (requestId == adminDashboardRequestGeneration) _isAdminDashboardLoading.value = false
        }
    }

    fun setSchoolStudentStatus(
        userId: String,
        status: String,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            repository.updateSchoolStudentStatus(userId, status)
                .onSuccess { message ->
                    loadAdminDashboard()
                    onResult(true, message)
                }
                .onFailure { error -> onResult(false, error.message ?: "تعذر تحديث حساب الطالب") }
        }
    }

    fun removeStudentFromClass(
        userId: String,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            repository.removeSchoolStudentFromClass(userId)
                .onSuccess { message ->
                    loadAdminDashboard()
                    onResult(true, message)
                }
                .onFailure { error -> onResult(false, error.message ?: "تعذر فصل الطالب من شعبته") }
        }
    }
    fun loadMyNotifications() {
        viewModelScope.launch {
            repository.getMyNotifications().onSuccess { notifications ->
                _myNotifications.value = notifications
            }
        }
    }

    fun generateMyRecoveryCode(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.generateMyRecoveryCode()
            result.onSuccess { code ->
                loadMyNotifications()
                onResult(true, code)
            }.onFailure { error ->
                onResult(false, error.message ?: "تعذر تغيير رمز الاسترداد")
            }
        }
    }

    fun loadGroupCorrections(groupId: String = getActiveGroupId()) {
        if (groupId.isBlank()) {
            groupCorrectionsRequestGeneration++
            groupCorrectionsSourceGroupId = null
            _groupCorrections.value = emptyList()
            _groupCorrectionsSnapshotGroupId.value = null
            _groupCorrectionsError.value = "لا توجد شعبة نشطة لعرض طلبات التصويب."
            _isGroupCorrectionsLoading.value = false
            return
        }
        if (groupCorrectionsSourceGroupId != groupId) {
            groupCorrectionsSourceGroupId = groupId
            _groupCorrectionsSnapshotGroupId.value = null
            _groupCorrections.value = emptyList()
        }
        val requestId = ++groupCorrectionsRequestGeneration
        viewModelScope.launch {
            _isGroupCorrectionsLoading.value = true
            _groupCorrectionsError.value = null
            try {
                repository.getGroupCorrections(groupId)
                    .onSuccess { list ->
                        if (requestId == groupCorrectionsRequestGeneration && groupCorrectionsSourceGroupId == groupId) {
                            _groupCorrections.value = list
                            _groupCorrectionsSnapshotGroupId.value = groupId
                        }
                    }
                    .onFailure { error ->
                        if (requestId == groupCorrectionsRequestGeneration) {
                            _groupCorrectionsError.value = error.message ?: "تعذر تحميل طلبات التصويب"
                        }
                    }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (requestId == groupCorrectionsRequestGeneration) {
                    _groupCorrectionsError.value = error.message ?: "تعذر تحميل طلبات التصويب"
                }
            } finally {
                if (requestId == groupCorrectionsRequestGeneration) _isGroupCorrectionsLoading.value = false
            }
        }
    }

    fun submitContentCorrection(
        contentId: String,
        fieldName: String,
        originalValue: String?,
        proposedValue: String,
        reason: String,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val res = repository.submitContentCorrection(contentId, fieldName, originalValue, proposedValue, reason)
            res.onSuccess { msg ->
                loadGroupCorrections()
                onResult(true, msg)
            }.onFailure { err ->
                onResult(false, err.message ?: "فشل إرسال طلب التصحيح")
            }
        }
    }

    fun approveCorrection(
        correctionId: String,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val res = repository.approveCorrection(correctionId)
            res.onSuccess { msg ->
                loadGroupCorrections()
                loadModeratorDashboard()
                onResult(true, msg)
            }.onFailure { err ->
                onResult(false, err.message ?: "تعذر اعتماد التصحيح")
            }
        }
    }

    fun rejectCorrection(
        correctionId: String,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val res = repository.rejectCorrection(correctionId)
            res.onSuccess { msg ->
                loadGroupCorrections()
                loadModeratorDashboard()
                onResult(true, msg)
            }.onFailure { err ->
                onResult(false, err.message ?: "تعذر رفض التصحيح")
            }
        }
    }

    fun loadCommunityDecisions(groupId: String = getActiveGroupId()) {
        if (groupId.isBlank()) {
            communityDecisionsRequestGeneration++
            communityDecisionsSourceGroupId = null
            _communityDecisionsSnapshotGroupId.value = null
            _communityDecisions.value = emptyList()
            _communityDecisionsError.value = "لا توجد شعبة نشطة لعرض القرارات الجماعية."
            _isCommunityDecisionsLoading.value = false
            return
        }
        if (communityDecisionsSourceGroupId != groupId) {
            communityDecisionsSourceGroupId = groupId
            _communityDecisionsSnapshotGroupId.value = null
            _communityDecisions.value = emptyList()
        }
        val requestId = ++communityDecisionsRequestGeneration
        viewModelScope.launch {
            _isCommunityDecisionsLoading.value = true
            _communityDecisionsError.value = null
            try {
                repository.getCommunityDecisions(groupId)
                    .onSuccess { list ->
                        if (requestId == communityDecisionsRequestGeneration && communityDecisionsSourceGroupId == groupId) {
                            _communityDecisions.value = list
                            _communityDecisionsSnapshotGroupId.value = groupId
                        }
                    }
                    .onFailure { error ->
                        if (requestId == communityDecisionsRequestGeneration) {
                            _communityDecisionsError.value = error.message ?: "تعذر تحميل القرارات الجماعية"
                        }
                    }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (requestId == communityDecisionsRequestGeneration) {
                    _communityDecisionsError.value = error.message ?: "تعذر تحميل القرارات الجماعية"
                }
            } finally {
                if (requestId == communityDecisionsRequestGeneration) _isCommunityDecisionsLoading.value = false
            }
        }
    }

    fun voteCommunityDecision(
        decisionId: String,
        voteChoice: Int,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val res = repository.voteCommunityDecision(decisionId, voteChoice)
            res.onSuccess { msg ->
                // A unified decision may have just changed the active schedule.
                // Refresh both sources so the schedule and governance screens cannot drift.
                loadCommunityDecisions()
                loadModeratorDashboard()
                loadScheduleData()
                onResult(true, msg)
            }.onFailure { err ->
                // The server may have closed an expired decision while rejecting this vote.
                // Refresh to display its authoritative terminal state immediately.
                loadCommunityDecisions()
                loadScheduleData()
                onResult(false, err.message ?: "تعذر تسجيل التصويت")
            }
        }
    }

    fun loadScheduleData(
        groupId: String = getActiveGroupId().ifBlank { currentUser.value?.defaultGroupId.orEmpty() }
    ) {
        if (groupId.isBlank()) {
            scheduleMetaRequestGeneration++
            scheduleMetaSourceGroupId = null
            _scheduleMetaGroupId.value = null
            _scheduleMetaSnapshotGroupId.value = null
            _scheduleMetaSyncState.value = SyncState.Idle
            _scheduleMeta.value = null
            _scheduleVersion.value = null
            loadScheduleProposals("")
            return
        }

        val requestId = ++scheduleMetaRequestGeneration
        if (scheduleMetaSourceGroupId != groupId) {
            scheduleMetaSourceGroupId = groupId
            _scheduleMetaSnapshotGroupId.value = null
            _scheduleMeta.value = null
            _scheduleVersion.value = null
        }
        _scheduleMetaGroupId.value = groupId
        _scheduleMetaSyncState.value = SyncState.Loading

        viewModelScope.launch {
            try {
                val result = repository.getScheduleMetaAndSlots(groupId)
                if (requestId != scheduleMetaRequestGeneration || scheduleMetaSourceGroupId != groupId) {
                    return@launch
                }
                result.onSuccess { data ->
                    if (data.success && data.meta != null) {
                        _scheduleMeta.value = data.meta
                        _scheduleVersion.value = data.version
                        _scheduleMetaSnapshotGroupId.value = groupId
                        _scheduleMetaSyncState.value = SyncState.Success()
                    } else {
                        _scheduleMetaSyncState.value = SyncState.Error(
                            "رد الخادم لم يؤكد بيانات صلاحيات الجدول لهذه الشعبة",
                            false
                        )
                    }
                }.onFailure { error ->
                    _scheduleMetaSyncState.value = SyncState.Error(
                        error.message ?: "تعذر تحميل إعدادات الجدول",
                        false
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (requestId == scheduleMetaRequestGeneration && scheduleMetaSourceGroupId == groupId) {
                    _scheduleMetaSyncState.value = SyncState.Error(
                        error.message ?: "تعذر تحميل إعدادات الجدول",
                        false
                    )
                }
            }
        }

        loadScheduleProposals(groupId)
    }

    fun loadScheduleProposals(
        groupId: String = getActiveGroupId().ifBlank { currentUser.value?.defaultGroupId.orEmpty() }
    ) {
        if (groupId.isBlank()) {
            scheduleProposalsRequestGeneration++
            scheduleProposalsSourceGroupId = null
            _scheduleProposalsGroupId.value = null
            _scheduleProposalsSnapshotGroupId.value = null
            _scheduleProposalsSyncState.value = SyncState.Idle
            _scheduleProposals.value = emptyList()
            return
        }

        val requestId = ++scheduleProposalsRequestGeneration
        if (scheduleProposalsSourceGroupId != groupId) {
            scheduleProposalsSourceGroupId = groupId
            _scheduleProposalsSnapshotGroupId.value = null
            _scheduleProposals.value = emptyList()
        }
        _scheduleProposalsGroupId.value = groupId
        _scheduleProposalsSyncState.value = SyncState.Loading

        viewModelScope.launch {
            try {
                val result = repository.getScheduleProposals(groupId)
                if (requestId != scheduleProposalsRequestGeneration || scheduleProposalsSourceGroupId != groupId) {
                    return@launch
                }
                result.onSuccess { list ->
                    _scheduleProposals.value = list
                    _scheduleProposalsSnapshotGroupId.value = groupId
                    _scheduleProposalsSyncState.value = SyncState.Success()
                }.onFailure { error ->
                    _scheduleProposalsSyncState.value = SyncState.Error(
                        error.message ?: "تعذر تحميل مقترحات الجدول",
                        false
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (requestId == scheduleProposalsRequestGeneration && scheduleProposalsSourceGroupId == groupId) {
                    _scheduleProposalsSyncState.value = SyncState.Error(
                        error.message ?: "تعذر تحميل مقترحات الجدول",
                        false
                    )
                }
            }
        }
    }

    fun saveScheduleBatch(
        slots: List<ScheduleSlot>,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val groupId = getActiveGroupId().ifBlank { currentUser.value?.defaultGroupId.orEmpty() }
        viewModelScope.launch {
            val res = repository.saveScheduleBatch(groupId, slots)
            res.onSuccess { msg ->
                if (getActiveGroupId().ifBlank { currentUser.value?.defaultGroupId.orEmpty() } == groupId) loadScheduleData(groupId)
                onResult(true, msg)
            }.onFailure { err ->
                onResult(false, err.message ?: "تعذر حفظ الجدول")
            }
        }
    }

    fun voteScheduleProposal(
        proposalId: String,
        voteType: String,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val groupId = getActiveGroupId().ifBlank { currentUser.value?.defaultGroupId.orEmpty() }
        viewModelScope.launch {
            val res = repository.voteScheduleProposal(proposalId, voteType)
            res.onSuccess { msg ->
                if (getActiveGroupId().ifBlank { currentUser.value?.defaultGroupId.orEmpty() } == groupId) loadScheduleData(groupId)
                onResult(true, msg)
            }.onFailure { err ->
                onResult(false, err.message ?: "تعذر التصويت")
            }
        }
    }

    fun approveScheduleProposal(
        proposalId: String,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (!canManageSchedule()) {
            onResult(false, "عذراً، اعتماد مقترحات الجدول محصور بالمعلمين وإدارة المدرسة")
            return
        }
        val groupId = getActiveGroupId().ifBlank { currentUser.value?.defaultGroupId.orEmpty() }
        viewModelScope.launch {
            val res = repository.approveScheduleProposal(proposalId, groupId)
            res.onSuccess { msg ->
                if (getActiveGroupId().ifBlank { currentUser.value?.defaultGroupId.orEmpty() } == groupId) loadScheduleData(groupId)
                onResult(true, msg)
            }.onFailure { err ->
                onResult(false, err.message ?: "تعذر اعتماد المقترح")
            }
        }
    }

    fun rejectScheduleProposal(
        proposalId: String,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val groupId = getActiveGroupId().ifBlank { currentUser.value?.defaultGroupId.orEmpty() }
        if (!canManageSchedule()) {
            onResult(false, "عذراً، رفض مقترحات الجدول محصور بالمعلمين وإدارة المدرسة")
            return
        }
        viewModelScope.launch {
            val res = repository.rejectScheduleProposal(proposalId)
            res.onSuccess { msg ->
                if (getActiveGroupId().ifBlank { currentUser.value?.defaultGroupId.orEmpty() } == groupId) loadScheduleProposals(groupId)
                onResult(true, msg)
            }.onFailure { err ->
                onResult(false, err.message ?: "تعذر رفض المقترح")
            }
        }
    }

    fun loadGroupMembers(groupId: String = getActiveGroupId()) {
        if (groupId.isBlank()) {
            groupMembersRequestGeneration++
            groupMembersSourceGroupId = null
            _groupMembersState.value = null
            _groupMembersSnapshotGroupId.value = null
            _groupMembersError.value = "لا توجد شعبة نشطة لعرض الأعضاء."
            _isGroupMembersLoading.value = false
            return
        }
        if (groupMembersSourceGroupId != groupId) {
            groupMembersSourceGroupId = groupId
            _groupMembersState.value = null
            _groupMembersSnapshotGroupId.value = null
        }
        val requestId = ++groupMembersRequestGeneration
        viewModelScope.launch {
            _groupMembersError.value = null
            _isGroupMembersLoading.value = true
            try {
                repository.getGroupMembers(groupId)
                    .onSuccess { data ->
                        if (requestId == groupMembersRequestGeneration && groupMembersSourceGroupId == groupId) {
                            if (data.success) {
                                _groupMembersState.value = data
                                _groupMembersSnapshotGroupId.value = groupId
                            } else _groupMembersError.value = "تعذر تحميل قائمة أعضاء المجموعة من الخادم."
                        }
                    }
                    .onFailure { error ->
                        if (requestId == groupMembersRequestGeneration) {
                            _groupMembersError.value = error.message ?: "تعذر تحميل أعضاء المجموعة. تحقق من الاتصال ثم أعد المحاولة."
                        }
                    }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (requestId == groupMembersRequestGeneration) {
                    _groupMembersError.value = error.message ?: "تعذر تحميل أعضاء المجموعة"
                }
            } finally {
                if (requestId == groupMembersRequestGeneration) _isGroupMembersLoading.value = false
            }
        }
    }

    fun updateGroupMemberRole(
        targetUserId: String,
        newRole: String,
        groupId: String = getActiveGroupId(),
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val res = repository.updateGroupMemberRole(groupId, targetUserId, newRole)
            res.onSuccess { msg ->
                loadGroupMembers(groupId)
                onResult(true, msg)
            }.onFailure { err ->
                onResult(false, err.message ?: "تعذر تحديث رتبة العضو")
            }
        }
    }

    fun removeGroupMember(
        targetUserId: String,
        isBan: Boolean,
        groupId: String = getActiveGroupId(),
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val res = repository.removeGroupMember(groupId, targetUserId, isBan)
            res.onSuccess { msg ->
                loadGroupMembers(groupId)
                onResult(true, msg)
            }.onFailure { err ->
                onResult(false, err.message ?: "تعذر تنفيذ الإجراء")
            }
        }
    }

    fun approveGroupJoinRequest(
        targetUserId: String,
        groupId: String = getActiveGroupId(),
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val res = repository.approveGroupJoinRequest(groupId, targetUserId)
            res.onSuccess { msg ->
                loadGroupMembers(groupId)
                onResult(true, msg)
            }.onFailure { err ->
                onResult(false, err.message ?: "تعذر اعتماد طلب الانضمام")
            }
        }
    }

    fun rejectGroupJoinRequest(
        targetUserId: String,
        groupId: String = getActiveGroupId(),
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val res = repository.rejectGroupJoinRequest(groupId, targetUserId)
            res.onSuccess { msg ->
                loadGroupMembers(groupId)
                onResult(true, msg)
            }.onFailure { err ->
                onResult(false, err.message ?: "تعذر رفض طلب الانضمام")
            }
        }
    }

    fun canManageSchedule(): Boolean {
        val meta = _scheduleMeta.value
        if (meta?.isInitialSetup == true) return true
        if (meta?.canEditDirectly == true) return true
        return currentUser.value?.hasPermission(Permission.MANAGE_SCHEDULE) == true
    }

    fun proposeScheduleSlotChange(
        dayOfWeek: Int,
        slotOrder: Int,
        oldSubjectId: String?,
        newSubjectId: String,
        reason: String,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val groupId = getActiveGroupId()
        viewModelScope.launch {
            val result = repository.proposeScheduleSlotChange(
                groupId = groupId,
                dayOfWeek = dayOfWeek,
                slotOrder = slotOrder,
                oldSubjectId = oldSubjectId,
                newSubjectId = newSubjectId,
                reason = reason
            )
            result.onSuccess { msg ->
                // A schedule proposal is also a unified community decision.
                // Take the user straight to that shared decision list after the server confirms it.
                loadCommunityDecisions()
                setSubScreen(SubScreen.COMMUNITY_DECISIONS)
                onResult(true, msg)
            }.onFailure { err ->
                onResult(false, err.message ?: "تعذر إرسال المقترح")
            }
        }
    }

    private val _myRoleRequests = MutableStateFlow<List<RoleRequestItem>>(emptyList())
    val myRoleRequests: StateFlow<List<RoleRequestItem>> = _myRoleRequests.asStateFlow()

    fun loadMyRoleRequests() {
        viewModelScope.launch {
            val list = repository.getMyRoleRequests()
            _myRoleRequests.value = list
        }
    }

    fun submitRoleUpgradeRequest(
        requestedRole: Role,
        reason: String,
        onResult: (Boolean, com.magd.tanweer.data.model.SubmitRoleUpgradeResponse?, String) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.submitRoleUpgradeRequest(
                requestedRole = requestedRole.name,
                reason = reason,
                groupId = getActiveGroupId().ifBlank { null }
            )
            res.onSuccess { resp ->
                loadMyRoleRequests()
                onResult(true, resp, resp.message ?: "تم إرسال طلب الترقية بنجاح")
            }.onFailure { err ->
                onResult(false, null, err.message ?: "فشل إرسال طلب الترقية")
            }
        }
    }

    fun redeemRoleCode(
        code: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.redeemRoleCode(code.trim())
            res.onSuccess { resp ->
                loadMyRoleRequests()
                repository.syncProfile()
                onResult(true, resp.message ?: "تم تفعيل الرتبة بنجاح 🎉")
            }.onFailure { err ->
                onResult(false, err.message ?: "رمز التفعيل غير صالح أو منتهي الصلاحية")
            }
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
        _isCheckingAuth.value = true
        viewModelScope.launch {
            val result = repository.register(fullName, phone, pass, gradeId, sectionId)
            result.onSuccess { pair ->
                restoreAcademicSelection(pair.first)
                if (pair.second.isNotEmpty()) {
                    _recoveryCodeDialog.value = pair.second
                }
                // The current-user startup coordinator performs the required server pull.
                onSuccess()
            }.onFailure { err ->
                _isCheckingAuth.value = false
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
        _isCheckingAuth.value = true
        viewModelScope.launch {
            val result = repository.login(phone, pass)
            result.onSuccess { user ->
                restoreAcademicSelection(user)
                // The current-user startup coordinator performs the required server pull.
                onSuccess()
            }.onFailure { err ->
                _isCheckingAuth.value = false
                onError(err.message ?: "فشل تسجيل الدخول")
            }
        }
    }

    fun loginWithRecoveryCode(
        code: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        _isCheckingAuth.value = true
        viewModelScope.launch {
            val result = repository.loginWithRecoveryCode(code)
            result.onSuccess { user ->
                restoreAcademicSelection(user)
                // The current-user startup coordinator performs the required server pull.
                onSuccess()
            }.onFailure { err ->
                _isCheckingAuth.value = false
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
            val previousDefaultGroupId = currentUser.value?.defaultGroupId
            val result = repository.updateProfile(fullName, gradeId, sectionId)
            result.onSuccess { user ->
                restoreAcademicSelection(user, resetGroup = previousDefaultGroupId != user.defaultGroupId)
                syncInitialData(getActiveGroupId(), _selectedDate.value, isExplicitAction = true)
                onSuccess()
            }.onFailure { err ->
                onError(err.message ?: "فشل تحديث البيانات")
            }
        }
    }

    fun logout() {
        _isCheckingAuth.value = true
        viewModelScope.launch {
            try {
                repository.logout(getApplication())
            } catch (e: Exception) {
                android.util.Log.e("TanweerViewModel", "Local logout cleanup failed", e)
            } finally {
                dataGenerationVerified = false
                resetUserScopedUiState()
                _isCheckingAuth.value = false
            }
        }
    }

    fun addLesson(
        subjectId: String,
        title: String,
        description: String?,
        date: String = _selectedDate.value
    ) {
        viewModelScope.launch {
            addLessonWithPages(subjectId, title, description, date, emptyList())
        }
    }

    suspend fun addLessonWithPages(
        subjectId: String,
        title: String,
        description: String?,
        date: String = _selectedDate.value,
        pages: List<com.magd.tanweer.util.ProcessedPageResult>
    ): Result<PublicationResult> {
        val user = currentUser.value
            ?: return Result.failure(Exception("لم يتم تسجيل الدخول"))
        val groupId = getActiveGroupId()
        val gradeName = user.gradeId?.let { SchoolHierarchy.getGradeName(it) } ?: "الإدارة المدرسية"
        val secAr = user.sectionId?.let { SchoolHierarchy.getSectionArabicName(it) } ?: ""
        val res = repository.addLessonWithPages(
            groupId = groupId,
            date = date,
            subjectId = subjectId,
            title = title,
            description = description,
            authorName = user.fullName,
            authorGradeSection = "$gradeName — $secAr",
            pages = pages
        )
        if (res.isSuccess) {
            closeUploadDialog()
        }
        return res
    }

    val todayPlannedHomeworkIds = currentUser.flatMapLatest { user ->
        if (user != null) {
            repository.getPlannedHomeworkIds(user.id, getTodayDateString())
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun togglePlannedHomework(
        homeworkId: String,
        title: String,
        subjectName: String?,
        onResult: ((isNowPlanned: Boolean) -> Unit)? = null
    ) {
        val user = currentUser.value ?: return
        val today = getTodayDateString()
        viewModelScope.launch {
            val planned = todayPlannedHomeworkIds.value.contains(homeworkId)
            if (planned) {
                repository.removeDailyPlanByHomework(homeworkId, user.id)
                onResult?.invoke(false)
            } else {
                repository.addToDailyPlan(
                    userId = user.id,
                    homeworkId = homeworkId,
                    title = title,
                    subjectName = subjectName,
                    planDate = today
                )
                onResult?.invoke(true)
            }
        }
    }

    fun getDailyPlanForDate(date: String = getTodayDateString()): Flow<List<DailyPlanItem>> {
        val userId = currentUser.value?.id ?: ""
        return repository.getDailyPlan(userId, date)
    }

    fun toggleDailyPlanItem(id: String, currentCompleted: Boolean) {
        viewModelScope.launch {
            repository.toggleDailyPlanItem(id, !currentCompleted)
        }
    }

    fun removeDailyPlanItem(id: String) {
        viewModelScope.launch {
            repository.removeDailyPlanItem(id)
        }
    }

    fun addHomework(
        subjectId: String,
        title: String,
        details: String?,
        pageNumbers: String?,
        questionNumbers: String?,
        dueDate: String,
        studyDate: String = _selectedDate.value,
        taskType: String = "HOMEWORK",
        pages: List<com.magd.tanweer.util.ProcessedPageResult> = emptyList(),
        isOfficial: Boolean = false,
        onResult: ((Result<PublicationResult>) -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ) {
        val user = currentUser.value ?: return
        val groupId = getActiveGroupId()
        viewModelScope.launch {
            val result = if (pages.isNotEmpty()) {
                repository.addHomeworkWithImages(
                    groupId = groupId,
                    studyDate = studyDate,
                    dueDate = dueDate,
                    subjectId = subjectId,
                    title = title,
                    details = details,
                    pageNumbers = pageNumbers,
                    questionNumbers = questionNumbers,
                    taskType = taskType,
                    pages = pages,
                    isOfficial = isOfficial
                )
            } else {
                repository.addHomework(
                    groupId = groupId,
                    studyDate = studyDate,
                    dueDate = dueDate,
                    subjectId = subjectId,
                    title = title,
                    details = details,
                    pageNumbers = pageNumbers,
                    questionNumbers = questionNumbers,
                    taskType = taskType,
                    isOfficial = isOfficial
                )
            }
            onResult?.invoke(result)
            if (result.isSuccess) {
                onComplete?.invoke()
            }
        }
    }

    fun toggleHomework(
        homeworkId: String,
        currentStatus: Boolean,
        onResult: ((com.magd.tanweer.data.local.HomeworkToggleResult) -> Unit)? = null
    ) {
        viewModelScope.launch {
            triggerHaptic(com.magd.tanweer.util.TanweerHapticManager.HapticType.CLICK)
            val res = repository.toggleHomeworkCompletion(homeworkId, currentStatus)
            if (res is com.magd.tanweer.data.local.HomeworkToggleResult.SyncedWithServer) {
                triggerHaptic(com.magd.tanweer.util.TanweerHapticManager.HapticType.SUCCESS)
            }
            onResult?.invoke(res)
        }
    }

    fun addExam(
        subjectId: String,
        title: String,
        requiredChapters: String?,
        notes: String?,
        examDate: String,
        examType: String = "MONTHLY",
        isOfficial: Boolean = false,
        onResult: ((Result<com.magd.tanweer.data.local.ExamAddResult>) -> Unit)? = null
    ) {
        val user = currentUser.value ?: return
        val groupId = getActiveGroupId()
        viewModelScope.launch {
            val res = repository.addExam(
                groupId = groupId,
                examDate = examDate,
                subjectId = subjectId,
                title = title,
                requiredChapters = requiredChapters,
                notes = notes,
                examType = examType,
                isOfficial = isOfficial
            )
            onResult?.invoke(res)
        }
    }

    fun updateExam(
        examId: String,
        subjectId: String,
        title: String,
        requiredChapters: String,
        notes: String,
        examDate: String,
        examType: String,
        onResult: ((Result<Unit>) -> Unit)? = null
    ) {
        viewModelScope.launch {
            onResult?.invoke(
                repository.updateExam(
                    examId = examId,
                    examDate = examDate,
                    subjectId = subjectId,
                    title = title,
                    requiredChapters = requiredChapters,
                    notes = notes,
                    examType = examType
                )
            )
        }
    }
    fun retryFailedExam(examId: String, onResult: ((Result<Unit>) -> Unit)? = null) {
        viewModelScope.launch {
            onResult?.invoke(repository.retryFailedExam(examId))
        }
    }

    fun discardFailedExam(examId: String, onResult: ((Result<Unit>) -> Unit)? = null) {
        viewModelScope.launch {
            onResult?.invoke(repository.discardFailedExam(examId))
        }
    }
    fun deleteExam(examId: String, onResult: ((Result<com.magd.tanweer.data.local.ExamDeleteResult>) -> Unit)? = null) {
        viewModelScope.launch {
            val res = repository.deleteExamById(examId)
            onResult?.invoke(res)
        }
    }

    fun addEvent(
        title: String,
        description: String?,
        category: String,
        eventDate: String,
        timeStr: String?,
        location: String?,
        onResult: ((Result<com.magd.tanweer.data.local.EventAddResult>) -> Unit)? = null
    ) {
        val user = currentUser.value ?: return
        val groupId = getActiveGroupId()
        viewModelScope.launch {
            val res = repository.addEvent(
                groupId = groupId,
                eventDate = eventDate,
                timeStr = timeStr,
                title = title,
                description = description,
                category = category,
                location = location
            )
            onResult?.invoke(res)
        }
    }

    fun createHoliday(
        title: String,
        reason: String,
        eventDate: String,
        isSchoolWide: Boolean,
        onResult: ((Result<com.magd.tanweer.data.local.EventAddResult>) -> Unit)? = null
    ) {
        val user = currentUser.value ?: return
        val groupId = getActiveGroupId()
        viewModelScope.launch {
            val res = repository.createHoliday(
                groupId = groupId,
                eventDate = eventDate,
                title = title,
                reason = reason,
                isSchoolWide = isSchoolWide
            )
            if (res.isSuccess) {
                val ym = eventDate.take(7)
                syncCalendarMonth(groupId, ym)
                repository.syncEvents(groupId)
            }
            onResult?.invoke(res)
        }
    }

    fun updateEvent(
        eventId: String,
        previousEventDate: String,
        eventDate: String,
        timeStr: String?,
        title: String,
        description: String?,
        location: String?,
        onResult: ((Result<Unit>) -> Unit)? = null
    ) {
        if (currentUser.value == null) {
            onResult?.invoke(Result.failure(IllegalStateException("يلزم تسجيل الدخول لتعديل الفعالية")))
            return
        }
        val syncScopeId = getActiveGroupId()
        viewModelScope.launch {
            val result = repository.updateEvent(
                eventId = eventId,
                eventDate = eventDate,
                timeStr = timeStr,
                title = title,
                description = description,
                location = location
            )
            if (result.isSuccess && syncScopeId.isNotBlank()) {
                repository.syncEvents(syncScopeId)
                val previousMonth = previousEventDate.take(7)
                val updatedMonth = eventDate.take(7)
                if (previousMonth.length == 7) repository.syncCalendarMonth(syncScopeId, previousMonth)
                if (updatedMonth.length == 7 && updatedMonth != previousMonth) {
                    repository.syncCalendarMonth(syncScopeId, updatedMonth)
                }
            }
            onResult?.invoke(result)
        }
    }

    fun deleteEvent(
        eventId: String,
        originalEventDate: String,
        onResult: ((Result<Unit>) -> Unit)? = null
    ) {
        if (currentUser.value == null) {
            onResult?.invoke(Result.failure(IllegalStateException("يلزم تسجيل الدخول لحذف الفعالية")))
            return
        }
        val syncScopeId = getActiveGroupId()
        viewModelScope.launch {
            val result = repository.deleteEvent(eventId)
            if (result.isSuccess && syncScopeId.isNotBlank()) {
                repository.syncEvents(syncScopeId)
                val affectedMonth = originalEventDate.take(7)
                if (affectedMonth.length == 7) repository.syncCalendarMonth(syncScopeId, affectedMonth)
            }
            onResult?.invoke(result)
        }
    }

    fun addIssue(
        title: String,
        description: String?,
        subjectId: String? = null,
        homeworkId: String? = null,
        examId: String? = null,
        onResult: ((Result<com.magd.tanweer.data.local.IssueAddResult>) -> Unit)? = null
    ) {
        val user = currentUser.value ?: return
        val groupId = getActiveGroupId()
        viewModelScope.launch {
            val res = repository.createIssue(
                groupId = groupId,
                title = title,
                description = description,
                subjectId = subjectId,
                authorName = user.fullName,
                homeworkId = homeworkId,
                examId = examId
            )
            onResult?.invoke(res)
        }
    }

    fun syncAllComprehensive(onFinished: ((ComprehensiveSyncReport) -> Unit)? = null) {
        viewModelScope.launch {
            val user = currentUser.value
            val groupId = getActiveGroupId()

            val succeeded = mutableListOf<String>()
            val failed = mutableListOf<Pair<String, String>>()
            val offline = mutableListOf<String>()

            fun processResult(sectionName: String, res: com.magd.tanweer.data.repository.SyncResult) {
                when (res) {
                    is com.magd.tanweer.data.repository.SyncResult.Success -> succeeded.add(sectionName)
                    is com.magd.tanweer.data.repository.SyncResult.Offline -> offline.add(sectionName)
                    is com.magd.tanweer.data.repository.SyncResult.Error -> failed.add(sectionName to res.message)
                }
            }

            processResult("الملف الشخصي", repository.syncProfile())
            processResult("الشُعب المدرسية", repository.syncGroups())

            if (groupId.isNotBlank()) {
                processResult("الجدول المدرسي", repository.syncSchedule(groupId))
                processResult("دروس اليوم", repository.syncDay(groupId, _selectedDate.value))
                processResult("الواجبات المنزلية", repository.syncHomeworks(groupId))
                processResult("جدول الاختبارات", repository.syncExams(groupId))
                processResult("استفسارات الشعبة", repository.syncIssues(groupId))
                processResult("الفعاليات المدرسية", repository.syncEvents(groupId))
                if (user != null && user.gradeId != null) {
                    processResult("المناهج والكتب", repository.syncBooks(user.gradeId))
                }
            } else {
                offline.add("أقسام الشعبة والجدول (لم يتم تحديد شعبة دراسية مفعلة بعد)")
            }

            // Sync multi-group academic archive to preserve complete student history across groups as an independent section
            processResult("الأرشيف الأكاديمي التاريخي الشامل", repository.syncStudentAcademicArchive())

            // Flush offline outbox queue with precise tracking
            val outboxResult = try {
                repository.processOutboxQueue()
            } catch (e: Exception) {
                Result.failure(e)
            }
            val processedOutbox = outboxResult.getOrDefault(0)
            val outboxErr = outboxResult.exceptionOrNull()?.message
            if (outboxErr != null) {
                failed.add("طابور العمليات غير المتصلة" to outboxErr)
            } else if (processedOutbox > 0) {
                succeeded.add("طابور العمليات غير المتصلة (تم رفع $processedOutbox عمليات بنجاح)")
            }

            val remainingPending = repository.getPendingOutboxCountSync()

            val isAllOk = failed.isEmpty() && offline.isEmpty() && remainingPending == 0 && outboxErr == null
            val summary = when {
                isAllOk ->
                    "اكتملت مزامنة جميع الأقسام وتأكيدها مع الخادم بنجاح (${succeeded.size} أقسام) ✓"
                failed.isEmpty() && (offline.isNotEmpty() || remainingPending > 0) ->
                    "تم تحديث الأقسام (${succeeded.size} ناجحة). يوجد ${offline.size} أقسام معتمدة على التخزين المؤقت، و$remainingPending عمليات محفوظة بانتظار الرفع ⏳"
                failed.isNotEmpty() && succeeded.isNotEmpty() ->
                    "اكتملت ${succeeded.size} أقسام، وتعذرت مزامنة ${failed.size} أقسام (${failed.joinToString { it.first }}). المعلق محلياً: $remainingPending"
                else ->
                    "تعذر الاتصال بالخادم. بياناتك محفوظة في جهازك وستُزامن تلقائياً فور توفر الإنترنت ⏳"
            }

            triggerHaptic(if (isAllOk) com.magd.tanweer.util.TanweerHapticManager.HapticType.SUCCESS else com.magd.tanweer.util.TanweerHapticManager.HapticType.WARNING)

            val report = ComprehensiveSyncReport(
                succeededSections = succeeded,
                failedSections = failed,
                offlineSections = offline,
                pendingOutboxCount = remainingPending,
                processedOutboxCount = processedOutbox,
                outboxError = outboxErr,
                isAllSucceeded = isAllOk,
                summaryMessage = summary
            )

            onFinished?.invoke(report)
        }
    }

    fun syncIssueDetails(issueId: String) {
        viewModelScope.launch {
            repository.syncIssueDetails(issueId)
        }
    }

    fun addIssueComment(
        issueId: String,
        comment: String,
        onDone: (Result<com.magd.tanweer.data.local.CommentAddResult>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = repository.addIssueComment(issueId, comment)
            onDone(res)
        }
    }

    fun markBestAnswer(
        issueId: String,
        commentId: String,
        onResult: ((Result<Unit>) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val res = repository.markBestAnswer(issueId, commentId)
            onResult?.invoke(res)
        }
    }

    fun retrySyncHomework(homeworkId: String) {
        viewModelScope.launch {
            repository.retrySyncHomework(homeworkId)
        }
    }

    fun retryFailedOutboxMutations(onResult: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val requeued = repository.mutationQueue.retryFailedMutations()
            onResult(requeued)
        }
    }

    fun retrySyncContent(contentId: String) {
        viewModelScope.launch {
            repository.retrySyncContent(contentId)
        }
    }

    fun retrySyncComment(issueId: String, commentId: String, commentText: String) {
        viewModelScope.launch {
            repository.retrySyncIssueComment(issueId, commentId, commentText)
        }
    }

    fun joinGroup(
        groupId: String,
        reason: String? = null,
        onResult: (isSuccess: Boolean, status: String, message: String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.joinGroup(groupId, reason)
            result.onSuccess { response ->
                syncGroups(isRefresh = true)
                val status = response.status ?: "ACTIVE"
                val msg = response.message ?: if (status == "ACTIVE") "تم الانضمام إلى المجموعة بنجاح" else "تم إرسال طلب الانضمام، بانتظار موافقة المشرف"
                onResult(true, status, msg)
            }.onFailure {
                onResult(false, "ERROR", it.message ?: "فشل الانضمام للمجموعة")
            }
        }
    }

    fun createPublicGroup(
        name: String,
        description: String? = null,
        icon: String? = "👥",
        visibility: String = "PUBLIC",
        chatPolicy: String = "MEMBERS",
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.createPublicGroup(name, description, icon, visibility, chatPolicy)
            res.onSuccess {
                syncGroups(isRefresh = true)
                onResult(true, it.message ?: "تم إنشاء المجموعة بنجاح")
            }.onFailure {
                onResult(false, it.message ?: "تعذر إنشاء المجموعة")
            }
        }
    }

    fun leaveGroup(groupId: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            repository.leaveGroup(groupId)
                .onSuccess { message ->
                    syncGroups(isRefresh = true)
                    if (_selectedGroupId.value == groupId) {
                        currentUser.value?.defaultGroupId?.takeIf { it.isNotBlank() && it != groupId }?.let {
                            setSelectedGroup(it)
                        }
                    }
                    onResult(true, message)
                }
                .onFailure { error ->
                    onResult(false, error.message ?: "تعذر مغادرة المجموعة")
                }
        }
    }

    fun dismissSchoolAnnouncement(
        eventId: String,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            repository.dismissSchoolAnnouncement(eventId)
                .onSuccess { onResult(true, "تم إخفاء التعميم من هذا الحساب فقط") }
                .onFailure { error -> onResult(false, error.message ?: "تعذر إخفاء التعميم") }
        }
    }
    fun publishSchoolAnnouncement(
        title: String,
        content: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.addEvent(
                groupId = "all",
                eventDate = getTodayDateString(),
                title = title.trim(),
                timeStr = null,
                category = "ANNOUNCEMENT",
                description = content.trim(),
                location = null
            )
            result.onSuccess {
                val groupId = getActiveGroupId()
                if (groupId.isNotBlank()) repository.syncEvents(groupId)
                onResult(true, "تم نشر التعميم رسمياً، وسيظهر في الصفحة الرئيسية والفعاليات لجميع أعضاء المدرسة.")
            }.onFailure { error ->
                onResult(false, error.message ?: "تعذر نشر التعميم المدرسي")
            }
        }
    }

    suspend fun sendChatMessageSuspend(
        text: String,
        targetGroupId: String? = null
    ): Boolean {
        val user = currentUser.value ?: return false
        val groupId = targetGroupId ?: getActiveGroupId()
        val cleanText = text.trim()
        if (cleanText.isBlank() || groupId.isBlank()) return false
        return try {
            val gradeName = user.gradeId?.let { SchoolHierarchy.getGradeName(it) } ?: "الإدارة المدرسية"
            val secAr = user.sectionId?.let { SchoolHierarchy.getSectionArabicName(it) } ?: ""
            repository.sendChatMessage(
                groupId = groupId,
                senderId = user.id,
                senderName = user.fullName,
                senderGradeSection = "$gradeName — $secAr",
                text = cleanText
            )
        } catch (e: Exception) {
            false
        }
    }

    fun sendChatMessage(
        text: String,
        targetGroupId: String? = null,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val success = sendChatMessageSuspend(text, targetGroupId)
            onComplete?.invoke(success)
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

        fun isWeekend(calendar: Calendar = Calendar.getInstance()): Boolean {
            val d = calendar.get(Calendar.DAY_OF_WEEK)
            return d == Calendar.FRIDAY || d == Calendar.SATURDAY
        }

        fun getDayOfWeekIndex(calendar: Calendar = Calendar.getInstance()): Int {
            return when (calendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SUNDAY -> 0
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> -1
                Calendar.SATURDAY -> -1
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
