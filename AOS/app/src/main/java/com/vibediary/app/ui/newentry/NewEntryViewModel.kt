package com.vibediary.app.ui.newentry

import android.app.Application
import android.location.Location
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.vibediary.app.ai.Transcriber
import com.vibediary.app.capture.AudioRecorder
import com.vibediary.app.capture.Locator
import com.vibediary.app.capture.Place
import com.vibediary.app.container
import com.vibediary.app.data.Entry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class NewEntryViewModel(app: Application, private val savedState: SavedStateHandle) : AndroidViewModel(app) {
    private val repo = app.container.repository
    private val prefs = app.container.prefs
    private val recorder = AudioRecorder()
    // Kept in the saved state: the camera app is heavy enough that Android may kill this
    // process while it is open, and the photo must still land here when the user comes back.
    private var pendingPhoto: File?
        get() = savedState.get<String>(KEY_PENDING_PHOTO)?.let(::File)
        set(file) { savedState[KEY_PENDING_PHOTO] = file?.path }
    private var levelJob: Job? = null
    private var saved = false

    /** One-shot: the dock button that opened this screen has been acted on. Saved so a screen
     *  restored after the camera doesn't open the camera again. */
    var startHandled: Boolean
        get() = savedState[KEY_START_HANDLED] ?: false
        set(handled) { savedState[KEY_START_HANDLED] = handled }

    var note by mutableStateOf("")
    var photoFile by mutableStateOf<String?>(null)
        private set
    var audioFile by mutableStateOf<String?>(null)
        private set
    var audioDurationMs by mutableLongStateOf(0L)
        private set
    var recording by mutableStateOf(false)
        private set
    var paused by mutableStateOf(false)
        private set
    /** "다 했어요" is being processed; recording controls are disabled until it finishes. */
    var stopping by mutableStateOf(false)
        private set
    /** A problem to tell the user about (mic busy or lost); the screen shows it once and clears it. */
    var recordingError by mutableStateOf<String?>(null)
        private set
    /** Audio captured so far while recording (pauses excluded), for the timer. */
    var recordedMs by mutableLongStateOf(0L)
        private set
    /** Recent loudness samples for the live waveform, oldest first. */
    val levels = mutableStateListOf<Float>()

    val locationEnabled: Boolean get() = prefs.saveLocation
    var location by mutableStateOf<Location?>(null)
        private set
    var place by mutableStateOf<Place?>(null)
        private set
    var locating by mutableStateOf(false)
        private set

    val canSave: Boolean
        get() = !recording && (note.isNotBlank() || photoFile != null || audioFile != null)

    private var locationJob: Job? = null

    var saving by mutableStateOf(false)
        private set

    fun fetchLocation() {
        if (!prefs.saveLocation || locationJob != null || location != null) return
        locationJob = viewModelScope.launch {
            locating = true
            val loc = Locator.current(getApplication())
            location = loc
            locating = false
            // The name is only for display here; the analysis worker fills it in if this is slow.
            if (loc != null) launch { place = Locator.place(getApplication(), loc.latitude, loc.longitude) }
        }
    }

    /** Name the user typed over the automatic 동/구 name; null = use the automatic one. */
    var placeLabel by mutableStateOf<String?>(null)

    /** The 동/구 name the address gives (what shows when the user hasn't renamed it). */
    val autoPlaceName: String? get() = place?.name?.split(" ")?.last()

    /** True once the user picked a spot on the map; GPS then no longer overrides it. */
    var manualLocation by mutableStateOf(false)
        private set

    fun setManualLocation(lat: Double, lng: Double, picked: Place?) {
        locationJob?.cancel()
        locating = false
        manualLocation = true
        location = Location("manual").apply { latitude = lat; longitude = lng }
        place = picked
        if (picked == null) viewModelScope.launch { place = Locator.place(getApplication(), lat, lng) }
    }

    // --- photo ---

    fun newPhotoUri(): Uri {
        val file = repo.newMediaFile("jpg")
        pendingPhoto = file
        val app = getApplication<Application>()
        return FileProvider.getUriForFile(app, "${app.packageName}.files", file)
    }

    fun onPhotoTaken(success: Boolean) {
        val file = pendingPhoto ?: return
        pendingPhoto = null
        if (success && file.length() > 0) replacePhoto(file.name) else file.delete()
    }

    fun onPhotoPicked(uri: Uri?) {
        uri ?: return
        viewModelScope.launch {
            val file = repo.newMediaFile("jpg")
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)!!.use { input ->
                        file.outputStream().use { input.copyTo(it) }
                    }
                }.isSuccess
            }
            if (ok) replacePhoto(file.name) else file.delete()
        }
    }

    fun removePhoto() = replacePhoto(null)

    private fun replacePhoto(name: String?) {
        photoFile?.let { repo.mediaFile(it).delete() }
        photoFile = name
    }

    // --- audio ---

    /** New recording, replacing any earlier one on this screen. */
    fun startRecording() {
        if (recording) return
        audioFile?.let { repo.mediaFile(it).delete() }
        val file = repo.newMediaFile(AudioRecorder.EXTENSION)
        audioFile = file.name
        audioDurationMs = 0
        levels.clear()
        if (!startCapture(file, append = false)) {
            file.delete()
            audioFile = null
        }
    }

    /** "이어서 녹음": keep the finished recording and add more to the end of it. */
    fun continueRecording() {
        if (recording) return
        val file = audioFile?.let { repo.mediaFile(it) }?.takeIf { it.exists() } ?: return startRecording()
        startCapture(file, append = true)
    }

    private fun startCapture(file: File, append: Boolean): Boolean {
        if (!recorder.start(file, viewModelScope, append)) {
            recordingError = MIC_UNAVAILABLE
            return false
        }
        clearTranscript() // the recording is changing, so any earlier text no longer matches it
        recording = true
        paused = false
        recordedMs = audioDurationMs
        levelJob = viewModelScope.launch {
            while (true) {
                if (recorder.hasFailed) {
                    recordingError = MIC_LOST
                    stopRecording()
                    break
                }
                if (!recorder.isPaused) {
                    levels.add(recorder.level)
                    if (levels.size > WAVE_BARS) levels.removeAt(0)
                }
                recordedMs = recorder.recordedMs
                delay(LEVEL_POLL_MS)
            }
        }
        return true
    }

    fun togglePause() {
        if (!recording || stopping) return
        if (recorder.isPaused) {
            if (!recorder.resume()) {
                recordingError = MIC_UNAVAILABLE
                return
            }
        } else {
            recorder.pause()
        }
        paused = recorder.isPaused
    }

    fun stopRecording() {
        if (!recording || stopping) return
        stopping = true
        levelJob?.cancel()
        viewModelScope.launch {
            audioDurationMs = recorder.stop()
            recording = false
            paused = false
            stopping = false
            transcribe()
        }
    }

    fun clearRecordingError() {
        recordingError = null
    }

    fun removeAudio() {
        clearTranscript()
        audioFile?.let { repo.mediaFile(it).delete() }
        audioFile = null
        audioDurationMs = 0
        levels.clear()
    }

    // --- transcript (shown before saving) ---

    private var transcribeJob: Job? = null

    /** What was said, once the on-device recognizer has turned the recording into text. */
    var transcript by mutableStateOf<String?>(null)
        private set
    var transcribing by mutableStateOf(false)
        private set
    /** Why the text couldn't be produced right now (e.g. language pack missing). */
    var transcriptProblem by mutableStateOf<Transcriber.Problem?>(null)
        private set

    /** Turns the finished recording into text right away, so it can be read before 저장. */
    private fun transcribe() {
        val file = audioFile?.let { repo.mediaFile(it) } ?: return
        transcribeJob?.cancel()
        transcribing = true
        transcriptProblem = null
        transcribeJob = viewModelScope.launch {
            var result = Transcriber.transcribe(getApplication(), file)
            // The engine may be busy (e.g. analysing the previous entry); one retry usually does it.
            // Other failures, like a timeout, would only repeat, so they aren't retried.
            if (result is Transcriber.Result.Unavailable && result.problem == Transcriber.Problem.BUSY) {
                delay(TRANSCRIBE_RETRY_DELAY_MS)
                result = Transcriber.transcribe(getApplication(), file)
            }
            when (result) {
                is Transcriber.Result.Text -> transcript = result.text
                is Transcriber.Result.Unavailable -> transcriptProblem = result.problem
            }
            transcribing = false
        }
    }

    private fun clearTranscript() {
        transcribeJob?.cancel()
        transcribeJob = null
        transcript = null
        transcriptProblem = null
        transcribing = false
    }

    // --- save ---

    /** When this screen opened, i.e. when the user started writing. */
    private val startedAt = System.currentTimeMillis()

    /** Where the user started writing vs where they are on 저장, when those are far apart. */
    data class MoveChoice(
        val start: Location,
        val startPlace: Place?,
        val now: Location,
        val nowPlace: Place?,
        val meters: Float,
        val minutesAgo: Long,
    )

    var moveChoice by mutableStateOf<MoveChoice?>(null)
        private set

    fun save(onSaved: () -> Unit) {
        if (!canSave || saving) return
        saving = true
        viewModelScope.launch {
            // A quick "record → save" can beat the GPS fix: always wait for the lookup to finish
            // so the moment lands on the map. The fused provider gives up on its own (→ null),
            // so this can't hang forever.
            if (location == null) locationJob?.join()

            // Written while on the move? Ask which place the moment belongs to.
            val start = location
            if (start != null && !manualLocation && prefs.saveLocation) {
                val now = Locator.current(getApplication())
                if (now != null && now.distanceTo(start) > MOVED_METERS) {
                    moveChoice = MoveChoice(
                        start = start,
                        startPlace = place,
                        now = now,
                        nowPlace = Locator.place(getApplication(), now.latitude, now.longitude),
                        meters = now.distanceTo(start),
                        minutesAgo = (System.currentTimeMillis() - startedAt) / 60_000,
                    )
                    saving = false
                    return@launch
                }
            }
            create(onSaved)
        }
    }

    /** Answer to [moveChoice]: keep where writing started, or use where the user is now. */
    fun saveAt(useNow: Boolean, onSaved: () -> Unit) {
        val choice = moveChoice ?: return
        moveChoice = null
        if (useNow) {
            // The start place's automatic name (prefilled when the name was tapped) isn't a
            // name the user chose; let the new place name itself.
            if (placeLabel?.trim() == choice.startPlace?.name?.split(" ")?.last()) placeLabel = null
            location = choice.now
            place = choice.nowPlace
        }
        saving = true
        viewModelScope.launch { create(onSaved) }
    }

    /** "계속 쓰기": close the question and keep editing. */
    fun dismissMoveChoice() {
        moveChoice = null
    }

    private suspend fun create(onSaved: () -> Unit) {
        // Saved before the text was ready: drop it here and let the background analysis
        // transcribe instead (it does so whenever the transcript is still empty).
        if (transcribing) clearTranscript()
        val loc = location
        repo.create(
            Entry(
                note = note.trim(),
                transcript = transcript,
                photoFile = photoFile,
                audioFile = audioFile,
                audioDurationMs = audioDurationMs,
                lat = loc?.latitude,
                lng = loc?.longitude,
                placeName = place?.name,
                stationLabel = placeLabel?.trim()?.takeIf { it.isNotEmpty() && it != autoPlaceName },
            )
        )
        saved = true
        onSaved()
    }

    override fun onCleared() {
        recorder.release()
        if (!saved) {
            // Discarded draft: don't leave orphaned media behind.
            listOfNotNull(photoFile, audioFile).forEach { repo.mediaFile(it).delete() }
            // pendingPhoto stays: with the camera open this is the system tearing the screen down,
            // and the restored screen still needs that file for the shot.
        }
    }

    companion object {
        const val WAVE_BARS = 30
        private const val KEY_PENDING_PHOTO = "pendingPhoto"
        private const val KEY_START_HANDLED = "startHandled"
        private const val LEVEL_POLL_MS = 90L
        private const val TRANSCRIBE_RETRY_DELAY_MS = 1_500L
        private const val MIC_UNAVAILABLE = "지금은 마이크를 쓸 수 없어요. 통화 중이거나 다른 앱이 쓰고 있는지 확인해 주세요"
        private const val MIC_LOST = "녹음을 이어 갈 수 없어 멈췄어요. 여기까지 녹음한 내용은 남아 있어요"
        /** Moving further than this between starting and saving triggers the question. */
        private const val MOVED_METERS = 300f
    }
}
