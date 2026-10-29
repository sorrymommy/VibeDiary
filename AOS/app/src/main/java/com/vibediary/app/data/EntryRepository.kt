package com.vibediary.app.data

import android.content.Context
import com.vibediary.app.ai.AnalysisWorker
import com.vibediary.app.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID

class EntryRepository(
    private val context: Context,
    private val dao: EntryDao,
    private val prefs: AppPrefs,
) {
    private val mediaDir = File(context.filesDir, "media").apply { mkdirs() }

    fun mediaFile(name: String) = File(mediaDir, name)

    fun newMediaFile(extension: String) = File(mediaDir, "${UUID.randomUUID()}.$extension")

    fun observeAll(): Flow<List<Entry>> = dao.observeAll()

    fun observe(id: String): Flow<Entry?> = dao.observe(id)

    suspend fun get(id: String): Entry? = dao.get(id)

    suspend fun dirty(): List<Entry> = dao.dirty()

    /** Saves a freshly captured entry and kicks off on-device analysis (STT + tags). */
    suspend fun create(entry: Entry) {
        dao.upsert(entry)
        AnalysisWorker.enqueue(context, entry.id, exportMedia = prefs.exportToGallery)
    }

    /** User/analysis edit: bumps updatedAt and queues a Drive sync. */
    suspend fun update(entry: Entry) {
        dao.upsert(entry.copy(updatedAt = System.currentTimeMillis(), dirty = true))
        SyncScheduler.requestSync(context, prefs)
    }

    suspend fun reanalyze(entry: Entry) {
        val retranscribe = entry.analysis == AnalysisState.FAILED
        dao.upsert(
            entry.copy(
                transcript = if (retranscribe) null else entry.transcript,
                analysis = AnalysisState.PENDING,
                analysisMessage = null,
            )
        )
        AnalysisWorker.enqueue(context, entry.id)
    }

    /** Soft delete so the tombstone can propagate to Drive. */
    suspend fun delete(id: String) {
        val entry = dao.get(id) ?: return
        entry.mediaFiles.forEach { mediaFile(it).delete() }
        update(entry.copy(deleted = true))
    }

    // --- used by sync ---

    suspend fun saveFromRemote(entry: Entry) = dao.upsert(entry)

    suspend fun markSynced(id: String, updatedAt: Long, driveId: String) =
        dao.markSynced(id, updatedAt, driveId)
}
