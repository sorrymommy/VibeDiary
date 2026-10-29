package com.vibediary.app.capture

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Records 16 kHz mono 16-bit PCM into a WAV file. That raw format is exactly what
 * the Android speech recognizer accepts as an audio source, so the same file can be
 * played back and transcribed without conversion.
 *
 * Supports pausing (the mic stops capturing, the file stays open) and appending to an
 * existing recording ("이어서 녹음").
 */
class AudioRecorder {
    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null

    /** PCM bytes in the file so far; the recorded length is derived from this, so pauses don't count. */
    @Volatile
    private var dataBytes = 0L

    @Volatile
    var isPaused: Boolean = false
        private set

    /** The mic stopped delivering audio mid-recording (taken by a call or another app, device error). */
    @Volatile
    var hasFailed: Boolean = false
        private set

    /** Loudness of the latest buffer, 0..1, for the live waveform. */
    @Volatile
    var level: Float = 0f
        private set

    /** Length of audio in the file, in milliseconds (excludes paused time). */
    val recordedMs: Long get() = dataBytes * 1000 / BYTES_PER_SECOND

    /**
     * Starts capturing into [target]. With [append], keeps what's already in the WAV and
     * continues after it; otherwise starts a fresh file.
     *
     * @return false if the mic couldn't be opened (busy, unavailable); nothing is started then.
     */
    @SuppressLint("MissingPermission") // caller requests RECORD_AUDIO first
    fun start(target: File, scope: CoroutineScope, append: Boolean = false): Boolean {
        check(audioRecord == null) { "already recording" }
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBufferSize <= 0) return false

        val recorder = runCatching {
            AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, ENCODING, minBufferSize * BUFFER_MULTIPLIER)
        }.getOrNull() ?: return false
        if (!recorder.tryStartCapturing()) {
            recorder.release()
            return false
        }

        audioRecord = recorder
        isPaused = false
        hasFailed = false
        // Set before capture starts so the timer never shows the previous recording's length.
        dataBytes = if (append) (target.length() - WAV_HEADER_SIZE).coerceAtLeast(0) else 0
        captureJob = scope.launch(Dispatchers.IO) { writeWav(recorder, target, append, minBufferSize) }
        return true
    }

    private suspend fun writeWav(recorder: AudioRecord, target: File, append: Boolean, chunkSize: Int) {
        RandomAccessFile(target, "rw").use { file ->
            if (append && file.length() > WAV_HEADER_SIZE) {
                dataBytes = file.length() - WAV_HEADER_SIZE
                file.seek(file.length())
            } else {
                dataBytes = 0
                file.setLength(0)
                file.write(ByteArray(WAV_HEADER_SIZE))
            }
            val chunk = ByteArray(chunkSize)
            try {
                captureLoop(recorder, file, chunk)
            } catch (e: IOException) {
                // Disk full or file gone: stop cleanly and let the screen say so instead of crashing.
                hasFailed = true
            } finally {
                // Also runs on cancellation (stop() while paused cancels the pause delay); without it
                // the header would stay empty and an appended part would be invisible to players.
                // A failure here leaves an unreadable header but must not crash the app.
                runCatching { writeWavHeader(file, dataBytes) }
            }
        }
    }

    private suspend fun captureLoop(recorder: AudioRecord, file: RandomAccessFile, chunk: ByteArray) {
        while (currentCoroutineContext().isActive) {
            if (isPaused) {
                level = 0f
                delay(PAUSE_POLL_MS)
                continue
            }
            val bytesRead = recorder.read(chunk, 0, chunk.size)
            when {
                // A chunk that lands just as pause is tapped is dropped: it is mostly the tap itself.
                bytesRead > 0 && !isPaused -> {
                    file.write(chunk, 0, bytesRead)
                    dataBytes += bytesRead
                    level = peakLevel(chunk, bytesRead)
                }
                // Reading a stopped recorder (pause racing this read) errors too; only a live one counts.
                bytesRead < 0 && !isPaused -> {
                    hasFailed = true
                    return
                }
            }
        }
    }

    fun pause() {
        val recorder = audioRecord ?: return
        if (isPaused) return
        isPaused = true
        recorder.stopQuietly()
    }

    /** @return false if the mic couldn't be taken back (e.g. a call is using it). */
    fun resume(): Boolean {
        val recorder = audioRecord ?: return false
        if (!isPaused) return true
        // Stay paused on failure (e.g. a call still holds the mic) so the user can try again later.
        if (!recorder.tryStartCapturing()) return false
        isPaused = false
        return true
    }

    /** Stops recording and returns the total length of the file's audio in milliseconds. Safe to call twice. */
    suspend fun stop(): Long {
        val recorder = audioRecord ?: return recordedMs
        audioRecord = null // first, so pause/resume/stop arriving meanwhile become no-ops
        captureJob?.cancelAndJoin()
        captureJob = null
        recorder.stopQuietly()
        recorder.release()
        isPaused = false
        return recordedMs
    }

    /** Non-suspending teardown for when the owner is destroyed mid-recording. */
    fun release() {
        captureJob?.cancel()
        captureJob = null
        audioRecord?.run { stopQuietly(); release() }
        audioRecord = null
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val EXTENSION = "wav"
        const val MIME_TYPE = "audio/wav"
        const val WAV_HEADER_SIZE = 44

        private const val BYTES_PER_SAMPLE = 2
        const val BYTES_PER_SECOND = SAMPLE_RATE * BYTES_PER_SAMPLE
        private const val BUFFER_MULTIPLIER = 4
        private const val PAUSE_POLL_MS = 50L

        private fun AudioRecord.tryStartCapturing(): Boolean {
            if (state != AudioRecord.STATE_INITIALIZED) return false
            return try {
                startRecording()
                recordingState == AudioRecord.RECORDSTATE_RECORDING
            } catch (e: IllegalStateException) {
                // Thrown when the mic is held elsewhere; the caller reports it, so false is enough here.
                false
            }
        }

        // stop() throws if the recorder isn't capturing; either way it no longer is, which is all callers need.
        private fun AudioRecord.stopQuietly() {
            runCatching { stop() }
        }

        private fun peakLevel(chunk: ByteArray, length: Int): Float {
            var peak = 0
            var i = 0
            while (i + 1 < length) {
                val sample = (chunk[i].toInt() and 0xFF) or (chunk[i + 1].toInt() shl 8)
                peak = maxOf(peak, kotlin.math.abs(sample.toShort().toInt()))
                i += BYTES_PER_SAMPLE
            }
            return (peak / 32768f).coerceIn(0f, 1f)
        }

        private fun writeWavHeader(file: RandomAccessFile, dataLength: Long) {
            fun intLE(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
            fun shortLE(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
            file.seek(0)
            file.write("RIFF".toByteArray())
            file.write(intLE((dataLength + 36).toInt()))
            file.write("WAVEfmt ".toByteArray())
            file.write(intLE(16))                 // fmt chunk size
            file.write(shortLE(1))                // PCM
            file.write(shortLE(1))                // mono
            file.write(intLE(SAMPLE_RATE))
            file.write(intLE(BYTES_PER_SECOND))   // byte rate
            file.write(shortLE(BYTES_PER_SAMPLE)) // block align
            file.write(shortLE(16))               // bits per sample
            file.write("data".toByteArray())
            file.write(intLE(dataLength.toInt()))
        }
    }
}
