package com.example.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long,
    val isUpdateAvailable: Boolean,
    val isForceUpdate: Boolean = false,
    val forceUpdateMessage: String = "",
    val publishedAt: String = "",
    val token: String? = null
)

sealed interface AppUpdateStatus {
    object Idle : AppUpdateStatus
    object Checking : AppUpdateStatus
    data class Available(val updateInfo: AppUpdateInfo) : AppUpdateStatus
    data class Downloading(
        val progressPercent: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : AppUpdateStatus
    data class ReadyToInstall(val apkFile: File, val updateInfo: AppUpdateInfo) : AppUpdateStatus
    data class Error(val message: String) : AppUpdateStatus
}

class GitHubUpdateService private constructor() {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    companion object {
        private const val TAG = "GitHubUpdateService"
        const val DEFAULT_REPO_OWNER = "Zero7AI"
        const val DEFAULT_REPO_NAME = "filtr-cafe-software"

        @Volatile
        private var instance: GitHubUpdateService? = null

        @Volatile
        private var lastResolvedToken: String? = null

        fun getInstance(): GitHubUpdateService {
            return instance ?: synchronized(this) {
                instance ?: GitHubUpdateService().also { instance = it }
            }
        }
    }

    /**
     * Checks the latest GitHub release for the specified repository and compares with current version.
     * Supports dynamic remote override via Supabase cloud so repository can be shifted remotely without touching phones.
     */
    suspend fun checkForUpdate(
        owner: String? = null,
        repo: String? = null,
        currentVersionName: String,
        currentVersionCode: Int = 0,
        token: String? = null
    ): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            var cleanOwner = owner?.trim().orEmpty().ifBlank { DEFAULT_REPO_OWNER }
            var cleanRepo = repo?.trim().orEmpty().ifBlank { DEFAULT_REPO_NAME }
            var cleanToken = token?.trim().orEmpty()

            // Check remote config from Supabase first (allows shifting repo remotely from your laptop)
            try {
                val remoteConfig = SupabaseService.getInstance().fetchUpdateRepoConfig()
                if (!remoteConfig.isNullOrBlank()) {
                    if (remoteConfig.startsWith("{")) {
                        val json = JSONObject(remoteConfig)
                        cleanOwner = json.optString("owner", cleanOwner)
                        cleanRepo = json.optString("repo", cleanRepo)
                        cleanToken = json.optString("token", cleanToken)
                    } else if (remoteConfig.contains("/")) {
                        val parts = remoteConfig.split("/")
                        if (parts.size >= 2) {
                            cleanOwner = parts[0].trim()
                            cleanRepo = parts[1].trim()
                        }
                    }
                    Log.d(TAG, "Using remote repository configuration: $cleanOwner/$cleanRepo")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Remote config fetch fallback: ${e.message}")
            }

            lastResolvedToken = cleanToken.ifBlank { null }

            // Check remote policy from Supabase early
            val policy = try { SupabaseService.getInstance().fetchAppUpdatePolicy() } catch (e: Exception) { null }
            val isMandatoryByCode = policy != null && policy.minVersionCode > 0 && currentVersionCode < policy.minVersionCode
            val isMandatoryByName = policy != null && policy.minVersionName.isNotBlank() && isNewerVersion(policy.minVersionName, currentVersionName)
            val isMandatoryByFlag = policy != null && policy.forceUpdate
            val isMandatory = isMandatoryByCode || isMandatoryByName || isMandatoryByFlag

            val url = "https://api.github.com/repos/$cleanOwner/$cleanRepo/releases/latest"

            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "FILTR-Billing-App")
                .header("Accept", "application/vnd.github.v3+json")

            if (cleanToken.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $cleanToken")
            }

            val response = try {
                client.newCall(requestBuilder.build()).execute()
            } catch (e: Exception) {
                if (isMandatory && policy != null) {
                    Log.i(TAG, "GitHub API call failed (${e.message}), but Supabase policy requires mandatory update: serving policy info")
                    val fallbackDownloadUrl = policy.apkDownloadUrl.ifBlank {
                        "https://github.com/$cleanOwner/$cleanRepo/releases/download/v${policy.minVersionName}/filtr-billing-v${policy.minVersionName}.apk"
                    }
                    return@withContext Result.success(
                        AppUpdateInfo(
                            versionName = policy.minVersionName,
                            releaseTitle = policy.title.ifBlank { "Mandatory Update Required ⚠️" },
                            releaseNotes = policy.message,
                            apkDownloadUrl = fallbackDownloadUrl,
                            apkFileName = policy.apkFileName.ifBlank { "filtr-billing-v${policy.minVersionName}.apk" },
                            apkSizeBytes = policy.apkSizeBytes,
                            isUpdateAvailable = true,
                            isForceUpdate = true,
                            forceUpdateMessage = policy.message,
                            token = null
                        )
                    )
                }
                throw e
            }

            if (!response.isSuccessful) {
                val code = response.code
                val errBody = response.body?.string().orEmpty()
                Log.w(TAG, "GitHub API returned HTTP $code: $errBody")
                if (isMandatory && policy != null) {
                    Log.i(TAG, "GitHub API HTTP $code, using Supabase policy for mandatory update")
                    val fallbackDownloadUrl = policy.apkDownloadUrl.ifBlank {
                        "https://github.com/$cleanOwner/$cleanRepo/releases/download/v${policy.minVersionName}/filtr-billing-v${policy.minVersionName}.apk"
                    }
                    return@withContext Result.success(
                        AppUpdateInfo(
                            versionName = policy.minVersionName,
                            releaseTitle = policy.title.ifBlank { "Mandatory Update Required ⚠️" },
                            releaseNotes = policy.message,
                            apkDownloadUrl = fallbackDownloadUrl,
                            apkFileName = policy.apkFileName.ifBlank { "filtr-billing-v${policy.minVersionName}.apk" },
                            apkSizeBytes = policy.apkSizeBytes,
                            isUpdateAvailable = true,
                            isForceUpdate = true,
                            forceUpdateMessage = policy.message,
                            token = null
                        )
                    )
                }
                return@withContext Result.failure(
                    Exception(
                        when (code) {
                            404 -> "No releases found on GitHub repository ($cleanOwner/$cleanRepo)."
                            401 -> "GitHub authentication failed. Please verify your token."
                            403 -> "GitHub API rate limit exceeded or access denied."
                            else -> "GitHub API error (HTTP $code)."
                        }
                    )
                )
            }

            val bodyStr = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response from GitHub"))
            val releaseJson = JSONObject(bodyStr)

            val tagName = releaseJson.optString("tag_name", "").trim()
            val releaseTitle = releaseJson.optString("name", tagName).ifBlank { tagName }
            val releaseNotes = releaseJson.optString("body", "No release notes provided.").trim()
            val publishedAt = releaseJson.optString("published_at", "")

            // Find APK asset in assets array
            val assetsArray = releaseJson.optJSONArray("assets")
            var apkDownloadUrl = ""
            var apkFileName = ""
            var apkSizeBytes = 0L

            if (assetsArray != null) {
                for (i in 0 until assetsArray.length()) {
                    val asset = assetsArray.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkFileName = name
                        apkSizeBytes = asset.optLong("size", 0L)
                        // If private token is used, use API asset URL so Authorization header can stream the raw binary
                        apkDownloadUrl = if (cleanToken.isNotBlank()) {
                            asset.optString("url", asset.optString("browser_download_url", ""))
                        } else {
                            asset.optString("browser_download_url", "")
                        }
                        break
                    }
                }
            }

            if (apkDownloadUrl.isBlank()) {
                return@withContext Result.failure(
                    Exception("Release $tagName exists on GitHub, but no .apk asset was uploaded to it.")
                )
            }

            val isNewer = isNewerVersion(tagName, currentVersionName)
            val cleanVersionName = tagName.removePrefix("v").removePrefix("V")

            val isForce = isMandatoryByCode || isMandatoryByName || (isMandatoryByFlag && isNewer)
            val isAvailable = isNewer || isMandatoryByCode || isMandatoryByName

            val updateInfo = AppUpdateInfo(
                versionName = cleanVersionName,
                releaseTitle = releaseTitle,
                releaseNotes = releaseNotes,
                apkDownloadUrl = apkDownloadUrl,
                apkFileName = apkFileName,
                apkSizeBytes = apkSizeBytes,
                isUpdateAvailable = isAvailable,
                isForceUpdate = isForce,
                forceUpdateMessage = policy?.message ?: "",
                publishedAt = publishedAt,
                token = cleanToken.ifBlank { null }
            )

            Log.d(TAG, "Check complete: local=$currentVersionName (code=$currentVersionCode), remote=$cleanVersionName, isNewer=$isNewer, isForce=$isForce")
            Result.success(updateInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for update: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Downloads the APK file in a chunked stream, notifying progress.
     */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        token: String?,
        onProgress: (progressPercent: Int, bytesRead: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) updatesDir.mkdirs()

            val outputFile = File(updatesDir, "filtr_update.apk")
            if (outputFile.exists()) outputFile.delete()

            val requestBuilder = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "FILTR-Billing-App")

            val cleanToken = token?.trim().takeIf { !it.isNullOrBlank() }
                ?: lastResolvedToken.orEmpty()
            val isApiUrl = downloadUrl.contains("api.github.com/repos/")
            if (cleanToken.isNotBlank() && isApiUrl) {
                requestBuilder.header("Authorization", "Bearer $cleanToken")
                // Required by GitHub API when requesting /releases/assets/{id}
                requestBuilder.header("Accept", "application/octet-stream")
            }

            var response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful && cleanToken.isNotBlank() && isApiUrl) {
                response.close()
                val browserFallbackUrl = "https://github.com/${DEFAULT_REPO_OWNER}/${DEFAULT_REPO_NAME}/releases/download/v1.3/filtr-billing-v1.3.apk"
                Log.w(TAG, "API download failed, falling back to direct CDN: $browserFallbackUrl")
                val fallbackReq = Request.Builder()
                    .url(browserFallbackUrl)
                    .header("User-Agent", "FILTR-Billing-App")
                    .build()
                response = client.newCall(fallbackReq).execute()
            }
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to download APK (HTTP ${response.code})"))
            }

            val responseBody = response.body ?: return@withContext Result.failure(Exception("Empty response body"))
            val totalBytes = responseBody.contentLength()

            val inputStream = responseBody.byteStream()
            val outputStream = FileOutputStream(outputFile)

            val buffer = ByteArray(8 * 1024)
            var bytesReadTotal = 0L
            var read: Int
            var lastReportedPercent = -1

            outputStream.use { out ->
                inputStream.use { input ->
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        bytesReadTotal += read
                        val percent = if (totalBytes > 0) {
                            ((bytesReadTotal * 100) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }
                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            onProgress(percent, bytesReadTotal, totalBytes)
                        }
                    }
                    out.flush()
                }
            }

            onProgress(100, bytesReadTotal, if (totalBytes > 0) totalBytes else bytesReadTotal)
            Log.d(TAG, "Download finished successfully: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading APK: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Triggers the Android system package installer for the downloaded APK.
     */
    fun installApk(context: Context, apkFile: File): Result<Unit> {
        return try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                return Result.failure(Exception("APK file not found or empty"))
            }

            // Check if app has permission to install packages on Android 8.0+ (API 26+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                    return Result.failure(
                        Exception("Please allow 'Install unknown apps' permission for FILTR Coffee, then tap Install again.")
                    )
                }
            }

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
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start installer: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Compares two semantic version strings (e.g. "1.1.0" > "1.0.0" or "v2.0" > "1.9").
     * Returns true if remoteVersion is strictly newer than currentVersion.
     */
    fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
        try {
            val remoteClean = remoteTag.trim().removePrefix("v").removePrefix("V")
            val currentClean = currentVersion.trim().removePrefix("v").removePrefix("V")

            if (remoteClean == currentClean) return false

            val remoteParts = remoteClean.split(".").mapNotNull { it.takeWhile { char -> char.isDigit() }.toIntOrNull() }
            val currentParts = currentClean.split(".").mapNotNull { it.takeWhile { char -> char.isDigit() }.toIntOrNull() }

            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }

            return false
        } catch (e: Exception) {
            Log.w(TAG, "Version comparison fallback for remote=$remoteTag, current=$currentVersion: ${e.message}")
            return remoteTag.trim() != currentVersion.trim()
        }
    }
}
