package com.vibediary.app.ui.settings

import android.Manifest
import android.app.Activity
import com.vibediary.app.capture.MediaExport
import com.vibediary.app.ui.lock.PinAction
import com.vibediary.app.ui.lock.PinSetupDialog
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import android.os.Build
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.vibediary.app.container
import com.vibediary.app.sync.DriveAuth
import com.vibediary.app.sync.SyncScheduler
import com.vibediary.app.ui.Vibe
import com.vibediary.app.ui.components.TagChip
import com.vibediary.app.ui.components.clock
import com.vibediary.app.ui.home.dayKey
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.container.prefs
    val scope = rememberCoroutineScope()

    var connected by remember { mutableStateOf(prefs.syncEnabled) }
    var autoSync by remember { mutableStateOf(prefs.autoSync) }
    var pullHours by remember { mutableIntStateOf(prefs.pullHours) }
    var wifiOnly by remember { mutableStateOf(prefs.wifiOnly) }
    var uploadMedia by remember { mutableStateOf(prefs.uploadMedia) }
    var saveLocation by remember { mutableStateOf(prefs.saveLocation) }
    var exportToGallery by remember { mutableStateOf(prefs.exportToGallery) }
    val lock = context.container.lock
    var lockEnabled by remember { mutableStateOf(lock.isEnabled) }
    var pinAction by remember { mutableStateOf<PinAction?>(null) }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        exportToGallery = granted; prefs.exportToGallery = granted
        if (!granted) Toast.makeText(context, "저장 권한이 있어야 휴대폰에 사본을 남길 수 있어요", Toast.LENGTH_SHORT).show()
    }
    var confirmDisconnect by remember { mutableStateOf(false) }

    val infos by remember { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(SyncScheduler.NOW) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val running = infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }

    fun onConnected() {
        prefs.syncEnabled = true
        connected = true
        SyncScheduler.enablePeriodic(context, prefs, replace = true)
        SyncScheduler.syncNow(context)
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) onConnected()
        else Toast.makeText(context, "구글 계정 연결을 취소했어요", Toast.LENGTH_SHORT).show()
    }

    fun connect() = scope.launch {
        runCatching { DriveAuth.authorize(context) }
            .onSuccess { r ->
                val pending = r.pendingIntent
                if (r.hasResolution() && pending != null) {
                    consent.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else onConnected()
            }
            .onFailure {
                Toast.makeText(context, "연결하지 못했어요. 인터넷 연결과 구글 계정을 확인해 주세요", Toast.LENGTH_LONG).show()
            }
    }

    fun reschedule() {
        if (prefs.syncEnabled) SyncScheduler.enablePeriodic(context, prefs, replace = true)
    }

    Column(
        Modifier.fillMaxSize().background(Vibe.Paper)
            .windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "노선으로 돌아가기")
            }
            Text("설정", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DriveCard(connected, running, onConnect = { connect() }, onSyncNow = { SyncScheduler.syncNow(context) })

            Group {
                SwitchRow("자동으로 맞추기", "새 기록을 남기면 알아서 드라이브에 올려요", autoSync, enabled = connected) {
                    autoSync = it; prefs.autoSync = it
                }
                HorizontalDivider(color = Vibe.Hairline)
                Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("다른 기기 기록 받아오기", style = MaterialTheme.typography.titleMedium, color = if (connected) Vibe.Ink else Vibe.Muted)
                    Segmented(
                        options = listOf(1 to "1시간마다", 6 to "6시간마다", 24 to "하루 한 번"),
                        selected = pullHours,
                        enabled = connected,
                    ) { pullHours = it; prefs.pullHours = it; reschedule() }
                }
                HorizontalDivider(color = Vibe.Hairline)
                SwitchRow("와이파이에서만", "모바일 데이터를 아끼고 싶을 때", wifiOnly, enabled = connected) {
                    wifiOnly = it; prefs.wifiOnly = it; reschedule()
                }
                HorizontalDivider(color = Vibe.Hairline)
                SwitchRow("사진과 녹음도 올리기", "끄면 글과 태그만 보관해요", uploadMedia, enabled = connected) {
                    uploadMedia = it; prefs.uploadMedia = it
                }
            }

            Group {
                SwitchRow("비밀번호로 잠그기", "앱을 켤 때 숫자 6자리를 물어요", lockEnabled) { on ->
                    pinAction = if (on) PinAction.SET else PinAction.DISABLE
                }
                if (lockEnabled) {
                    HorizontalDivider(color = Vibe.Hairline)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { pinAction = PinAction.CHANGE },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("비밀번호 바꾸기", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Vibe.Muted)
                    }
                }
            }

            Group {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        Text("한국어 음성 인식", style = MaterialTheme.typography.titleMedium)
                        Text(voiceStatus(context).second, style = MaterialTheme.typography.bodySmall, color = Vibe.Muted)
                    }
                    TagChip(voiceStatus(context).first)
                }
                HorizontalDivider(color = Vibe.Hairline)
                SwitchRow("기록할 때 위치 남기기", "노선과 지도에 역으로 표시돼요", saveLocation) {
                    saveLocation = it; prefs.saveLocation = it
                }
                HorizontalDivider(color = Vibe.Hairline)
                SwitchRow(
                    "사진과 녹음을 휴대폰에도 남기기",
                    "새로 남기는 사진은 갤러리의 VibeDiary 앨범에, 녹음은 녹음 폴더에 사본을 저장해요",
                    exportToGallery,
                ) { on ->
                    if (on && MediaExport.needsPermission) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    else { exportToGallery = on; prefs.exportToGallery = on }
                }
            }

            if (connected) {
                TextButton(
                    onClick = { confirmDisconnect = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally).height(48.dp),
                ) { Text("드라이브 연결 끊기", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    pinAction?.let { action ->
        PinSetupDialog(action) { changed ->
            pinAction = null
            lockEnabled = lock.isEnabled
            if (changed) {
                Toast.makeText(
                    context,
                    when (action) {
                        PinAction.SET -> "비밀번호를 정했어요. 다음에 앱을 켤 때부터 물어요"
                        PinAction.CHANGE -> "비밀번호를 바꿨어요"
                        PinAction.DISABLE -> "비밀번호 잠금을 껐어요"
                    },
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("드라이브 연결을 끊을까요?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("휴대폰의 기록은 그대로 있어요. 드라이브에 이미 올린 기록도 지워지지 않고, 다시 연결하면 이어서 맞춰요.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    prefs.syncEnabled = false
                    connected = false
                    SyncScheduler.disable(context)
                }) { Text("연결 끊기", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisconnect = false }) { Text("그대로 두기", color = Vibe.Ink, style = MaterialTheme.typography.labelLarge) }
            },
            containerColor = Color.White,
        )
    }
}

@Composable
private fun DriveCard(connected: Boolean, running: Boolean, onConnect: () -> Unit, onSyncNow: () -> Unit) {
    val prefs = LocalContext.current.container.prefs
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Color.White).border(2.dp, Vibe.Ink, shape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(if (connected) Vibe.Leaf else Vibe.Paper), contentAlignment = Alignment.Center) {
                Icon(if (connected) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff, null, tint = if (connected) Vibe.LeafInk else Vibe.Muted)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                if (connected) "구글 드라이브에 보관 중" else "구글 드라이브에 보관하기",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(
            when {
                !connected -> "기록과 사진, 녹음을 내 드라이브의 앱 전용 숨김 공간에 보관해요. 따로 서버를 쓰지 않아 내 계정에만 남아요."
                running -> "드라이브와 맞추는 중이에요."
                prefs.lastSyncError != null -> "마지막으로 맞추지 못했어요: ${prefs.lastSyncError}"
                prefs.lastSyncAt > 0 -> "${syncLabel(prefs.lastSyncAt)}에 마지막으로 맞췄어요. 드라이브의 앱 전용 숨김 공간에 있어서 내 파일 목록에는 보이지 않아요."
                else -> "곧 처음으로 맞춰요."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Vibe.Body,
        )
        Button(
            onClick = if (connected) onSyncNow else onConnect,
            enabled = !(connected && running),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Vibe.Ink, contentColor = Color.White),
        ) {
            if (connected) {
                Icon(Icons.Outlined.Sync, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                when {
                    !connected -> "구글 계정 연결하기"
                    running -> "맞추는 중"
                    else -> "지금 맞추기"
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private fun syncLabel(at: Long): String =
    if (at.dayKey() == System.currentTimeMillis().dayKey()) "오늘 ${at.clock()}"
    else SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA).format(Date(at))

private fun voiceStatus(context: android.content.Context): Pair<String, String> = when {
    Build.VERSION.SDK_INT < 33 -> "지원 안 함" to "Android 13 이상에서 녹음을 글로 옮길 수 있어요"
    SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ->
        "기기 안" to "녹음은 휴대폰 밖으로 나가지 않아요. 한국어 언어팩이 없으면 처음 옮길 때 받아요"
    SpeechRecognizer.isRecognitionAvailable(context) -> "기본 엔진" to "기기의 기본 음성 인식을 써요"
    else -> "없음" to "이 기기에 음성 인식 엔진이 없어요"
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(horizontal = 18.dp, vertical = 4.dp)) {
        content()
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) Vibe.Ink else Vibe.Muted)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Vibe.Muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Vibe.Route, checkedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFCDD3CF), uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun Segmented(options: List<Pair<Int, String>>, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Vibe.Paper).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isSel = value == selected
            Box(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (isSel) Vibe.Ink else Color.Transparent)
                    .selectable(selected = isSel, enabled = enabled, role = Role.RadioButton) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        isSel -> Color.White
                        enabled -> Vibe.Body
                        else -> Vibe.Muted
                    },
                )
            }
        }
    }
}
