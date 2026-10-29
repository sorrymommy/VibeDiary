package com.vibediary.app.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vibediary.app.capture.AudioCompressor
import com.vibediary.app.capture.AudioRecorder
import com.vibediary.app.capture.Locator
import com.vibediary.app.capture.MediaExport
import com.vibediary.app.data.AnalysisState
import com.vibediary.app.data.Entry
import com.vibediary.app.data.EntryRepository
import com.vibediary.app.container
import kotlinx.coroutines.delay
import java.io.File

/**
 * Background pipeline: recording → text, then the recording is compressed for keeping;
 * photo → labels, location → place, all → tags.
 */
class AnalysisWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    /** What became of the recording's text on this run. */
    private data class Speech(val transcript: String?, val failure: String?, val canRetry: Boolean)

    override suspend fun doWork(): Result {
        val repo = applicationContext.container.repository
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val entry = repo.get(id)?.takeIf { !it.deleted } ?: return Result.success()

        val speech = transcribe(repo, entry)
        // A failure "다시 분석" can fix needs the WAV the recognizer reads, so keep it until then.
        val audioFile = if (speech.canRetry) entry.audioFile else entry.audioFile?.let { compress(repo, it) }

        // Only on the first run after capture, so "다시 분석" never makes duplicates.
        if (inputData.getBoolean(KEY_EXPORT, false)) {
            entry.photoFile?.let { MediaExport.exportPhoto(applicationContext, repo.mediaFile(it), entry.createdAt) }
            audioFile?.let { MediaExport.exportAudio(applicationContext, repo.mediaFile(it), entry.createdAt) }
        }

        val imageTags = entry.photoFile?.let { ImageTagger.tags(applicationContext, repo.mediaFile(it)) }.orEmpty()
        val place = if (entry.lat != null && entry.lng != null) {
            Locator.place(applicationContext, entry.lat, entry.lng)
        } else null

        val text = listOfNotNull(entry.note, speech.transcript).joinToString("\n")
        // A name the user gave the place beats the address-derived 동/구 as its tag.
        val tags = TagExtractor.extract(text, imageTags, entry.stationLabel ?: place?.tag)

        // Re-read so edits the user made while we were working are not lost.
        val latest = repo.get(id)?.takeIf { !it.deleted }
        val compressed = audioFile != entry.audioFile
        // Deleted, or its recording replaced (e.g. by a sync), meanwhile: nothing will point at
        // the compressed copy, so drop it.
        if (compressed && (latest == null || latest.audioFile != entry.audioFile)) {
            audioFile?.let { repo.mediaFile(it).delete() }
        }
        if (latest == null) return Result.success()
        val keepsCompressed = compressed && latest.audioFile == entry.audioFile
        repo.update(
            latest.copy(
                transcript = latest.transcript ?: speech.transcript,
                audioFile = if (keepsCompressed) audioFile else latest.audioFile,
                placeName = latest.placeName ?: place?.name,
                tags = (latest.tags + tags).distinct(),
                analysis = if (speech.failure == null) AnalysisState.DONE else AnalysisState.FAILED,
                analysisMessage = speech.failure,
            )
        )
        // Only now that the entry points at the compressed copy is the WAV safe to drop.
        if (keepsCompressed) entry.audioFile?.let { repo.mediaFile(it).delete() }
        return Result.success()
    }

    private suspend fun transcribe(repo: EntryRepository, entry: Entry): Speech {
        val audioFile = entry.audioFile
        if (audioFile == null || entry.transcript != null) return Speech(entry.transcript, null, canRetry = false)
        // Already compressed, so a failure that can't be retried: say why if it was the length.
        if (File(audioFile).extension != AudioRecorder.EXTENSION) {
            val reason = if (Transcriber.isTooLong(entry.audioDurationMs)) Transcriber.TOO_LONG_REASON else NOT_TRANSCRIBABLE
            return Speech(null, reason, canRetry = false)
        }

        val wav = repo.mediaFile(audioFile)
        var result = Transcriber.transcribe(applicationContext, wav, TRANSCRIBE_BUDGET_MS)
        // Saving right after the on-screen preview can find the engine still busy with it.
        // Retry here rather than with Result.retry(), which would redo the gallery export.
        if (result is Transcriber.Result.Unavailable && result.problem == Transcriber.Problem.BUSY) {
            delay(BUSY_RETRY_DELAY_MS)
            result = Transcriber.transcribe(applicationContext, wav, TRANSCRIBE_BUDGET_MS)
        }
        return when (result) {
            is Transcriber.Result.Text -> Speech(result.text, null, canRetry = false)
            is Transcriber.Result.Unavailable -> Speech(
                transcript = null,
                failure = result.reason,
                canRetry = result.problem in RETRYABLE,
            )
        }
    }

    /** Returns the compressed file's name, or [wavName] unchanged if it isn't a WAV or fails. */
    private suspend fun compress(repo: EntryRepository, wavName: String): String {
        if (File(wavName).extension != AudioRecorder.EXTENSION) return wavName
        val target = repo.newMediaFile(AudioCompressor.EXTENSION)
        // On failure the WAV stays: bigger, but still a playable recording.
        return if (AudioCompressor.compress(repo.mediaFile(wavName), target)) target.name else wavName
    }

    companion object {
        private const val KEY_ID = "entryId"
        /** WorkManager stops a worker after ~10 min; leave room for the rest of the analysis. */
        private const val TRANSCRIBE_BUDGET_MS = 8 * 60_000L
        private const val BUSY_RETRY_DELAY_MS = 3_000L
        private const val KEY_EXPORT = "exportMedia"
        private const val NOT_TRANSCRIBABLE = "이 녹음은 이미 압축되어 다시 글로 옮길 수 없어요"
        /** Problems that "다시 분석" may get past later; the others never change for this clip. */
        private val RETRYABLE = setOf(
            Transcriber.Problem.LANGUAGE_PACK,
            Transcriber.Problem.BUSY,
            Transcriber.Problem.TEMPORARY,
        )

        fun enqueue(context: Context, entryId: String, exportMedia: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<AnalysisWorker>()
                .setInputData(workDataOf(KEY_ID to entryId, KEY_EXPORT to exportMedia))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("analyze-$entryId", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
