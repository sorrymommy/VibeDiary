package com.vibediary.app.ai

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import com.vibediary.app.capture.AudioRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Turns a recorded WAV file into text using Android's built-in speech recognizer,
 * preferring the on-device engine so nothing leaves the phone and nothing costs money.
 *
 * Feeding a file (instead of the live mic) requires Android 13+ (EXTRA_AUDIO_SOURCE).
 */
object Transcriber {
    /** Why there's no text, so each screen can word it for its own situation. */
    enum class Problem {
        /** This phone can never do it (Android 12 or lower, no recognition engine). */
        UNSUPPORTED,
        /** The Korean on-device model is missing; a download has been requested. */
        LANGUAGE_PACK,
        /** The engine is busy with another job right now; retrying shortly usually works. */
        BUSY,
        /** Timed out or a one-off engine error: may work later, but not by retrying at once. */
        TEMPORARY,
        /** Longer than [MAX_CLIP_MS]: never transcribed, so retrying is pointless. */
        TOO_LONG,
    }

    /**
     * Recordings longer than this aren't transcribed. At [FEED_SPEED] a 10-minute clip takes
     * about 5 minutes, which still fits the background worker (stopped after ~10 minutes).
     */
    const val MAX_CLIP_MINUTES = 10
    const val MAX_CLIP_MS = MAX_CLIP_MINUTES * 60_000L
    const val TOO_LONG_REASON = "${MAX_CLIP_MINUTES}분이 넘는 녹음은 글로 옮기지 않아요"

    fun isTooLong(durationMs: Long): Boolean = durationMs > MAX_CLIP_MS

    sealed interface Result {
        data class Text(val text: String) : Result
        /** [reason] is the wording kept on the saved entry (shown on its detail screen). */
        data class Unavailable(val problem: Problem, val reason: String) : Result
    }

    /** Room for the engine's own start-up and finishing work on top of the feed time. */
    private const val ENGINE_MARGIN_MS = 30_000L

    /**
     * How much faster than real time the recording is fed to the engine. Measured on a
     * Galaxy S23 with a 45 s clip: 1× and 2× gave the full text; 4× dropped words at every
     * segment boundary; all-at-once kept only the first sentence.
     */
    private const val FEED_SPEED = 2
    private const val FEED_CHUNK_MS = 100
    /** Trailing silence so the engine finalises the last segment before the input ends. */
    private const val TAIL_SILENCE_MS = 1_500

    /**
     * @param budgetMs longest the caller can wait (background workers are stopped after ~10 min).
     */
    suspend fun transcribe(context: Context, wav: File, budgetMs: Long = Long.MAX_VALUE): Result {
        if (Build.VERSION.SDK_INT < 33) {
            return Result.Unavailable(Problem.UNSUPPORTED, "음성→텍스트 변환은 Android 13 이상에서 지원됩니다")
        }
        if (!wav.exists()) return Result.Unavailable(Problem.UNSUPPORTED, "녹음 파일이 없습니다")
        val clipMs = (wav.length() - AudioRecorder.WAV_HEADER_SIZE) * 1000 / AudioRecorder.BYTES_PER_SECOND
        if (isTooLong(clipMs)) return Result.Unavailable(Problem.TOO_LONG, TOO_LONG_REASON)
        // A slower phone can only keep up with real time (the full pipe throttles the feed),
        // so wait for that worst case, within the caller's budget.
        val timeoutMs = minOf(clipMs + TAIL_SILENCE_MS + ENGINE_MARGIN_MS, budgetMs)
        return withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.Main) { transcribe33(context.applicationContext, wav) }
        } ?: Result.Unavailable(Problem.TEMPORARY, "음성 변환 시간이 너무 오래 걸렸어요")
    }

    @RequiresApi(33)
    private suspend fun transcribe33(context: Context, wav: File): Result = suspendCancellableCoroutine { cont ->
        val onDevice = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        if (!onDevice && !SpeechRecognizer.isRecognitionAvailable(context)) {
            cont.resume(Result.Unavailable(Problem.UNSUPPORTED, "이 기기에 음성인식 엔진이 없습니다"))
            return@suspendCancellableCoroutine
        }
        val recognizer = if (onDevice) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        val (readSide, writeSide) = ParcelFileDescriptor.createPipe()
        val segments = mutableListOf<String>()
        // Tells the feeding thread to stop once the session is over, so it never outlives it.
        // Only that thread closes writeSide (closing it here could race a write in progress).
        val feedStopped = AtomicBoolean(false)

        fun stopFeeding() {
            feedStopped.set(true)
            runCatching { readSide.close() }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readSide)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, AudioRecorder.SAMPLE_RATE)
            // Keep listening across pauses until the file ends.
            putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
        }

        fun finish(result: Result) {
            if (!cont.isActive) return
            cont.resume(result)
            recognizer.destroy()
            stopFeeding()
        }

        fun collected() = segments.joinToString(" ")

        // Segments come with a leading space; trim each so the text joins with single spaces.
        fun addBest(bundle: Bundle) {
            bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { segments += it }
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onSegmentResults(segmentResults: Bundle) = addBest(segmentResults)

            override fun onEndOfSegmentedSession() = finish(Result.Text(collected()))

            override fun onResults(results: Bundle) {
                addBest(results)
                finish(Result.Text(collected()))
            }

            override fun onError(error: Int) {
                when {
                    segments.isNotEmpty() -> finish(Result.Text(collected()))
                    error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> finish(Result.Text(""))
                    error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                        error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                        if (onDevice) runCatching { recognizer.triggerModelDownload(intent) }
                        finish(Result.Unavailable(Problem.LANGUAGE_PACK, "한국어 음성인식 언어팩이 필요합니다. 다운로드 후 '다시 분석'을 눌러주세요"))
                    }
                    error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                        finish(Result.Unavailable(Problem.BUSY, "음성인식 엔진이 다른 작업 중이었어요"))
                    else -> finish(Result.Unavailable(Problem.TEMPORARY, "음성인식 엔진이 응답하지 않았어요 (코드 $error)"))
                }
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer.startListening(intent)

        // Stream raw PCM (skip the WAV header) into the pipe; closing it signals end of audio.
        // If the session ends or is cancelled first, stopFeeding() ends this thread: the flag
        // stops the loop, and once the engine lets go of the pipe any pending write fails.
        Thread({
            runCatching {
                ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { out ->
                    FileInputStream(wav).use { input ->
                        input.skip(AudioRecorder.WAV_HEADER_SIZE.toLong())
                        feedPaced(input, out, feedStopped)
                    }
                }
            }
        }, "transcribe-feed").start()

        cont.invokeOnCancellation {
            Handler(Looper.getMainLooper()).post {
                recognizer.destroy()
                stopFeeding()
            }
        }
    }

    /**
     * The engine treats the stream as live audio: handed the whole file at once it ends the
     * session after the first segment and drops the rest. So feed it in short chunks at
     * [FEED_SPEED]× real time, then a little silence so the last segment is finalised.
     */
    private fun feedPaced(input: InputStream, out: OutputStream, stopped: AtomicBoolean) {
        val chunk = ByteArray(AudioRecorder.BYTES_PER_SECOND * FEED_CHUNK_MS / 1000)
        val pauseMs = FEED_CHUNK_MS.toLong() / FEED_SPEED
        while (!stopped.get()) {
            val bytesRead = input.read(chunk)
            if (bytesRead <= 0) break
            out.write(chunk, 0, bytesRead)
            Thread.sleep(pauseMs)
        }
        val silence = ByteArray(chunk.size)
        repeat(TAIL_SILENCE_MS / FEED_CHUNK_MS) {
            if (stopped.get()) return
            out.write(silence)
            Thread.sleep(pauseMs)
        }
    }
}
