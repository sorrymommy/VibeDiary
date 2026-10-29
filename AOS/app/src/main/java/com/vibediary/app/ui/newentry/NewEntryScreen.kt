package com.vibediary.app.ui.newentry

import android.Manifest
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.CircularProgressIndicator
import com.vibediary.app.ai.Transcriber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import com.vibediary.app.capture.Place
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.maps.model.LatLng
import com.vibediary.app.ui.map.LocationPickerDialog
import com.vibediary.app.ui.map.rememberMapBlock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vibediary.app.ui.Vibe
import com.vibediary.app.ui.components.CaptureMode
import com.vibediary.app.ui.components.EntryPhoto
import com.vibediary.app.ui.components.StationDot

@Composable
fun NewEntryScreen(start: CaptureMode, onClose: () -> Unit, vm: NewEntryViewModel = viewModel()) {
    val focus = remember { FocusRequester() }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startRecording()
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { vm.onPhotoTaken(it) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { vm.onPhotoPicked(it) }

    // What the dock button asked for. Runs once, after the location prompt (two system
    // prompts at once would swallow one of them).
    fun handleStart() {
        if (vm.startHandled) return
        vm.startHandled = true
        when (start) {
            CaptureMode.PHOTO -> takePicture.launch(vm.newPhotoUri())
            CaptureMode.VOICE -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
            CaptureMode.MEMO -> runCatching { focus.requestFocus() }
        }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.fetchLocation()
        handleStart()
    }

    LaunchedEffect(Unit) {
        if (vm.locationEnabled) {
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            handleStart()
        }
    }

    val context = LocalContext.current
    LaunchedEffect(vm.recordingError) {
        vm.recordingError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.clearRecordingError()
        }
    }

    Column(
        Modifier.fillMaxSize().background(Vibe.Paper)
            .windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).imePadding(),
    ) {
        // Top bar
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Close, "기록 취소") }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { vm.save(onClose) },
                enabled = vm.canSave && !vm.saving,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Vibe.Ink, contentColor = Color.White,
                    disabledContainerColor = Color(0xFFCDD3CF), disabledContentColor = Color(0xFF4A544E),
                ),
            ) { Text(if (vm.saving) "위치 확인 중" else "노선에 추가", style = MaterialTheme.typography.labelLarge) }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PlaceLine(vm)

            vm.photoFile?.let { photo ->
                Box {
                    EntryPhoto(photo, Modifier.fillMaxWidth().heightIn(max = 260.dp).height(220.dp).clip(RoundedCornerShape(12.dp)))
                    FilledIconButton(
                        onClick = vm::removePhoto,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Vibe.Ink),
                    ) { Icon(Icons.Filled.Close, "사진 빼기") }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("메모", style = MaterialTheme.typography.labelMedium, color = Vibe.Muted)
                BasicTextField(
                    value = vm.note,
                    onValueChange = { vm.note = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize * 1.06f, color = Vibe.Ink),
                    cursorBrush = SolidColor(Vibe.Route),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).focusRequester(focus)
                        .semantics { contentDescription = "메모" }
                        .drawBehind {
                            val y = size.height - 1.dp.toPx()
                            drawLine(Vibe.Ink, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
                        },
                    decorationBox = { inner ->
                        Box(Modifier.padding(bottom = 10.dp)) {
                            if (vm.note.isEmpty()) {
                                Text("떠오른 생각을 적어 두세요. #태그도 돼요", style = MaterialTheme.typography.bodyLarge, color = Vibe.Muted)
                            }
                            inner()
                        }
                    },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InkOutlinedButton(Icons.Outlined.PhotoCamera, "사진 찍기") { takePicture.launch(vm.newPhotoUri()) }
                InkOutlinedButton(Icons.Outlined.Image, "앨범에서") {
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        RecordingPanel(
            vm = vm,
            onStart = { micPermission.launch(Manifest.permission.RECORD_AUDIO) },
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 16.dp),
        )
    }

    vm.moveChoice?.let { choice ->
        MoveChoiceDialog(
            choice = choice,
            onStart = { vm.saveAt(useNow = false, onSaved = onClose) },
            onNow = { vm.saveAt(useNow = true, onSaved = onClose) },
            onDismiss = vm::dismissMoveChoice,
        )
    }
}

/** Asked on 저장 when the user moved a fair way since starting to write. */
@Composable
private fun MoveChoiceDialog(
    choice: NewEntryViewModel.MoveChoice,
    onStart: () -> Unit,
    onNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val distance = if (choice.meters < 1000) "약 ${(choice.meters / 10).toInt() * 10}m"
    else "약 ${"%.1f".format(choice.meters / 1000)}km"
    val ago = if (choice.minutesAgo < 1) "방금 전" else "${choice.minutesAgo}분 전"
    fun Place?.short(fallback: String) = this?.name?.split(" ")?.last() ?: fallback

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("그새 다른 곳으로 옮겨 왔네요", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "쓰기 시작한 곳과 지금 있는 곳이 $distance 떨어져 있어요. 이 순간을 어디에 남길까요?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                PlaceChoice("쓰기 시작한 곳", choice.startPlace.short("이름 없는 곳"), ago, current = false, onClick = onStart)
                PlaceChoice("지금 있는 곳", choice.nowPlace.short("이름 없는 곳"), "지금", current = true, onClick = onNow)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("계속 쓰기", style = MaterialTheme.typography.labelLarge, color = Vibe.Ink) }
        },
    )
}

@Composable
private fun PlaceChoice(label: String, place: String, whenText: String, current: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Vibe.Paper,
        border = androidx.compose.foundation.BorderStroke(2.dp, Vibe.Ink),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            StationDot(current = current)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = Vibe.Muted)
                Text(place, style = MaterialTheme.typography.titleLarge)
            }
            Text(whenText, style = MaterialTheme.typography.labelMedium, color = Vibe.Muted)
        }
    }
}

/**
 * Where this moment goes on the line. The name is editable in place (the automatic 동/구
 * name is just the starting text); the pin button picks the spot on the map instead of GPS.
 */
@Composable
private fun PlaceLine(vm: NewEntryViewModel) {
    val canPick = rememberMapBlock() == null
    var picking by remember { mutableStateOf(false) }
    val auto = vm.autoPlaceName
    val status = when {
        auto != null -> auto
        vm.locating -> "어디인지 찾는 중이에요"
        vm.location != null -> "이름 없는 곳"
        else -> "어딘가"
    }
    var field by remember { mutableStateOf(TextFieldValue(vm.placeLabel.orEmpty())) }
    val nameStyle = MaterialTheme.typography.headlineMedium

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
        StationDot(current = false)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            BasicTextField(
                value = field,
                onValueChange = { field = it; vm.placeLabel = it.text },
                singleLine = true,
                textStyle = nameStyle.copy(color = Vibe.Ink),
                cursorBrush = SolidColor(Vibe.Route),
                modifier = Modifier.fillMaxWidth()
                    .semantics { contentDescription = "장소 이름, 눌러서 바꾸기" }
                    .onFocusChanged { f ->
                        // First tap on the automatic name: start from it, all selected, so typing replaces it.
                        if (f.isFocused && vm.placeLabel == null && auto != null) {
                            field = TextFieldValue(auto, TextRange(0, auto.length))
                            vm.placeLabel = auto
                        }
                    },
                decorationBox = { inner ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            if (field.text.isEmpty()) Text(status, style = nameStyle, color = if (vm.placeLabel == "") Vibe.Muted else Vibe.Ink)
                            inner()
                        }
                        // One tap empties the name to type a new one (empty = automatic name).
                        if (field.text.isNotEmpty()) {
                            IconButton(onClick = { field = TextFieldValue(""); vm.placeLabel = "" }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Filled.Cancel, "장소 이름 지우기", tint = Vibe.Muted)
                            }
                        }
                    }
                },
            )
            Text(
                when {
                    !vm.placeLabel.isNullOrBlank() && vm.placeLabel != auto -> "내가 정한 이름으로 남겨요"
                    vm.placeLabel == "" && auto != null -> "비워 두면 '$auto'(으)로 남겨요"
                    vm.manualLocation -> "지도에서 고른 위치로 남겨요. 이름을 눌러 바꿀 수 있어요"
                    vm.location != null -> "이름을 눌러 바꿀 수 있어요"
                    vm.locating -> "잠시만요"
                    !vm.locationEnabled -> "설정에서 위치 남기기를 꺼 두었어요"
                    else -> "위치 권한을 허용하면 노선에 표시돼요"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Vibe.Muted,
            )
        }
        if (canPick) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color.White).border(2.dp, Vibe.Ink, CircleShape)
                    .clickable(onClickLabel = "지도에서 위치 고르기") { picking = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.EditLocationAlt, "지도에서 위치 고르기", tint = Vibe.Ink, modifier = Modifier.size(22.dp)) }
        }
    }

    if (picking) {
        LocationPickerDialog(
            initial = vm.location?.let { LatLng(it.latitude, it.longitude) },
            onPick = { pos, place ->
                vm.setManualLocation(pos.latitude, pos.longitude, place)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun InkOutlinedButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, Vibe.Ink),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Vibe.Ink),
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** Pause and "다 했어요" share one height so they read as a pair. */
private val RECORD_CONTROL_HEIGHT = 56.dp

@Composable
private fun RecordingPanel(vm: NewEntryViewModel, onStart: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    when {
        vm.recording -> Column(
            modifier.fillMaxWidth().clip(shape).background(Vibe.Ink).padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                // A softly breathing route-green dot says "on air"; it holds still while paused.
                val pulse by rememberInfiniteTransition(label = "rec").animateFloat(
                    initialValue = 1f, targetValue = 0.35f,
                    animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "recDot",
                )
                Box(
                    Modifier.align(Alignment.CenterVertically).size(12.dp)
                        .graphicsLayer { alpha = if (vm.paused) 0.35f else pulse }
                        .clip(CircleShape).background(if (vm.paused) Vibe.Muted else Vibe.Route)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (vm.paused) "잠시 멈췄어요" else "듣고 있어요",
                    style = MaterialTheme.typography.titleMedium, color = Vibe.Leaf, modifier = Modifier.weight(1f),
                )
                Text(
                    formatDuration(vm.recordedMs),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = MaterialTheme.typography.headlineLarge.fontSize * 1.25f),
                    color = Color.White,
                )
            }
            Waveform(vm.levels, Modifier.fillMaxWidth().height(48.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(
                    onClick = vm::togglePause,
                    enabled = !vm.stopping,
                    modifier = Modifier.size(RECORD_CONTROL_HEIGHT),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Vibe.Paper, contentColor = Vibe.Ink),
                ) {
                    if (vm.paused) Icon(Icons.Outlined.Mic, "이어서 녹음")
                    else Icon(Icons.Filled.Pause, "잠시 멈추기")
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    when {
                        Transcriber.isTooLong(vm.recordedMs) -> "${Transcriber.MAX_CLIP_MINUTES}분이 넘어 글로는 옮기지 않아요. 녹음은 남아요"
                        vm.paused -> "다시 누르면 이어서 녹음해요"
                        else -> "잠시 멈췄다 이어서 말할 수 있어요"
                    },
                    style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = vm::stopRecording,
                    enabled = !vm.stopping,
                    modifier = Modifier.height(RECORD_CONTROL_HEIGHT),
                    shape = RoundedCornerShape(RECORD_CONTROL_HEIGHT / 2),
                    colors = ButtonDefaults.buttonColors(containerColor = Vibe.Route, contentColor = Color.White),
                ) {
                    Icon(Icons.Filled.Stop, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("다 했어요", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        vm.audioFile != null -> Column(
            modifier.fillMaxWidth().clip(shape).background(Vibe.Leaf).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Tapping the mic adds more to the end of this recording.
                FilledIconButton(
                    onClick = vm::continueRecording,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Vibe.LeafInk),
                ) { Icon(Icons.Outlined.Mic, "이어서 녹음") }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("음성 ${formatDuration(vm.audioDurationMs)} 남겼어요", style = MaterialTheme.typography.titleMedium, color = Vibe.LeafInk)
                    Text("마이크를 누르면 이어서 녹음해요", style = MaterialTheme.typography.bodySmall, color = Vibe.LeafInk)
                }
                IconButton(onClick = vm::removeAudio) { Icon(Icons.Filled.Close, "녹음 지우기", tint = Vibe.LeafInk) }
            }
            TranscriptPreview(vm)
        }

        else -> Row(
            modifier.fillMaxWidth().clip(shape).background(Color.White).border(2.dp, Vibe.Ink, shape).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                onClick = onStart,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Vibe.Route, contentColor = Color.White),
            ) { Icon(Icons.Outlined.Mic, "녹음 시작") }
            Spacer(Modifier.width(14.dp))
            Text("눌러서 말로 남기기", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** What the recording says, read on-device as soon as recording stops (before 저장). */
@Composable
private fun TranscriptPreview(vm: NewEntryViewModel) {
    val text = vm.transcript
    val problem = vm.transcriptProblem
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("말한 내용", style = MaterialTheme.typography.labelMedium, color = Vibe.LeafInk)
        when {
            vm.transcribing -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Vibe.Route)
                Spacer(Modifier.width(8.dp))
                Text("글로 옮기는 중이에요. 녹음 길이의 절반쯤 걸려요", style = MaterialTheme.typography.bodyMedium, color = Vibe.Muted)
            }
            problem != null -> Text(
                when (problem) {
                    Transcriber.Problem.UNSUPPORTED ->
                        "이 휴대폰에서는 말을 글로 옮길 수 없어요. 녹음은 그대로 남아요."
                    Transcriber.Problem.LANGUAGE_PACK ->
                        "한국어 음성인식 언어팩이 아직 없어요. 지금 받고 있으니, 저장해 두면 언어팩이 준비된 뒤 기록 화면의 '다시 분석'으로 글로 옮길 수 있어요."
                    Transcriber.Problem.BUSY, Transcriber.Problem.TEMPORARY ->
                        "지금은 글로 옮기지 못했어요. 저장하면 다시 옮겨 볼게요."
                    Transcriber.Problem.TOO_LONG ->
                        "${Transcriber.TOO_LONG_REASON}. 녹음은 그대로 남아요."
                },
                style = MaterialTheme.typography.bodyMedium, color = Vibe.Muted,
            )
            text.isNullOrBlank() -> Text("알아들을 수 있는 말이 없었어요", style = MaterialTheme.typography.bodyMedium, color = Vibe.Muted)
            else -> Text(text, style = MaterialTheme.typography.bodyLarge, color = Vibe.Ink)
        }
    }
}

@Composable
private fun Waveform(levels: List<Float>, modifier: Modifier = Modifier) {
    Box(modifier.drawBehind {
        val bars = NewEntryViewModel.WAVE_BARS
        val gap = 6.dp.toPx()
        val w = (size.width - gap * (bars - 1)) / bars
        val pad = bars - levels.size
        for (i in 0 until bars) {
            val level = if (i < pad) 0f else levels[i - pad]
            val h = (size.height * (0.12f + 0.88f * kotlin.math.sqrt(level))).coerceAtMost(size.height)
            drawRoundRect(
                color = if (i < pad) Vibe.Leaf.copy(alpha = 0.3f) else Vibe.Leaf,
                topLeft = Offset(i * (w + gap), (size.height - h) / 2),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2),
            )
        }
    })
}

fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
