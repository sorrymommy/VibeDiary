package com.vibediary.app.ui.detail

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.CameraPosition
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.vibediary.app.container
import com.vibediary.app.data.AnalysisState
import com.vibediary.app.data.Entry
import com.vibediary.app.ui.Vibe
import com.vibediary.app.ui.components.EntryPhoto
import com.vibediary.app.ui.components.TagChip
import com.vibediary.app.ui.components.clock
import com.vibediary.app.ui.components.stationName
import com.vibediary.app.ui.components.autoStationName
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.IconButton
import com.vibediary.app.ui.map.latLng
import androidx.compose.runtime.key
import com.vibediary.app.ui.map.LocationPickerDialog
import com.vibediary.app.ui.map.rememberMapBlock
import com.vibediary.app.capture.Place
import com.google.android.gms.maps.model.LatLng
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material3.OutlinedButton
import com.vibediary.app.ui.newentry.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("M월 d일 EEEE", Locale.KOREA)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryDetailScreen(entryId: String, onBack: () -> Unit) {
    val repo = LocalContext.current.container.repository
    val scope = rememberCoroutineScope()
    val entry by remember(entryId) { repo.observe(entryId) }.collectAsStateWithLifecycle(initialValue = null)
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val mapBlock = rememberMapBlock()

    val current = entry
    if (current == null || current.deleted) {
        Box(Modifier.fillMaxSize().background(Vibe.Paper))
        return
    }

    var noteDraft by rememberSaveable(editing) { mutableStateOf(current.note) }
    var transcriptDraft by rememberSaveable(editing) { mutableStateOf(current.transcript.orEmpty()) }
    var tagsDraft by rememberSaveable(editing, saver = TagsStateSaver) { mutableStateOf(current.tags) }
    // What editing started from, to tell the user's changes apart from analysis results
    // that arrive while the editor is open. Saveable like the drafts, so a rotation can't
    // pair old drafts with a newer starting point.
    val transcriptAtEditStart by rememberSaveable(editing) { mutableStateOf(current.transcript) }
    val nameAtEditStart by rememberSaveable(editing) { mutableStateOf(current.stationName) }
    val tagsAtEditStart by rememberSaveable(editing, saver = TagsStateSaver) { mutableStateOf(current.tags) }
    var newTag by rememberSaveable(editing) { mutableStateOf("") }
    // A spot picked on the map while editing; applied on 저장.
    var placeDraft by rememberSaveable(editing, saver = PlaceDraftSaver) { mutableStateOf(null) }
    var picking by remember { mutableStateOf(false) }
    // The place name shown on the line; starts as whatever is shown now (typed or automatic).
    var nameDraft by rememberSaveable(editing) { mutableStateOf(current.stationName) }
    val moved = placeDraft?.let { (pos, place) ->
        current.copy(lat = pos.latitude, lng = pos.longitude, placeName = place?.name)
    } ?: current
    // Drafts are only a preview while editing. Outside edit mode always show the saved entry:
    // right after 저장 the drafts still hold the pre-edit values for a frame, and showing
    // (or re-saving) them would undo the rename.
    val shown = if (editing) {
        moved.copy(stationLabel = nameDraft.trim().takeIf { it.isNotEmpty() && it != moved.autoStationName })
    } else {
        current
    }

    /**
     * Applies only what the user changed to the entry as it is now. The analysis may have
     * finished while editing (text, tags, place name, the compressed recording); writing the
     * screen's snapshot back would undo that.
     */
    fun saveEdits() {
        scope.launch {
            val latest = repo.get(current.id)?.takeIf { !it.deleted } ?: return@launch
            val located = placeDraft?.let { (pos, place) ->
                latest.copy(lat = pos.latitude, lng = pos.longitude, placeName = place?.name)
            } ?: latest
            val name = nameDraft.trim()
            val label = if (name != nameAtEditStart) {
                name.takeIf { it.isNotEmpty() && it != located.autoStationName }
            } else {
                located.stationLabel
            }
            val edited = located.copy(stationLabel = label)
            // Tags the analysis added while editing join the user's list. Any place tag, the
            // one shown when editing started or the one the analysis derived, follows the name;
            // a stand-in like "이름 없는 곳" is no place, so it isn't kept as a tag.
            val placeTags = setOf(nameAtEditStart, latest.autoStationName)
            val newPlaceTag = edited.stationName.takeIf { edited.stationLabel != null || !edited.placeName.isNullOrBlank() }
            val carriedTags = (tagsDraft + (latest.tags - tagsAtEditStart.toSet()))
                .mapNotNull { if (it in placeTags) newPlaceTag else it }
            val typedTag = newTag.trim().removePrefix("#")
            val tags = (carriedTags + typedTag).filter { it.isNotBlank() }.distinct()
            val transcript = transcriptDraft.trim().ifEmpty { null }
                ?: if (transcriptAtEditStart == null) latest.transcript else ""
            repo.update(edited.copy(note = noteDraft.trim(), transcript = transcript, tags = tags))
            editing = false
        }
    }

    Box(Modifier.fillMaxSize().background(Vibe.Paper)) {
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            if (current.photoFile != null) {
                EntryPhoto(current.photoFile, Modifier.fillMaxWidth().height(320.dp))
            } else {
                Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars).height(88.dp))
            }

            Column(
                Modifier.padding(horizontal = 24.dp).offset(y = if (current.photoFile != null) (-28).dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                StationBand(shown)
                if (editing) {
                    InkField(
                        nameDraft, { nameDraft = it }, "장소 이름 (비우면 ${moved.autoStationName})",
                        singleLine = true,
                        clearable = true,
                    )
                    OutlinedButton(
                        onClick = { picking = true },
                        enabled = mapBlock == null,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(2.dp, if (mapBlock == null) Vibe.Ink else Vibe.Hairline),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Vibe.Ink),
                    ) {
                        Icon(Icons.Outlined.EditLocationAlt, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (mapBlock == null) "지도에서 위치 바꾸기" else mapBlock.message,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }

                if (editing) {
                    InkField(noteDraft, { noteDraft = it }, "메모", minLines = 4)
                } else if (current.note.isNotBlank()) {
                    Text(current.note, style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize * 1.12f, lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.15f))
                }

                current.audioFile?.let { audio ->
                    Column(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.fillMaxWidth().height(2.dp).background(Vibe.Ink))
                        // Keyed so play state resets when analysis swaps in the compressed file.
                        key(audio) { AudioPlayer(repo.mediaFile(audio), current.audioDurationMs) }
                        if (editing) {
                            InkField(transcriptDraft, { transcriptDraft = it }, "말로 남긴 내용", minLines = 2)
                        } else {
                            Text(
                                when (current.analysis) {
                                    AnalysisState.PENDING -> "말한 내용을 글로 옮기는 중이에요"
                                    AnalysisState.FAILED -> current.analysisMessage ?: "글로 옮기지 못했어요. 아래 '다시 분석'을 눌러 보세요"
                                    AnalysisState.DONE ->
                                        if (current.transcript.isNullOrBlank()) "알아들을 수 있는 말이 없었어요"
                                        else "말로 남긴 내용: ${current.transcript}"
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.15f),
                                color = Vibe.Body,
                            )
                        }
                    }
                }

                if (editing) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tagsDraft.forEach { tag ->
                            InputChip(
                                selected = false,
                                onClick = { tagsDraft = tagsDraft - tag },
                                label = { Text(tag, style = MaterialTheme.typography.labelMedium) },
                                trailingIcon = { Icon(Icons.Filled.Close, "$tag 태그 빼기", Modifier.size(16.dp)) },
                            )
                        }
                    }
                    InkField(newTag, { newTag = it }, "태그 추가", singleLine = true)
                } else {
                    if (current.tags.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            current.tags.forEach { TagChip(it) }
                        }
                    }
                    if (current.analysis != AnalysisState.PENDING) {
                        TextButton(onClick = { scope.launch { repo.reanalyze(current) } }) {
                            Icon(Icons.Outlined.AutoAwesome, null, tint = Vibe.Route)
                            Spacer(Modifier.width(6.dp))
                            Text("다시 분석", style = MaterialTheme.typography.labelMedium, color = Vibe.Ink)
                        }
                    }
                }

                shown.latLng?.takeIf { mapBlock == null }?.let { pos -> key(pos) {
                    // Keyed by position so the preview re-centres after the place is moved.
                    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(pos, 15f) }
                    GoogleMap(
                        modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)),
                        cameraPositionState = camera,
                        uiSettings = MapUiSettings(zoomControlsEnabled = false, scrollGesturesEnabled = false, mapToolbarEnabled = false),
                    ) {
                        MarkerComposable(
                            state = remember(pos) { MarkerState(position = pos) },
                            anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                        ) {
                            Box(Modifier.size(24.dp).clip(CircleShape).background(Vibe.Ink).border(4.dp, Color.White, CircleShape))
                        }
                    }
                } }
                Spacer(Modifier.height(16.dp))
            }
        }

        // Floating actions over the photo.
        Row(
            Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
        ) {
            WhiteRoundButton(if (editing) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack, if (editing) "편집 취소" else "노선으로 돌아가기") {
                if (editing) editing = false else onBack()
            }
            Spacer(Modifier.weight(1f))
            if (editing) {
                Button(
                    onClick = ::saveEdits,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Vibe.Ink, contentColor = Color.White),
                ) { Text("저장", style = MaterialTheme.typography.labelLarge) }
            } else {
                WhiteRoundButton(Icons.Outlined.Edit, "편집") { editing = true }
                Spacer(Modifier.width(8.dp))
                WhiteRoundButton(Icons.Outlined.Delete, "삭제") { confirmDelete = true }
            }
        }
    }

    if (picking) {
        LocationPickerDialog(
            initial = shown.latLng,
            onPick = { pos, place ->
                // Swap the old place tag for the new one so tags follow the move.
                tagsDraft = (tagsDraft.filterNot { it == shown.stationName } + listOfNotNull(place?.tag)).distinct()
                // An untouched automatic name follows the move; a name the user typed stays.
                if (nameDraft.trim() == moved.autoStationName) {
                    nameDraft = place?.name?.split(" ")?.last().orEmpty()
                }
                placeDraft = pos to place
                picking = false
            },
            onDismiss = { picking = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("이 순간을 노선에서 지울까요?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("사진과 녹음도 함께 지워지고, 구글 드라이브에서도 사라져요.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        repo.delete(current.id)
                        onBack()
                    }
                }) { Text("지우기", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("그대로 두기", color = Vibe.Ink, style = MaterialTheme.typography.labelLarge) }
            },
            containerColor = Color.White,
        )
    }
}

// Not listSaver: it saves nothing for an empty list, so removing the last tag would be undone.
private val TagsStateSaver = Saver<MutableState<List<String>>, ArrayList<String>>(
    save = { ArrayList(it.value) },
    restore = { mutableStateOf(it) },
)

/** A picked spot as [lat, lng, place name, place tag]; nothing is saved when none was picked. */
private val PlaceDraftSaver = listSaver<MutableState<Pair<LatLng, Place?>?>, Any?>(
    save = { state ->
        state.value?.let { (pos, place) -> listOf(pos.latitude, pos.longitude, place?.name, place?.tag) }.orEmpty()
    },
    restore = { saved ->
        val draft = if (saved.isEmpty()) null else {
            LatLng(saved[0] as Double, saved[1] as Double) to (saved[2] as String?)?.let { Place(it, saved[3] as String?) }
        }
        mutableStateOf(draft)
    },
)

/** Ink "station sign" band: place on the left, time on the right. */
@Composable
private fun StationBand(entry: Entry) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Vibe.Ink).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.stationName, style = MaterialTheme.typography.headlineMedium, color = Color.White)
            val sub = when {
                entry.placeName == null -> if (entry.hasLocation) "주소를 찾지 못했어요" else "위치 없이 남겼어요"
                entry.placeName.trim() == entry.stationName -> null // "역삼동 / 역삼동에서" says nothing new
                else -> "${entry.placeName}에서"
            }
            sub?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Color(0xFFC6CEC9)) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(entry.createdAt.clock(), style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(dateFormat.format(Date(entry.createdAt)), style = MaterialTheme.typography.labelMedium, color = Color(0xFFC6CEC9))
        }
    }
}

@Composable
private fun WhiteRoundButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Vibe.Ink),
    ) { Icon(icon, description) }
}

@Composable
private fun InkField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    minLines: Int = 1,
    singleLine: Boolean = false,
    clearable: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        trailingIcon = if (clearable && value.isNotEmpty()) {
            { IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Cancel, "지우기", tint = Vibe.Muted) } }
        } else null,
        textStyle = MaterialTheme.typography.bodyLarge,
        minLines = minLines,
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Vibe.Ink, unfocusedBorderColor = Vibe.Muted,
            focusedLabelColor = Vibe.Ink, cursorColor = Vibe.Route,
            focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
        ),
    )
}

@Composable
private fun AudioPlayer(file: File, durationMs: Long) {
    var playing by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    val player = remember(file) { MediaPlayer() }
    DisposableEffect(player) {
        runCatching {
            player.setDataSource(file.absolutePath)
            player.prepare()
        }
        player.setOnCompletionListener { playing = false; positionMs = 0 }
        onDispose { player.release() }
    }
    LaunchedEffect(playing) {
        while (playing) {
            positionMs = player.currentPosition.toLong()
            delay(200)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledIconButton(
            onClick = {
                if (playing) player.pause() else runCatching { player.start() }
                playing = player.isPlaying
            },
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Vibe.Ink, contentColor = Color.White),
        ) {
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "일시정지" else "음성 메모 재생")
        }
        Spacer(Modifier.width(12.dp))
        LinearProgressIndicator(
            progress = { if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
            color = Vibe.Ink,
            trackColor = Color(0xFFCDD3CF),
            drawStopIndicator = {},
            gapSize = 0.dp,
        )
        Spacer(Modifier.width(12.dp))
        Text("${formatDuration(positionMs)} / ${formatDuration(durationMs)}", style = MaterialTheme.typography.labelMedium, color = Vibe.Muted)
    }
}
