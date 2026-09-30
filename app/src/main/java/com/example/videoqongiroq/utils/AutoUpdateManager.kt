package com.example.videoqongiroq.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class AutoUpdateManager(private val context: Context) {

    private val client = OkHttpClient.Builder().build()

    companion object {
        private const val TAG = "AutoUpdateManager"
        private const val VERSION_URL = "https://video-qongiroq.onrender.com/version"
        private const val CURRENT_VERSION_CODE = 1
    }

    suspend fun checkAndAutoUpdate(
        onUpdateAvailable: (versionName: String, downloadUrl: String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(VERSION_URL).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext
                    val json = JSONObject(body)
                    val latestCode = json.optInt("latestVersionCode", CURRENT_VERSION_CODE)
                    val versionName = json.optString("versionName", "1.1")
                    val downloadUrl = json.optString("downloadUrl", "https://video-qongiroq.onrender.com/download")

                    if (latestCode > CURRENT_VERSION_CODE) {
                        Log.d(TAG, "New version available: $versionName (code $latestCode)")
                        withContext(Dispatchers.Main) {
                            onUpdateAvailable(versionName, downloadUrl)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking version update", e)
            }
        }
    }

    suspend fun downloadAndInstallApk(downloadUrl: String, onProgress: (Int) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(downloadUrl).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext

                val body = response.body ?: return@withContext
                val totalLength = body.contentLength()

                val apkFile = File(context.cacheDir, "video_qongiroq_update.apk")
                if (apkFile.exists()) apkFile.delete()

                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(apkFile)

                val buffer = ByteArray(8192)
                var downloaded: Long = 0
                var read: Int

                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                    downloaded += read
                    if (totalLength > 0) {
                        val progress = ((downloaded * 100) / totalLength).toInt()
                        withContext(Dispatchers.Main) {
                            onProgress(progress)
                        }
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                withContext(Dispatchers.Main) {
                    installApk(apkFile)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error downloading APK update", e)
            }
        }
    }

    private fun installApk(apkFile: File) {
        try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering APK install", e)
        }
    }
}
