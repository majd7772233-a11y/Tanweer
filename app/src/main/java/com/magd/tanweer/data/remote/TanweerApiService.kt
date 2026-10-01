package com.magd.tanweer.data.remote

import com.magd.tanweer.data.model.*
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val phoneNumber: String,
    val password: String,
    val fullName: String,
    val gradeId: Int,
    val sectionId: String,
    val email: String? = null,
    val deviceId: String? = null
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val phoneNumber: String,
    val password: String,
    val deviceId: String? = null,
    val appVersion: String? = "1.0"
)

@JsonClass(generateAdapter = true)
data class RecoveryLoginRequest(
    val recoveryCode: String,
    val deviceId: String? = null
)

@JsonClass(generateAdapter = true)
data class UpdateProfileRequest(
    val fullName: String? = null,
    val gradeId: Int? = null,
    val sectionId: String? = null
)

@JsonClass(generateAdapter = true)
data class GroupsResponse(
    val success: Boolean,
    val myGroups: List<GroupItem> = emptyList(),
    val discoverGroups: List<GroupItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ScheduleResponse(
    val success: Boolean,
    val slots: List<ScheduleSlot> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CreateScheduleSlotRequest(
    val dayOfWeek: Int,
    val slotOrder: Int,
    val subjectId: String,
    val subjectName: String,
    val subjectIcon: String,
    val colorHex: String? = null,
    val startTime: String? = null,
    val endTime: String? = null
)

@JsonClass(generateAdapter = true)
data class ScheduleProposalRequest(
    val groupId: String,
    val dayOfWeek: Int,
    val slotOrder: Int,
    val oldSubjectId: String?,
    val newSubjectId: String,
    val reason: String
)

@JsonClass(generateAdapter = true)
data class CalendarOverviewResponse(
    val success: Boolean,
    val contentsByDate: List<DateCountItem> = emptyList(),
    val exams: List<ExamItem> = emptyList(),
    val events: List<SchoolEventItem> = emptyList(),
    val homeworks: List<HomeworkItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class DateCountItem(
    val date: String,
    val count: Int
)

@JsonClass(generateAdapter = true)
data class DayDetailResponse(
    val success: Boolean,
    val date: String,
    val contents: List<ContentItem> = emptyList(),
    val homeworks: List<HomeworkItem> = emptyList(),
    val exams: List<ExamItem> = emptyList(),
    val events: List<SchoolEventItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CreateContentRequest(
    val groupId: String,
    val studyDate: String,
    val subjectId: String,
    val type: String = "LESSON",
    val title: String,
    val description: String? = null,
    val media: List<MediaItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CreateHomeworkRequest(
    val groupId: String,
    val studyDate: String,
    val dueDate: String,
    val subjectId: String,
    val title: String,
    val details: String? = null,
    val pageNumbers: String? = null,
    val questionNumbers: String? = null,
    val taskType: String = "HOMEWORK",
    val mediaUrls: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CreateExamRequest(
    val groupId: String,
    val examDate: String,
    val subjectId: String,
    val title: String,
    val requiredChapters: String? = null,
    val notes: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateEventRequest(
    val groupId: String,
    val eventDate: String,
    val timeStr: String? = null,
    val title: String,
    val description: String? = null,
    val category: String = "ACTIVITY",
    val location: String? = null
)

@JsonClass(generateAdapter = true)
data class HomeworksResponse(
    val success: Boolean,
    val homeworks: List<HomeworkItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ExamsResponse(
    val success: Boolean,
    val exams: List<ExamItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class EventsResponse(
    val success: Boolean,
    val events: List<SchoolEventItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class IssuesResponse(
    val success: Boolean,
    val issues: List<IssueItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class IssueDetailResponse(
    val success: Boolean,
    val issue: IssueItem,
    val comments: List<IssueCommentItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CreateIssueRequest(
    val groupId: String,
    val subjectId: String? = null,
    val homeworkId: String? = null,
    val examId: String? = null,
    val title: String,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class AddCommentRequest(
    val comment: String
)

@JsonClass(generateAdapter = true)
data class PostMessageRequest(
    val id: String? = null,
    val text: String
)

@JsonClass(generateAdapter = true)
data class ChatMessagesResponse(
    val success: Boolean,
    val messages: List<ChatMessageItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class BooksResponse(
    val success: Boolean,
    val books: List<BookItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GenericResponse(
    val success: Boolean,
    val message: String? = null,
    val isCompleted: Boolean? = null,
    val error: ApiError? = null
)

@JsonClass(generateAdapter = true)
data class UploadMediaResponse(
    val success: Boolean,
    val id: String? = null,
    val url: String? = null,
    val relativeUrl: String? = null,
    val fileSize: Int? = null,
    val mimeType: String? = null,
    val checksum: String? = null,
    val message: String? = null,
    val error: ApiError? = null
)

@JsonClass(generateAdapter = true)
data class ProfileResponse(
    val success: Boolean,
    val user: User? = null
)

interface TanweerApiService {

    @POST("api/v1/auth/register")
    suspend fun register(@Body req: RegisterRequest): Response<AuthResponse>

    @POST("api/v1/auth/login")
    suspend fun login(@Body req: LoginRequest): Response<AuthResponse>

    @POST("api/v1/auth/recovery-login")
    suspend fun loginWithRecoveryCode(@Body req: RecoveryLoginRequest): Response<AuthResponse>

    @GET("api/v1/me")
    suspend fun getProfile(): Response<ProfileResponse>

    @POST("api/v1/profile")
    suspend fun updateProfile(@Body req: UpdateProfileRequest): Response<ProfileResponse>

    @GET("api/v1/groups")
    suspend fun getGroups(): Response<GroupsResponse>

    @POST("api/v1/groups/{id}/join-request")
    suspend fun joinGroupRequest(@Path("id") groupId: String): Response<GenericResponse>

    @GET("api/v1/groups/{id}/messages")
    suspend fun getGroupMessages(@Path("id") groupId: String): Response<ChatMessagesResponse>

    @POST("api/v1/groups/{id}/messages")
    suspend fun postGroupMessage(
        @Path("id") groupId: String,
        @Body req: PostMessageRequest
    ): Response<GenericResponse>

    @GET("api/v1/schedule/{groupId}")
    suspend fun getSchedule(@Path("groupId") groupId: String): Response<ScheduleResponse>

    @POST("api/v1/schedule/{groupId}/slots")
    suspend fun createScheduleSlot(
        @Path("groupId") groupId: String,
        @Body req: CreateScheduleSlotRequest
    ): Response<GenericResponse>

    @DELETE("api/v1/schedule/{groupId}/slots/{dayOfWeek}/{slotOrder}")
    suspend fun deleteScheduleSlot(
        @Path("groupId") groupId: String,
        @Path("dayOfWeek") dayOfWeek: Int,
        @Path("slotOrder") slotOrder: Int
    ): Response<GenericResponse>

    @POST("api/v1/schedule/proposals")
    suspend fun proposeScheduleChange(@Body req: ScheduleProposalRequest): Response<GenericResponse>

    @GET("api/v1/calendar")
    suspend fun getCalendar(
        @Query("groupId") groupId: String,
        @Query("month") month: String
    ): Response<CalendarOverviewResponse>

    @GET("api/v1/day/{date}")
    suspend fun getDayDetail(
        @Path("date") date: String,
        @Query("groupId") groupId: String
    ): Response<DayDetailResponse>

    @Multipart
    @POST("api/v1/media/upload")
    suspend fun uploadMedia(
        @Part file: MultipartBody.Part
    ): Response<UploadMediaResponse>

    @POST("api/v1/content")
    suspend fun createContent(@Body req: CreateContentRequest): Response<GenericResponse>

    @POST("api/v1/content/{id}/useful")
    suspend fun voteUseful(@Path("id") contentId: String): Response<GenericResponse>

    @GET("api/v1/homeworks")
    suspend fun getHomeworks(@Query("groupId") groupId: String): Response<HomeworksResponse>

    @POST("api/v1/homeworks")
    suspend fun createHomework(@Body req: CreateHomeworkRequest): Response<GenericResponse>

    @POST("api/v1/homeworks/{id}/toggle")
    suspend fun toggleHomework(@Path("id") id: String): Response<GenericResponse>

    @GET("api/v1/exams")
    suspend fun getExams(@Query("groupId") groupId: String): Response<ExamsResponse>

    @POST("api/v1/exams")
    suspend fun createExam(@Body req: CreateExamRequest): Response<GenericResponse>

    @GET("api/v1/events")
    suspend fun getEvents(@Query("groupId") groupId: String): Response<EventsResponse>

    @POST("api/v1/events")
    suspend fun createEvent(@Body req: CreateEventRequest): Response<GenericResponse>

    @GET("api/v1/issues")
    suspend fun getIssues(@Query("groupId") groupId: String): Response<IssuesResponse>

    @GET("api/v1/issues/{id}")
    suspend fun getIssueDetails(@Path("id") id: String): Response<IssueDetailResponse>

    @POST("api/v1/issues")
    suspend fun createIssue(@Body req: CreateIssueRequest): Response<GenericResponse>

    @POST("api/v1/issues/{id}/comments")
    suspend fun addIssueComment(
        @Path("id") issueId: String,
        @Body req: AddCommentRequest
    ): Response<GenericResponse>

    @POST("api/v1/issues/{id}/best-answer/{commentId}")
    suspend fun markBestAnswer(
        @Path("id") issueId: String,
        @Path("commentId") commentId: String
    ): Response<GenericResponse>

    @GET("api/v1/books")
    suspend fun getBooks(@Query("gradeId") gradeId: Int): Response<BooksResponse>
}

// GitHub API interface for fetching official curriculum books directly from GitHub Releases / catalog.json
interface GitHubApiService {
    @GET("repos/majd7772233-a11y/tanweer-books/releases")
    suspend fun getReleases(): Response<List<GitHubRelease>>

    @GET
    suspend fun getCatalog(
        @Url url: String = "https://raw.githubusercontent.com/majd7772233-a11y/tanweer-books/main/catalog.json"
    ): Response<BooksCatalogResponse>
}

object NetworkModule {
    private const val BASE_URL = "https://tanweer.magd.workers.dev/"
    private const val GITHUB_BASE_URL = "https://api.github.com/"
    private var authToken: String? = null

    fun setAuthToken(token: String?) {
        authToken = token
    }

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()
        authToken?.let {
            builder.addHeader("Authorization", "Bearer $it")
        }
        builder.addHeader("X-App-Version", "1.0")
        chain.proceed(builder.build())
    }

    private val githubInterceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()
            .addHeader("User-Agent", "Tanweer-Android-App")
            .addHeader("Accept", "application/vnd.github.v3+json")
        chain.proceed(builder.build())
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val apiService: TanweerApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TanweerApiService::class.java)
    }

    val gitHubApiService: GitHubApiService by lazy {
        val githubClient = OkHttpClient.Builder()
            .addInterceptor(githubInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(GITHUB_BASE_URL)
            .client(githubClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GitHubApiService::class.java)
    }
}
