package com.hang2515.personalcalendar.backup

import com.hang2515.personalcalendar.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class BackupApiException(val status: Int, message: String) : IOException(message)
data class BackupResponse(val body: String, val etag: String)

class BackupApi {
    suspend fun request(token: String, method: String, snapshot: Boolean = false, body: String? = null, etag: String? = null): BackupResponse =
        withContext(Dispatchers.IO) {
            check(CloudConfig.isConfigured) { "此安装包尚未配置云服务" }
            val path = if (snapshot) "/v1/backup/snapshot" else "/v1/backup"
            val connection = URL(BuildConfig.CLOUD_API_URL.trimEnd('/') + path).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 15000; connection.readTimeout = 20000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    require(bytes.size <= BackupSnapshot.MAX_BYTES) { "备份超过 1 MiB" }
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty(if (etag == null) "If-None-Match" else "If-Match", etag ?: "*")
                    connection.setFixedLengthStreamingMode(bytes.size)
                    connection.outputStream.use { it.write(bytes) }
                }
                val code = connection.responseCode
                if (code !in 200..299) {
                    connection.errorStream?.close()
                    throw BackupApiException(code, when (code) {
                        401, 403 -> "登录已失效或没有权限，请重新登录"
                        404 -> "此账号尚无云备份"
                        409, 412 -> "云备份已有更新，请重新读取云备份后选择恢复或重新上传"
                        400, 413 -> "备份格式或大小不受支持"
                        429 -> "请求较多，请稍后重试"
                        else -> "云备份暂时不可用，请稍后重试"
                    })
                }
                val bytes = connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (output.size() <= BackupSnapshot.MAX_BYTES) {
                        val count = input.read(buffer, 0, minOf(buffer.size, BackupSnapshot.MAX_BYTES + 1 - output.size()))
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                require(bytes.size <= BackupSnapshot.MAX_BYTES) { "云备份超过大小限制" }
                val responseBody = String(bytes, Charsets.UTF_8)
                JSONObject(responseBody)
                BackupResponse(responseBody, checkNotNull(connection.getHeaderField("ETag")) { "云备份缺少版本标识" })
            } finally { connection.disconnect() }
        }
}
