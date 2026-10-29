package com.vibediary.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vibediary.app.container
import com.vibediary.app.capture.AudioCompressor
import com.vibediary.app.capture.AudioRecorder
import com.vibediary.app.data.AnalysisState
import com.vibediary.app.data.Entry
import com.vibediary.app.data.EntryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Two-way sync with the Drive appDataFolder.
 *
 * Layout in Drive: one `entry_<id>.json` per entry plus its media files under their
 * local names. Conflicts resolve by `updatedAt` (last writer wins); deletes travel as
 * tombstones (`deleted = true`) so every device learns about them.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @Serializable
    private data class EntryDto(
        val id: String,
        val createdAt: Long,
        val updatedAt: Long,
        val note: String = "",
        val transcript: String? = null,
        val photoFile: String? = null,
        val audioFile: String? = null,
        val audioDurationMs: Long = 0,
        val lat: Double? = null,
        val lng: Double? = null,
        val placeName: String? = null,
        val tags: List<String> = emptyList(),
        val analysis: String = AnalysisState.DONE.name,
        val deleted: Boolean = false,
        val stationLabel: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.container.prefs
        val repo = applicationContext.container.repository
        if (!prefs.syncEnabled) return@withContext Result.success()

        val token = DriveAuth.silentToken(applicationContext)
        if (token == null) {
            prefs.lastSyncError = "구글 계정을 다시 연결해 주세요"
            return@withContext Result.failure()
        }
        try {
            val api = DriveApi(token)
            push(api, repo, prefs.uploadMedia)
            pull(api, repo, prefs.lastPullTime)?.let { prefs.lastPullTime = it }
            prefs.lastSyncAt = System.currentTimeMillis()
            prefs.lastSyncError = null
            Result.success()
        } catch (e: DriveApi.AuthException) {
            prefs.lastSyncError = e.message
            Result.retry()
        } catch (e: IOException) {
            prefs.lastSyncError = e.message
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    private fun mimeOf(name: String) = when (name.substringAfterLast('.').lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        AudioRecorder.EXTENSION -> AudioRecorder.MIME_TYPE
        AudioCompressor.EXTENSION -> AudioCompressor.MIME_TYPE
        else -> "application/octet-stream"
    }

    private suspend fun push(api: DriveApi, repo: EntryRepository, uploadMedia: Boolean) {
        for (entry in repo.dirty()) {
            if (entry.deleted) {
                entry.mediaFiles.forEach { name -> api.findByName(name)?.let { api.delete(it.id) } }
            } else if (uploadMedia && entry.analysis != AnalysisState.PENDING) {
                // Not while analysis runs: its WAV is about to be replaced by a compressed copy,
                // and uploading it now would leave an orphan on Drive. The analysis result
                // marks the entry dirty again, so the media goes up on that sync.
                // A media file never changes under its name (compression makes a new name), so upload
                // only if Drive doesn't have it yet.
                for (name in entry.mediaFiles) {
                    val file = repo.mediaFile(name)
                    if (file.exists() && api.findByName(name) == null) {
                        api.upload(name, mimeOf(name), file.asRequestBody(mimeOf(name).toMediaType()))
                    }
                }
            }
            val jsonName = "entry_${entry.id}.json"
            val existingId = entry.driveJsonId ?: api.findByName(jsonName)?.id
            val body = json.encodeToString(EntryDto.serializer(), entry.toDto()).toRequestBody(JSON_TYPE)
            val uploaded = try {
                api.upload(jsonName, "application/json", body, existingId)
            } catch (e: IOException) {
                // Stale id (file removed on another device): create a fresh one.
                if (existingId == null) throw e
                api.upload(jsonName, "application/json", body, null)
            }
            repo.markSynced(entry.id, entry.updatedAt, uploaded.id)
        }
    }

    /** Returns the new pull watermark, or null if nothing new. */
    private suspend fun pull(api: DriveApi, repo: EntryRepository, since: String?): String? {
        val query = buildString {
            append("name contains 'entry_'")
            since?.let { append(" and modifiedTime > '$it'") }
        }
        val remote = api.list(query)
        if (remote.isEmpty()) return null

        for (file in remote) {
            val dto = runCatching {
                json.decodeFromString(EntryDto.serializer(), api.downloadText(file.id))
            }.getOrNull() ?: continue
            val local = repo.get(dto.id)
            if (local != null && local.updatedAt >= dto.updatedAt) continue

            if (!dto.deleted) {
                listOfNotNull(dto.photoFile, dto.audioFile).forEach { name ->
                    val target = repo.mediaFile(name)
                    if (!target.exists()) api.findByName(name)?.let { api.downloadTo(it.id, target) }
                }
            } else {
                local?.mediaFiles?.forEach { repo.mediaFile(it).delete() }
            }
            repo.saveFromRemote(dto.toEntry(driveJsonId = file.id))
        }
        return remote.mapNotNull { it.modifiedTime }.maxOrNull()
    }

    private fun Entry.toDto() = EntryDto(
        id, createdAt, updatedAt, note, transcript, photoFile, audioFile, audioDurationMs,
        lat, lng, placeName, tags, analysis.name, deleted, stationLabel,
    )

    private fun EntryDto.toEntry(driveJsonId: String) = Entry(
        id = id, createdAt = createdAt, updatedAt = updatedAt, note = note, transcript = transcript,
        photoFile = photoFile, audioFile = audioFile, audioDurationMs = audioDurationMs,
        lat = lat, lng = lng, placeName = placeName, stationLabel = stationLabel, tags = tags,
        analysis = runCatching { AnalysisState.valueOf(analysis) }.getOrDefault(AnalysisState.DONE),
        deleted = deleted, dirty = false, driveJsonId = driveJsonId,
    )

    private companion object {
        val JSON_TYPE = "application/json; charset=UTF-8".toMediaType()
    }
}
