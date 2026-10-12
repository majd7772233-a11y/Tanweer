package com.magd.tanweer.util

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.magd.tanweer.BuildConfig
import com.magd.tanweer.data.model.GitHubRelease
import com.magd.tanweer.data.remote.NetworkModule
import com.magd.tanweer.data.remote.SystemVersionResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class VersionSource(val label: String, val icon: String) {
    GITHUB_RELEASE("إصدار رسمي من GitHub", "🐙"),
    SERVER_CHANNEL("قناة التحديثات الرسمية لتنوير", "🛡️")
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
    val isUpdateAvailable: Boolean,
    val isMandatory: Boolean,
    val source: VersionSource
)

object AppVersionManager {

    /**
     * Resolves latest app version by checking GitHub Releases first, then falling back to Server Version API.
     */
    suspend fun checkVersion(): Result<AppVersionInfo> = withContext(Dispatchers.IO) {
        val installedCode = BuildConfig.VERSION_CODE
        val installedName = BuildConfig.VERSION_NAME

        // 1. Try querying GitHub Releases
        try {
            val ghRes = NetworkModule.gitHubApiService.getAppLatestRelease()
            if (ghRes.isSuccessful && ghRes.body() != null) {
                val rel = ghRes.body()!!
                val tag = rel.tag_name?.trim() ?: "v$installedName"
                val verName = tag.removePrefix("v").removePrefix("V")
                val verCode = parseVersionCodeFromTag(verName, installedCode)

                // Search for APK asset
                val apkAsset = rel.assets.find { it.name.endsWith(".apk", ignoreCase = true) }
                val directApkUrl = apkAsset?.browser_download_url
                val apkSizeMb = apkAsset?.size?.let { Math.round((it.toDouble() / (1024 * 1024)) * 10.0) / 10.0 }

                val changelogLines = rel.body?.split("\n")
                    ?.map { it.trim().removePrefix("-").removePrefix("*").trim() }
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()

                return@withContext Result.success(
                    AppVersionInfo(
                        currentVersionName = verName,
                        currentVersionCode = verCode,
                        installedVersionName = installedName,
                        installedVersionCode = installedCode,
                        releaseDate = rel.published_at?.take(10) ?: rel.created_at?.take(10),
                        changelog = changelogLines,
                        webpageUrl = rel.html_url ?: "https://github.com/majd7772233-a11y/tanweer/releases/latest",
                        directApkUrl = directApkUrl,
                        directApkSizeMb = apkSizeMb,
                        isUpdateAvailable = verCode > installedCode,
                        isMandatory = false,
                        source = VersionSource.GITHUB_RELEASE
                    )
                )
            }
        } catch (_: Exception) {
            // GitHub release query failed or repo has no release yet; fallback to server API
        }

        // 2. Query official Server Update Channel API
        try {
            val srvRes = NetworkModule.apiService.getSystemVersion()
            if (srvRes.isSuccessful && srvRes.body() != null) {
                val body = srvRes.body()!!
                val isUpdateAvailable = body.versionCode > installedCode
                val isMandatory = body.isUpdateRequired || (installedCode < body.minSupportedVersionCode)

                val downloadUrl = body.downloadUrl ?: "https://github.com/majd7772233-a11y/tanweer/releases/latest"
                val isDirectApk = downloadUrl.endsWith(".apk", ignoreCase = true)

                return@withContext Result.success(
                    AppVersionInfo(
                        currentVersionName = body.currentVersion,
                        currentVersionCode = body.versionCode,
                        installedVersionName = installedName,
                        installedVersionCode = installedCode,
                        releaseDate = body.releaseDate,
                        changelog = body.changelog,
                        webpageUrl = if (!isDirectApk) downloadUrl else "https://github.com/majd7772233-a11y/tanweer/releases/latest",
                        directApkUrl = if (isDirectApk) downloadUrl else null,
                        directApkSizeMb = null,
                        isUpdateAvailable = isUpdateAvailable,
                        isMandatory = isMandatory,
                        source = VersionSource.SERVER_CHANNEL
                    )
                )
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        Result.failure(Exception("تعذر الاتصال بقنوات التحديث الرسمية"))
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
     * Downloads APK file using Android's native DownloadManager.
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
}
