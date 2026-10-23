package com.mahad.openbuddy.data.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** A newer release found on GitHub. */
data class UpdateInfo(
    val versionName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val notes: String
)

/** UI state of the self-update flow. */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val info: UpdateInfo, val percent: Int) : UpdateUiState
    data class ReadyToInstall(val info: UpdateInfo, val file: File) : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}

/**
 * Self-update for a sideloaded app: the latest GitHub release is the source
 * of truth. The app checks on launch, downloads the APK itself, and hands it
 * to the system installer — the user only confirms the system install prompt
 * (Android does not allow fully silent installs for regular apps).
 */
object AppUpdateManager {

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/mahadahmad82-source/Open-android-buddy/releases/latest"
    private const val APK_ASSET_NAME = "open-buddy.apk"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val downloadClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** Numeric dotted-version comparison ("1.1.10" > "1.1.9"). */
    fun isNewerVersion(remote: String, current: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val c = parts(current)
        val n = maxOf(r.size, c.size)
        for (i in 0 until n) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    suspend fun fetchLatestUpdate(currentVersion: String): Result<UpdateInfo?> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(LATEST_RELEASE_URL)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "OpenBuddy-Android")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("Update check failed (HTTP ${response.code})."))
                    }
                    val json = JSONObject(body)
                    val remoteVersion = json.optString("tag_name", "").removePrefix("apk-v").removePrefix("v")
                    if (remoteVersion.isBlank() || !isNewerVersion(remoteVersion, currentVersion)) {
                        return@withContext Result.success(null)
                    }
                    val assets = json.optJSONArray("assets")
                    var apkUrl: String? = null
                    var apkSize = 0L
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.optJSONObject(i) ?: continue
                            if (asset.optString("name") == APK_ASSET_NAME) {
                                apkUrl = asset.optString("browser_download_url", "")
                                apkSize = asset.optLong("size", 0L)
                                break
                            }
                        }
                    }
                    val url = apkUrl
                    if (url.isNullOrBlank()) {
                        return@withContext Result.failure(Exception("The latest release has no APK attached."))
                    }
                    Result.success(
                        UpdateInfo(
                            versionName = remoteVersion,
                            downloadUrl = url,
                            sizeBytes = apkSize,
                            notes = json.optString("body", "").trim().take(600)
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun downloadApk(
        context: Context,
        info: UpdateInfo,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(dir, "open-buddy.apk")
            val request = Request.Builder()
                .url(info.downloadUrl)
                .header("User-Agent", "OpenBuddy-Android")
                .get()
                .build()
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Download failed (HTTP ${response.code})."))
                }
                val body = response.body
                    ?: return@withContext Result.failure(Exception("Download returned no data."))
                val total = if (body.contentLength() > 0) body.contentLength() else info.sizeBytes
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        var lastPercent = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total > 0) {
                                val percent = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(percent)
                                }
                            }
                        }
                        output.flush()
                    }
                }
            }
            if (target.length() <= 0L) {
                return@withContext Result.failure(Exception("Downloaded file is empty."))
            }
            Result.success(target)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
