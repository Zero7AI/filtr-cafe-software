package com.example.data.remote

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
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
    val token: String? = null,
    val fallbackBrowserUrl: String? = null
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
        .followSslRedirects(false)
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            if (!isAllowedGitHubHttpsUrl(request.url.toString())) {
                throw SecurityException("Blocked non-GitHub or insecure OTA URL: ${request.url.host}")
            }
            chain.proceed(request)
        }
        .build()

    companion object {
        private const val TAG = "GitHubUpdateService"
        const val DEFAULT_REPO_OWNER = "Zero7AI"
        const val DEFAULT_REPO_NAME = "filtr-cafe-software"
        private val SAFE_REPO_SEGMENT_REGEX = Regex("""^[A-Za-z0-9_.-]+$""")

        @Volatile
        private var instance: GitHubUpdateService? = null

        fun getInstance(): GitHubUpdateService {
            return instance ?: synchronized(this) {
                instance ?: GitHubUpdateService().also { instance = it }
            }
        }

        internal fun isAllowedGitHubHttpsUrl(rawUrl: String): Boolean {
            val parsed = try {
                URI(rawUrl.trim())
            } catch (_: Exception) {
                return false
            }
            if (!parsed.scheme.equals("https", ignoreCase = true)) return false
            val host = parsed.host?.lowercase(Locale.US) ?: return false
            return host == "github.com" ||
                host == "api.github.com" ||
                host.endsWith(".githubusercontent.com")
        }
    }

    private fun JSONObject.optNonNullString(key: String, fallback: String = ""): String {
        if (isNull(key)) return fallback
        val value = optString(key, fallback)
        return if (value == "null") fallback else value
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
        token: String? = null,
        isStaffApp: Boolean = false
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
                        cleanOwner = json.optNonNullString("owner", cleanOwner)
                        cleanRepo = json.optNonNullString("repo", cleanRepo)
                        cleanToken = json.optNonNullString("token", cleanToken)
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

            if (!SAFE_REPO_SEGMENT_REGEX.matches(cleanOwner) || !SAFE_REPO_SEGMENT_REGEX.matches(cleanRepo)) {
                return@withContext Result.failure(SecurityException("Invalid GitHub repository configuration"))
            }

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
                    Log.i(TAG, "GitHub API call failed (${e.message}), using Supabase policy for mandatory update")
                    return@withContext Result.success(buildPolicyFallbackUpdate(cleanOwner, cleanRepo, policy, isStaffApp))
                }
                throw e
            }

            response.use { resp ->
                if (!resp.isSuccessful) {
                    val code = resp.code
                    val errBody = resp.body?.string().orEmpty()
                    Log.w(TAG, "GitHub API returned HTTP $code: $errBody")
                    if (isMandatory && policy != null) {
                        Log.i(TAG, "GitHub API HTTP $code, using Supabase policy for mandatory update")
                        return@withContext Result.success(buildPolicyFallbackUpdate(cleanOwner, cleanRepo, policy, isStaffApp))
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

                val bodyStr = resp.body?.string() ?: return@withContext Result.failure(Exception("Empty response from GitHub"))
                val releaseJson = JSONObject(bodyStr)

                val tagName = releaseJson.optNonNullString("tag_name", "").trim()
                val releaseTitle = releaseJson.optNonNullString("name", tagName).ifBlank { tagName }
                val releaseNotes = releaseJson.optNonNullString("body", "No release notes provided.").trim()
                val publishedAt = releaseJson.optNonNullString("published_at", "")

                // Find single unified APK asset (matching "filtr-billing-*.apk" or "filtr-*.apk")
                val assetsArray = releaseJson.optJSONArray("assets")
                var apkDownloadUrl = ""
                var apkFileName = ""
                var apkSizeBytes = 0L
                var fallbackApkUrl = ""
                var fallbackApkName = ""
                var fallbackApkSize = 0L
                var fallbackBrowserUrl = ""

                if (assetsArray != null) {
                    for (i in 0 until assetsArray.length()) {
                        val asset = assetsArray.getJSONObject(i)
                        val name = asset.optNonNullString("name", "")
                        if (!name.endsWith(".apk", ignoreCase = true)) continue

                        val size = asset.optLong("size", 0L)
                        val browserUrl = asset.optNonNullString("browser_download_url", "")
                        val apiAssetUrl = asset.optNonNullString("url", browserUrl)
                        val downloadTarget = if (cleanToken.isNotBlank()) apiAssetUrl else browserUrl

                        if (!isAllowedGitHubHttpsUrl(downloadTarget)) {
                            Log.w(TAG, "Ignoring non-GitHub/insecure APK asset URL for $name")
                            continue
                        }

                        val isBillingAsset = name.contains("filtr-billing", ignoreCase = true)
                        val isFiltrAsset = name.startsWith("filtr-", ignoreCase = true)

                        // Best match: "filtr-billing-*.apk"
                        if (isBillingAsset) {
                            apkFileName = name
                            apkSizeBytes = size
                            apkDownloadUrl = downloadTarget
                            fallbackBrowserUrl = browserUrl.takeIf { isAllowedGitHubHttpsUrl(it) }.orEmpty()
                            break
                        } else if (isFiltrAsset && apkDownloadUrl.isBlank()) {
                            // Good match: "filtr-*.apk"
                            apkFileName = name
                            apkSizeBytes = size
                            apkDownloadUrl = downloadTarget
                            fallbackBrowserUrl = browserUrl.takeIf { isAllowedGitHubHttpsUrl(it) }.orEmpty()
                        } else if (fallbackApkUrl.isBlank()) {
                            fallbackApkName = name
                            fallbackApkSize = size
                            fallbackApkUrl = downloadTarget
                            if (fallbackBrowserUrl.isBlank() && isAllowedGitHubHttpsUrl(browserUrl)) {
                                fallbackBrowserUrl = browserUrl
                            }
                        }
                    }
                }

                if (apkDownloadUrl.isBlank() && fallbackApkUrl.isNotBlank()) {
                    apkDownloadUrl = fallbackApkUrl
                    apkFileName = fallbackApkName
                    apkSizeBytes = fallbackApkSize
                }

                if (apkDownloadUrl.isBlank()) {
                    return@withContext Result.failure(
                        Exception("Release $tagName exists on GitHub, but no matching verified GitHub APK (filtr-billing-*.apk or filtr-*.apk) was uploaded to it.")
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
                    token = cleanToken.ifBlank { null },
                    fallbackBrowserUrl = fallbackBrowserUrl.ifBlank { null }
                )

                Log.d(TAG, "Check complete: local=$currentVersionName (code=$currentVersionCode), remote=$cleanVersionName, isNewer=$isNewer, isForce=$isForce")
                Result.success(updateInfo)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for update: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun buildPolicyFallbackUpdate(
        cleanOwner: String,
        cleanRepo: String,
        policy: SupabaseService.AppUpdatePolicy,
        isStaffApp: Boolean = false
    ): AppUpdateInfo {
        val fileName = "filtr-billing-v${policy.minVersionName}.apk"
        val defaultGitHubUrl = "https://github.com/$cleanOwner/$cleanRepo/releases/download/v${policy.minVersionName}/$fileName"
        val candidateUrl = policy.apkDownloadUrl.trim()
        val safeUrl = if (candidateUrl.isNotBlank() && isAllowedGitHubHttpsUrl(candidateUrl)) {
            candidateUrl
        } else {
            if (candidateUrl.isNotBlank()) {
                Log.w(TAG, "Blocked disallowed policy apk_download_url; using GitHub release URL instead")
            }
            defaultGitHubUrl
        }
        return AppUpdateInfo(
            versionName = policy.minVersionName,
            releaseTitle = policy.title.ifBlank { "Mandatory Update Required ⚠️" },
            releaseNotes = policy.message,
            apkDownloadUrl = safeUrl,
            apkFileName = fileName,
            apkSizeBytes = policy.apkSizeBytes,
            isUpdateAvailable = true,
            isForceUpdate = true,
            forceUpdateMessage = policy.message,
            token = null,
            fallbackBrowserUrl = safeUrl
        )
    }

    /**
     * Downloads the APK file in a chunked stream, notifying progress, and verifies the APK archive.
     */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        token: String?,
        fallbackBrowserUrl: String? = null,
        onProgress: (progressPercent: Int, bytesRead: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!isAllowedGitHubHttpsUrl(downloadUrl)) {
                return@withContext Result.failure(SecurityException("Blocked untrusted or insecure APK download URL"))
            }

            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) updatesDir.mkdirs()

            val outputFile = File(updatesDir, "filtr_update.apk")
            if (outputFile.exists()) outputFile.delete()

            val requestBuilder = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "FILTR-Billing-App")

            val cleanToken = token?.trim().orEmpty()
            val isApiUrl = downloadUrl.contains("api.github.com/repos/")
            if (cleanToken.isNotBlank() && isApiUrl) {
                requestBuilder.header("Authorization", "Bearer $cleanToken")
                // Required by GitHub API when requesting /releases/assets/{id}
                requestBuilder.header("Accept", "application/octet-stream")
            }

            var shouldTryFallback = false
            client.newCall(requestBuilder.build()).execute().use { initialResponse ->
                if (!initialResponse.isSuccessful &&
                    cleanToken.isNotBlank() &&
                    isApiUrl &&
                    !fallbackBrowserUrl.isNullOrBlank() &&
                    isAllowedGitHubHttpsUrl(fallbackBrowserUrl)
                ) {
                    shouldTryFallback = true
                } else {
                    val streamResult = streamApkResponse(initialResponse, outputFile, onProgress)
                    if (streamResult.isFailure) {
                        return@withContext Result.failure(streamResult.exceptionOrNull() ?: Exception("Download failed"))
                    }
                }
            }

            if (shouldTryFallback) {
                Log.w(TAG, "API download failed, falling back to direct GitHub CDN")
                val fallbackReq = Request.Builder()
                    .url(fallbackBrowserUrl!!)
                    .header("User-Agent", "FILTR-Billing-App")
                    .build()
                client.newCall(fallbackReq).execute().use { fallbackResponse ->
                    val streamResult = streamApkResponse(fallbackResponse, outputFile, onProgress)
                    if (streamResult.isFailure) {
                        return@withContext Result.failure(streamResult.exceptionOrNull() ?: Exception("Download failed"))
                    }
                }
            }

            val verifyResult = verifyApkArchive(context, outputFile)
            if (verifyResult.isFailure) {
                outputFile.delete()
                return@withContext Result.failure(
                    verifyResult.exceptionOrNull() ?: SecurityException("APK verification failed")
                )
            }

            Log.d(TAG, "Download finished & verified: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading APK: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun streamApkResponse(
        response: Response,
        outputFile: File,
        onProgress: (progressPercent: Int, bytesRead: Long, totalBytes: Long) -> Unit
    ): Result<Unit> {
        if (!response.isSuccessful) {
            return Result.failure(Exception("Failed to download APK (HTTP ${response.code})"))
        }

        val responseBody = response.body ?: return Result.failure(Exception("Empty response body"))
        val totalBytes = responseBody.contentLength()

        val buffer = ByteArray(8 * 1024)
        var bytesReadTotal = 0L
        var read: Int
        var lastReportedPercent = -1

        FileOutputStream(outputFile).use { out ->
            responseBody.byteStream().use { input ->
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
        return Result.success(Unit)
    }

    /**
     * Verifies downloaded APK packageName, non-downgrade versionCode, and signing certificate SHA-256
     * against the currently running app's signing certificates (supports both debug and release keys).
     */
    fun verifyApkArchive(context: Context, apkFile: File): Result<Unit> {
        return try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                return Result.failure(Exception("APK file not found or empty"))
            }

            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }

            val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
            } ?: return Result.failure(SecurityException("Invalid or corrupted APK archive"))

            if (archiveInfo.packageName != context.packageName) {
                return Result.failure(
                    SecurityException("APK package mismatch: expected ${context.packageName}, got ${archiveInfo.packageName}")
                )
            }

            val installedInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, flags)
            }

            val installedVersionCode = PackageInfoCompat.getLongVersionCode(installedInfo)
            val archiveVersionCode = PackageInfoCompat.getLongVersionCode(archiveInfo)
            if (archiveVersionCode <= 0L || archiveVersionCode < installedVersionCode) {
                return Result.failure(
                    SecurityException("Blocked APK version downgrade (archive=$archiveVersionCode, installed=$installedVersionCode)")
                )
            }

            val installedCerts = extractSigningCertSha256(installedInfo)
            val archiveCerts = extractSigningCertSha256(archiveInfo)
            if (installedCerts.isEmpty() || archiveCerts.isEmpty() || installedCerts.none { it in archiveCerts }) {
                return Result.failure(
                    SecurityException("APK signing certificate mismatch — update blocked for security")
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractSigningCertSha256(pkgInfo: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo
            if (signingInfo != null) {
                if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
                else signingInfo.signingCertificateHistory
            } else {
                null
            }
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures
        } ?: return emptySet()

        val digest = MessageDigest.getInstance("SHA-256")
        return signatures.map { sig ->
            digest.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    /**
     * Triggers the Android system package installer for the downloaded APK.
     */
    fun installApk(context: Context, apkFile: File): Result<Unit> {
        return try {
            val verifyResult = verifyApkArchive(context, apkFile)
            if (verifyResult.isFailure) {
                return verifyResult
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
     * Compares two semantic version strings (e.g. "1.1.0" > "1.0.0", "release-2.2" > "2.1").
     * Returns true only if remoteVersion is strictly newer than currentVersion.
     */
    fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
        return try {
            val remoteParts = parseVersionParts(remoteTag)
            val currentParts = parseVersionParts(currentVersion)
            if (remoteParts.isEmpty() || currentParts.isEmpty()) return false

            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0L }
                val c = currentParts.getOrElse(i) { 0L }
                if (r > c) return true
                if (r < c) return false
            }

            false
        } catch (e: Exception) {
            Log.w(TAG, "Version comparison failed for remote=$remoteTag, current=$currentVersion: ${e.message}")
            false
        }
    }

    private fun parseVersionParts(version: String): List<Long> {
        val firstDigitIdx = version.indexOfFirst { it.isDigit() }
        if (firstDigitIdx < 0) return emptyList()
        val core = version.substring(firstDigitIdx)
            .substringBefore('-')
            .substringBefore('+')
            .trim()
        return core.split(".")
            .mapNotNull { segment -> segment.takeWhile { it.isDigit() }.toLongOrNull() }
    }
}
