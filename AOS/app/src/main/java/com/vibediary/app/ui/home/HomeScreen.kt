package com.vibediary.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.vibediary.app.container
import com.vibediary.app.data.AnalysisState
import com.vibediary.app.data.Entry
import com.vibediary.app.sync.SyncScheduler
import com.vibediary.app.ui.Vibe
import com.vibediary.app.ui.components.CaptureDock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.vibediary.app.ui.components.CaptureMode
import com.vibediary.app.ui.components.EntryPhoto
import com.vibediary.app.ui.components.RoundButton
import com.vibediary.app.ui.components.StationDot
import com.vibediary.app.ui.components.TagChip
import com.vibediary.app.ui.components.clock
import com.vibediary.app.ui.components.stationName
import com.vibediary.app.ui.map.MapBlock
import com.vibediary.app.ui.map.rememberMapBlock
import com.vibediary.app.ui.newentry.formatDuration
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val dayFormat = SimpleDateFormat("M월 d일 EEEE", Locale.KOREA)
private val koreanCounts = listOf("", "한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열")

fun Long.dayKey(): Long = Calendar.getInstance().run {
    timeInMillis = this@dayKey
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    timeInMillis
}

@Composable
fun HomeScreen(
    onCapture: (CaptureMode) -> Unit,
    onOpenEntry: (String) -> Unit,
    onOpenMap: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val repo = LocalContext.current.container.repository
    val entries by remember { repo.observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val mapBlock = rememberMapBlock()

    val todayKey = System.currentTimeMillis().dayKey()
    val today = remember(entries) { entries.filter { it.createdAt.dayKey() == todayKey }.sortedBy { it.createdAt } }
    val byDay = remember(entries) { entries.groupBy { it.createdAt.dayKey() } }

    Box(Modifier.fillMaxSize().background(Vibe.Paper)) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 108.dp),
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        ) {
            item(key = "header") {
                Header(today.size, mapBlock, onOpenMap)
            }
            if (today.isNotEmpty()) {
                item(key = "summary") { RouteSummary(today) }
            }
            item(key = "sync") { SyncStatusRow(onOpenSettings) }

            if (entries.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "아직 멈춘 곳이 없어요.\n아래 버튼으로 지금 이 순간을 남겨 보세요.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Vibe.Muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 96.dp, start = 32.dp, end = 32.dp),
                    )
                }
            }

            val latestId = entries.firstOrNull()?.id
            byDay.forEach { (day, dayEntries) ->
                if (day != todayKey) {
                    item(key = "day-$day") {
                        Text(
                            dayFormat.format(Date(day)),
                            style = MaterialTheme.typography.titleMedium,
                            color = Vibe.Muted,
                            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 10.dp),
                        )
                    }
                }
                items(dayEntries, key = { it.id }) { entry ->
                    StationRow(
                        entry = entry,
                        current = entry.id == latestId,
                        lineAbove = entry != dayEntries.first(),
                        lineBelow = entry != dayEntries.last(),
                        onClick = { onOpenEntry(entry.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        // Capture lives behind a single "+" so the dock doesn't sit on top of the latest
        // stops. Choosing an action, tapping outside or Back folds it again.
        var dockOpen by rememberSaveable { mutableStateOf(false) }
        BackHandler(enabled = dockOpen) { dockOpen = false }
        if (dockOpen) {
            Box(
                Modifier.fillMaxSize().background(Vibe.Ink.copy(alpha = 0.08f))
                    .clickable(interactionSource = null, indication = null, onClickLabel = "기록 버튼 닫기") { dockOpen = false }
            )
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            AnimatedVisibility(
                visible = dockOpen,
                enter = fadeIn(tween(150)) + expandHorizontally(tween(220), expandFrom = Alignment.End),
                exit = fadeOut(tween(120)) + shrinkHorizontally(tween(180), shrinkTowards = Alignment.End),
            ) {
                CaptureDock(onCapture = { mode -> dockOpen = false; onCapture(mode) })
            }
            AnimatedVisibility(visible = !dockOpen, enter = fadeIn(tween(150)) + scaleIn(), exit = fadeOut(tween(100)) + scaleOut()) {
                Surface(
                    onClick = { dockOpen = true },
                    shape = CircleShape,
                    color = Vibe.Ink,
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(64.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, "기록하기", tint = Vibe.Paper, modifier = Modifier.size(30.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(todayCount: Int, mapBlock: MapBlock?, onOpenMap: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("오늘의 노선", style = MaterialTheme.typography.headlineLarge)
            val date = dayFormat.format(Date())
            Text(
                if (todayCount == 0) "$date, 아직 멈춘 곳이 없어요"
                else "$date, ${koreanCounts.getOrElse(todayCount) { "$todayCount" }} 곳에서 멈췄어요",
                style = MaterialTheme.typography.bodyMedium,
                color = Vibe.Muted,
            )
        }
        RoundButton(
            icon = Icons.Outlined.Map,
            contentDescription = mapBlock?.message ?: "지도로 보기",
            onClick = onOpenMap,
            enabled = mapBlock == null,
        )
    }
}

/** This many stops fit across the card at once; a day with more scrolls sideways. */
private const val VISIBLE_STOPS = 5
private val DOT_SIZE = 18.dp
private val ROUTE_WIDTH = 6.dp

/**
 * Today's stops left → right as a horizontal line diagram. Every stop gets the same pitch
 * (a fifth of the card, so long names are cut short), up to [VISIBLE_STOPS] sit centred and
 * more scroll sideways.
 */
@Composable
private fun RouteSummary(today: List<Entry>) {
    BoxWithConstraints(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp).fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(Color.White)
            .padding(vertical = 16.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Rounded down in px so five stops never add up to more than the card and scroll.
        val pitch = with(LocalDensity.current) { (constraints.maxWidth / VISIBLE_STOPS).toDp() }
        // Reversed so scroll position 0 is the end: the latest stop stays in view when the card
        // first shows and when the list scrolls it back in. A new stop resets the position, so it
        // comes into view even if the user had scrolled back to earlier ones.
        val scroll = remember(today.size) { ScrollState(initial = 0) }
        Row(Modifier.horizontalScroll(scroll, reverseScrolling = true)) {
            today.forEachIndexed { index, entry ->
                RouteStop(
                    entry,
                    isFirst = index == 0,
                    isLast = index == today.lastIndex,
                    modifier = Modifier.width(pitch),
                )
            }
        }
    }
}

/** One stop: its dot on the line (joined only to its neighbours), name and time below. */
@Composable
private fun RouteStop(entry: Entry, isFirst: Boolean, isLast: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().height(DOT_SIZE).drawBehind {
                // A line only runs between stops: none before the first or after the last.
                val start = if (isFirst) size.width / 2 else 0f
                val end = if (isLast) size.width / 2 else size.width
                if (end > start) {
                    val y = size.height / 2
                    drawLine(Vibe.Route, Offset(start, y), Offset(end, y), ROUTE_WIDTH.toPx())
                }
            },
            contentAlignment = Alignment.Center,
        ) { StationDot(current = isLast, size = DOT_SIZE) }
        Text(
            entry.stationName,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
        )
        Text(
            entry.createdAt.clock(),
            style = MaterialTheme.typography.labelSmall,
            color = Vibe.Muted,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Drive status doubles as the way into settings (its main job is backup). */
@Composable
private fun SyncStatusRow(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.container.prefs
    val infos by remember { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(SyncScheduler.NOW) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val running = infos.any { it.state == WorkInfo.State.RUNNING }

    val (icon, text) = when {
        !prefs.syncEnabled -> Icons.Outlined.CloudOff to "드라이브에 연결하지 않았어요"
        running -> Icons.Outlined.CloudSync to "드라이브와 맞추는 중이에요"
        prefs.lastSyncError != null -> Icons.Outlined.CloudOff to "드라이브와 맞추지 못했어요"
        prefs.lastSyncAt > 0 -> Icons.Outlined.CloudDone to "${lastSyncLabel(prefs.lastSyncAt)}에 드라이브와 맞췄어요"
        else -> Icons.Outlined.CloudSync to "드라이브와 처음 맞추는 중이에요"
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "동기화와 설정 열기", onClick = onOpenSettings)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (prefs.syncEnabled) Vibe.Route else Vibe.Muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Vibe.Body)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(Color.White)
                .drawBehind { drawCircle(Vibe.Ink, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx())) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Settings, "설정", tint = Vibe.Ink, modifier = Modifier.size(18.dp)) }
    }
}

private fun lastSyncLabel(at: Long): String =
    if (at.dayKey() == System.currentTimeMillis().dayKey()) at.clock()
    else SimpleDateFormat("M/d HH:mm", Locale.KOREA).format(Date(at))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StationRow(
    entry: Entry,
    current: Boolean,
    lineAbove: Boolean,
    lineBelow: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable(onClick = onClick).padding(horizontal = 20.dp),
    ) {
        // The rail: a thick route line through this stop.
        Box(
            Modifier.width(28.dp).fillMaxHeight().drawBehind {
                val x = size.width / 2
                val w = ROUTE_WIDTH.toPx()
                val dotY = 12.dp.toPx()
                if (lineAbove) drawLine(Vibe.Route, Offset(x, 0f), Offset(x, dotY), w)
                if (lineBelow) drawLine(Vibe.Route, Offset(x, dotY), Offset(x, size.height), w)
            },
            contentAlignment = Alignment.TopCenter,
        ) { StationDot(current, Modifier.padding(top = 4.dp)) }

        Spacer(Modifier.width(12.dp))
        Column(
            Modifier.weight(1f).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(entry.stationName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.createdAt.clock(), style = MaterialTheme.typography.labelMedium, color = Vibe.Muted)
            }
            entry.photoFile?.let {
                EntryPhoto(it, Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(10.dp)))
            }
            if (entry.note.isNotBlank()) {
                Text(entry.note, style = MaterialTheme.typography.bodyLarge, maxLines = 5, overflow = TextOverflow.Ellipsis)
            }
            if (entry.audioFile != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(Vibe.Leaf), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Mic, null, tint = Vibe.LeafInk, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("음성 ${formatDuration(entry.audioDurationMs)}", style = MaterialTheme.typography.labelMedium, color = Vibe.Muted)
                }
                when {
                    entry.analysis == AnalysisState.PENDING ->
                        Text("말한 내용을 글로 옮기는 중이에요", style = MaterialTheme.typography.bodyMedium, color = Vibe.Muted)
                    !entry.transcript.isNullOrBlank() ->
                        Text(entry.transcript, style = MaterialTheme.typography.bodyLarge, color = Vibe.Body, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            if (entry.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    entry.tags.forEach { TagChip(it) }
                }
            }
        }
    }
}
