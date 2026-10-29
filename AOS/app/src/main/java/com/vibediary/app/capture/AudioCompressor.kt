package com.vibediary.app.capture

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import kotlin.coroutines.CoroutineContext

/**
 * Shrinks a finished [AudioRecorder] WAV into AAC (.m4a), about an eighth of the size,
 * for keeping and backing up. The WAV is still what the speech recognizer reads, so this
 * runs only once the recording no longer needs transcribing.
 */
object AudioCompressor {
    const val EXTENSION = "m4a"
    const val MIME_TYPE = "audio/mp4"

    /** Plenty for a single voice at 16 kHz; about 240 KB per minute. */
    private const val BIT_RATE = 32_000
    private const val CODEC_TIMEOUT_US = 10_000L
    private const val INPUT_CHUNK_BYTES = 8_192
    /** Waits with no output after the last input before an encoder counts as stuck (~5 s). */
    private const val MAX_IDLE_DRAINS = 500

    /**
     * Writes [target] from [wav] and returns true only for a complete, playable file.
     * On failure or cancellation the partial [target] is deleted; [wav] is never touched.
     */
    suspend fun compress(wav: File, target: File): Boolean {
        var ok = false
        try {
            ok = withContext(Dispatchers.IO) {
                try {
                    encode(wav, target, coroutineContext)
                    target.length() > 0
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false // codec or file error: the caller keeps the WAV
                }
            }
        } finally {
            if (!ok) target.delete()
        }
        return ok
    }

    private fun encode(wav: File, target: File, job: CoroutineContext) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, AudioRecorder.SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, INPUT_CHUNK_BYTES)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        try {
            val muxer = MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            try {
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec.start()
                FileInputStream(wav).use { input ->
                    input.channel.position(AudioRecorder.WAV_HEADER_SIZE.toLong())
                    pump(codec, muxer, input, job)
                }
                // Writes the index that makes the file playable; failing here (e.g. disk full,
                // or no audio at all) must count as a failed compression.
                muxer.stop()
            } finally {
                muxer.release()
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
    }

    /** Feeds PCM in and drains AAC out until the encoder reports the end of the stream. */
    private fun pump(codec: MediaCodec, muxer: MediaMuxer, input: FileInputStream, job: CoroutineContext) {
        val info = MediaCodec.BufferInfo()
        val chunk = ByteArray(INPUT_CHUNK_BYTES)
        var track = -1
        var inputDone = false
        var presentedBytes = 0L
        var idleDrains = 0
        while (true) {
            job.ensureActive()
            if (!inputDone) {
                val index = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                if (index >= 0) {
                    val buffer = codec.getInputBuffer(index)!!
                    val read = input.read(chunk, 0, minOf(buffer.remaining(), chunk.size))
                    val timeUs = presentedBytes * 1_000_000 / AudioRecorder.BYTES_PER_SECOND
                    if (read <= 0) {
                        codec.queueInputBuffer(index, 0, 0, timeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        buffer.put(chunk, 0, read)
                        codec.queueInputBuffer(index, 0, read, timeUs, 0)
                        presentedBytes += read
                    }
                }
            }
            when (val index = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                }
                MediaCodec.INFO_TRY_AGAIN_LATER ->
                    if (inputDone && ++idleDrains > MAX_IDLE_DRAINS) throw IOException("AAC encoder never finished")
                else -> if (index >= 0) {
                    idleDrains = 0
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (!isConfig && info.size > 0 && track >= 0) {
                        muxer.writeSampleData(track, codec.getOutputBuffer(index)!!, info)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }
}
