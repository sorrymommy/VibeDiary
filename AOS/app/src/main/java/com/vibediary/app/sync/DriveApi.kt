package com.vibediary.app.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Minimal Drive v3 REST client scoped to the hidden appDataFolder. Blocking; call off the main thread. */
class DriveApi(private val accessToken: String) {

    @Serializable
    data class DriveFile(val id: String, val name: String, val modifiedTime: String? = null)

    @Serializable
    private data class FileList(val files: List<DriveFile> = emptyList(), val nextPageToken: String? = null)

    class AuthException : IOException("Google 인증이 만료되었습니다")

    fun list(query: String? = null): List<DriveFile> {
        val out = mutableListOf<DriveFile>()
        var pageToken: String? = null
        do {
            val url = FILES.newBuilder()
                .addQueryParameter("spaces", "appDataFolder")
                .addQueryParameter("fields", "nextPageToken,files(id,name,modifiedTime)")
                .addQueryParameter("pageSize", "1000")
                .apply {
                    query?.let { addQueryParameter("q", it) }
                    pageToken?.let { addQueryParameter("pageToken", it) }
                }
                .build()
            val page = json.decodeFromString<FileList>(execute(get(url)).use { it.body.string() })
            out += page.files
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return out
    }

    fun findByName(name: String): DriveFile? = list("name = '${name.replace("'", "\\'")}'").firstOrNull()

    fun downloadText(id: String): String =
        execute(get(FILES.newBuilder().addPathSegment(id).addQueryParameter("alt", "media").build()))
            .use { it.body.string() }

    fun downloadTo(id: String, target: File) {
        val tmp = File(target.parentFile, target.name + ".part")
        execute(get(FILES.newBuilder().addPathSegment(id).addQueryParameter("alt", "media").build())).use { resp ->
            tmp.outputStream().use { resp.body.byteStream().copyTo(it) }
        }
        if (!tmp.renameTo(target)) throw IOException("cannot write ${target.name}")
    }

    /** Creates a new file in appDataFolder, or overwrites [existingId]'s content. */
    fun upload(name: String, mimeType: String, content: RequestBody, existingId: String? = null): DriveFile {
        val metadata = buildJsonObject {
            put("name", name)
            if (existingId == null) putJsonArray("parents") { add("appDataFolder") }
        }.toString().toRequestBody(JSON)

        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata)
            .addPart(content)
            .build()

        val url = UPLOAD.newBuilder()
            .apply { existingId?.let { addPathSegment(it) } }
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter("fields", "id,name,modifiedTime")
            .build()
        val request = Request.Builder().url(url).auth()
            .method(if (existingId == null) "POST" else "PATCH", body)
            .build()
        return json.decodeFromString(execute(request).use { it.body.string() })
    }

    fun delete(id: String) {
        val request = Request.Builder().url(FILES.newBuilder().addPathSegment(id).build()).auth().delete().build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 404) throw IOException("Drive delete ${resp.code}")
        }
    }

    private fun get(url: HttpUrl) = Request.Builder().url(url).auth().get().build()

    private fun Request.Builder.auth() = header("Authorization", "Bearer $accessToken")

    private fun execute(request: Request): Response {
        val resp = http.newCall(request).execute()
        if (resp.isSuccessful) return resp
        val code = resp.code
        val msg = resp.use { it.body.string().take(300) }
        if (code == 401) throw AuthException()
        throw IOException("Drive HTTP $code: $msg")
    }

    companion object {
        private val FILES = "https://www.googleapis.com/drive/v3/files".toHttpUrl()
        private val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files".toHttpUrl()
        private val JSON = "application/json; charset=UTF-8".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }
        private val http = OkHttpClient.Builder()
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}
