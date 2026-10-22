package com.magd.tanweer.util

import android.app.DownloadManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.magd.tanweer.BuildConfig
import com.magd.tanweer.MainActivity
import com.magd.tanweer.R
import com.magd.tanweer.data.notifications.TanweerNotificationManager
import com.magd.tanweer.data.remote.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

enum class VersionSource(val label: String, val icon: String) {
    SERVER_CHANNEL("قناة التحديث المعتمدة لمنصة تنوير", "🛡️"),
    GITHUB_RELEASE("إصدار رسمي من GitHub", "🐙")
}

data class AppVersionInfo(
    val currentVersionName: String,
    val currentVersionCode: Int,
    val installedVersionName: String = BuildConfig.VERSION_NAME,
    val installedVersionCode: Int = BuildConfig.VERSION_CODE,
    val releaseDate: String?,
    val changelog: List<String>,
    val webpageUrl: String,
    val directApkUrl: String?,
    val directApkSizeMb: Double?,
    val sha256: String? = null,
    val isUpdateAvailable: Boolean,
    val isMandatory: Boolean,
    val source: VersionSource
)

sealed class ApkVerificationResult {
    data class Valid(
        val file: File,
        val packageName: String,
        val versionCode: Int,
        val versionName: String,
        val calculatedSha256: String
    ) : ApkVerificationResult()

    data class Invalid(val reason: String) : ApkVerificationResult()
}

object AppVersionManager {

    private const val TAG = "AppVersionManager"
    private const val PREFS_NAME = "tanweer_prefs"
    private const val KEY_LAST_UPDATE_CHECK_TIME = "last_app_update_check_time"

    private val rawHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Resolves latest app version by checking the authoritative Server Version API first,
     * supplemented by fetching Change Log/X.Y.Z.md from repo if available.
     */
    suspend fun checkVersion(): Result<AppVersionInfo> = withContext(Dispatchers.IO) {
        val installedCode = BuildConfig.VERSION_CODE
        val installedName = BuildConfig.VERSION_NAME

        var serverVersionInfo: AppVersionInfo? = null
        var gitHubVersionInfo: AppVersionInfo? = null

        // 1. Query Platform Server Update Channel API
        try {
            val srvRes = NetworkModule.apiService.getSystemVersion()
            if (srvRes.isSuccessful && srvRes.body() != null) {
                val body = srvRes.body()!!
                val isUpdateAvailable = body.versionCode > installedCode
                val isMandatory = body.isUpdateRequired || (installedCode < body.minSupportedVersionCode)
                val downloadUrl = body.downloadUrl ?: "https://github.com/majd7772233-a11y/Tanweer/releases/latest"
                val isDirectApk = downloadUrl.endsWith(".apk", ignoreCase = true)
                val detailedChangelog = fetchChangeLogMarkdown(body.currentVersion).ifEmpty { body.changelog }

                serverVersionInfo = AppVersionInfo(
                    currentVersionName = body.currentVersion,
                    currentVersionCode = body.versionCode,
                    installedVersionName = installedName,
                    installedVersionCode = installedCode,
                    releaseDate = body.releaseDate,
                    changelog = detailedChangelog,
                    webpageUrl = if (!isDirectApk) downloadUrl else "https://github.com/majd7772233-a11y/Tanweer/releases/latest",
                    directApkUrl = if (isDirectApk) downloadUrl else null,
                    directApkSizeMb = null,
                    sha256 = body.sha256,
                    isUpdateAvailable = isUpdateAvailable,
                    isMandatory = isMandatory,
                    source = VersionSource.SERVER_CHANNEL
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Server version API query note: ${e.message}")
        }

        // 2. Query GitHub Releases API
        try {
            val ghRes = NetworkModule.gitHubApiService.getAppLatestRelease()
            if (ghRes.isSuccessful && ghRes.body() != null) {
                val rel = ghRes.body()!!
                val tag = rel.tag_name?.trim() ?: "v$installedName"
                val verName = tag.removePrefix("v").removePrefix("V")
                val verCode = parseVersionCodeFromTag(verName, installedCode)

                val apkAsset = rel.assets.find { it.name.endsWith(".apk", ignoreCase = true) }
                val directApkUrl = apkAsset?.browser_download_url
                val apkSizeMb = apkAsset?.size?.let { Math.round((it.toDouble() / (1024 * 1024)) * 10.0) / 10.0 }

                val rawChangelog = fetchChangeLogMarkdown(verName).ifEmpty {
                    rel.body?.split("\n")
                        ?.map { it.trim().removePrefix("-").removePrefix("*").trim() }
                        ?.filter { it.isNotBlank() }
                        ?: emptyList()
                }

                gitHubVersionInfo = AppVersionInfo(
                    currentVersionName = verName,
                    currentVersionCode = verCode,
                    installedVersionName = installedName,
                    installedVersionCode = installedCode,
                    releaseDate = rel.published_at?.take(10) ?: rel.created_at?.take(10),
                    changelog = rawChangelog,
                    webpageUrl = rel.html_url ?: "https://github.com/majd7772233-a11y/Tanweer/releases/latest",
                    directApkUrl = directApkUrl,
                    directApkSizeMb = apkSizeMb,
                    sha256 = null,
                    isUpdateAvailable = verCode > installedCode,
                    isMandatory = false,
                    source = VersionSource.GITHUB_RELEASE
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "GitHub release query note: ${e.message}")
        }

        // 3. Resolve unified source of truth:
        // Prioritize whichever source offers a newer version code or direct APK download asset
        val resolved = when {
            gitHubVersionInfo != null && (serverVersionInfo == null || gitHubVersionInfo.currentVersionCode > serverVersionInfo.currentVersionCode) -> {
                gitHubVersionInfo
            }
            serverVersionInfo != null -> {
                if (serverVersionInfo.directApkUrl == null && gitHubVersionInfo?.directApkUrl != null &&
                    gitHubVersionInfo.currentVersionCode >= serverVersionInfo.currentVersionCode) {
                    serverVersionInfo.copy(
                        directApkUrl = gitHubVersionInfo.directApkUrl,
                        directApkSizeMb = gitHubVersionInfo.directApkSizeMb
                    )
                } else {
                    serverVersionInfo
                }
            }
            gitHubVersionInfo != null -> gitHubVersionInfo
            else -> null
        }

        if (resolved != null) {
            Result.success(resolved)
        } else {
            Result.failure(Exception("تعذر الاتصال بقنوات التحديث الرسمية للتطبيق"))
        }
    }

    /**
     * Reads Change Log/X.Y.Z.md from repository or returns empty list if not found.
     */
    suspend fun fetchChangeLogMarkdown(versionName: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val localLog = File("Change Log/$versionName.md")
            if (localLog.exists()) {
                val lines = localLog.readLines()
                    .map { it.trim() }
                    .filter { it.startsWith("-") || it.startsWith("*") || it.startsWith("•") }
                    .map { it.removePrefix("-").removePrefix("*").removePrefix("•").trim() }
                    .filter { it.isNotBlank() }
                if (lines.isNotEmpty()) return@withContext lines
            }
        } catch (_: Exception) {}

        val candidateUrls = listOf(
            "https://raw.githubusercontent.com/majd7772233-a11y/tanweer/main/Change%20Log/$versionName.md",
            "https://raw.githubusercontent.com/majd7772233-a11y/tanweer/main/Change%20Log/v$versionName.md",
            "https://raw.githubusercontent.com/majd7772233-a11y/tanweer/main/CHANGELOG.md"
        )

        for (url in candidateUrls) {
            try {
                val req = Request.Builder().url(url).build()
                rawHttpClient.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val lines = body.split("\n")
                                .map { it.trim() }
                                .filter { it.startsWith("-") || it.startsWith("*") || it.startsWith("•") }
                                .map { it.removePrefix("-").removePrefix("*").removePrefix("•").trim() }
                                .filter { it.isNotBlank() }
                            if (lines.isNotEmpty()) {
                                return@withContext lines
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        emptyList()
    }

    private fun parseVersionCodeFromTag(versionName: String, fallback: Int): Int {
        val parts = versionName.split(".").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 100 + parts[1] * 10 + parts[2]
            2 -> parts[0] * 100 + parts[1]
            1 -> parts[0]
            else -> fallback
        }
    }

    /**
     * Downloads APK file using Android's native DownloadManager into app cache or external downloads.
     */
    fun startNativeApkDownload(context: Context, apkUrl: String, versionName: String): Long {
        val uri = Uri.parse(apkUrl)
        val request = DownloadManager.Request(uri).apply {
            setTitle("تحديث تنوير $versionName")
            setDescription("جاري تنزيل ملف التثبيت المعتمد...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "tanweer-$versionName.apk")
            setMimeType("application/vnd.android.package-archive")
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = dm.enqueue(request)
        Toast.makeText(context, "بدأ تنزيل التحديث في شريط الإشعارات 📥", Toast.LENGTH_LONG).show()
        return id
    }

    /**
     * Calculates SHA-256 hash of a local file.
     */
    fun calculateFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val hashBytes = digest.digest()
        val sb = StringBuilder()
        for (b in hashBytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    /**
     * Verifies downloaded APK integrity, package identity, and optional SHA-256 before installation.
     */
    fun verifyDownloadedApk(context: Context, apkFile: File, expectedSha256: String? = null): ApkVerificationResult {
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            return ApkVerificationResult.Invalid("ملف التثبيت غير موجود أو غير مكتمل التنزيل")
        }

        val calculatedSha = try {
            calculateFileSha256(apkFile)
        } catch (e: Exception) {
            return ApkVerificationResult.Invalid("تعذر التحقق من البصمة الرقمية للملف: ${e.message}")
        }

        if (!expectedSha256.isNullOrBlank()) {
            if (!calculatedSha.equals(expectedSha256.trim(), ignoreCase = true)) {
                return ApkVerificationResult.Invalid("البصمة الرقمية SHA-256 لا تطابق الإصدار الرسمي المعتمد")
            }
        }

        val pm = context.packageManager
        val pkgInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            }
        } catch (e: Exception) {
            null
        }

        if (pkgInfo == null) {
            return ApkVerificationResult.Invalid("حزمة APK تالفة أو غير متوافقة مع نظام أندرويد")
        }

        if (pkgInfo.packageName != context.packageName) {
            return ApkVerificationResult.Invalid("معرف الحزمة (${pkgInfo.packageName}) لا يطابق تطبيق تنوير الرسمي")
        }

        @Suppress("DEPRECATION")
        val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.longVersionCode.toInt()
        } else {
            pkgInfo.versionCode
        }

        return ApkVerificationResult.Valid(
            file = apkFile,
            packageName = pkgInfo.packageName,
            versionCode = archiveVersionCode,
            versionName = pkgInfo.versionName ?: "1.0",
            calculatedSha256 = calculatedSha
        )
    }

    /**
     * Launches the native Android Package Installer for an APK file.
     * Android requires explicit user confirmation through the system Package Installer dialog.
     */
    fun launchPackageInstaller(context: Context, apkFile: File): Boolean {
        return try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(installIntent)
            Toast.makeText(context, "يرجى تأكيد التثبيت عبر مثبت الحزم في أندرويد 📦", Toast.LENGTH_LONG).show()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed launching package installer", e)
            Toast.makeText(context, "تعذر فتح مثبت الحزم: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            false
        }
    }

    /**
     * Periodic background check for app updates (runs once every 24 hours during daily reminder cycle).
     */
    suspend fun checkPeriodicUpdate(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_UPDATE_CHECK_TIME, 0L)
        val now = System.currentTimeMillis()

        // Check once daily (24 hours cooldown)
        if (now - lastCheck < 24 * 60 * 60 * 1000L && lastCheck > 0L) {
            return@withContext
        }

        val result = checkVersion()
        prefs.edit().putLong(KEY_LAST_UPDATE_CHECK_TIME, now).apply()

        result.onSuccess { info ->
            if (info.isUpdateAvailable) {
                TanweerNotificationManager.initChannel(context)
                val notifyIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra(TanweerNotificationManager.EXTRA_NAVIGATE_TO, "VERSION_CHECK")
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    1099,
                    notifyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, TanweerNotificationManager.CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("🚀 يتوفر إصدار جديد من تنوير (v${info.currentVersionName})")
                    .setContentText("إصدار رسمي جديد يتضمن تحسينات ومزايا جديدة. اضغط للاطلاع والتحديث.")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build()

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.notify(2099, notification)
            }
        }
    }
}
