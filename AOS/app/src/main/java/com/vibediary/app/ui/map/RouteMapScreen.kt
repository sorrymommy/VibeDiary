package com.vibediary.app.ui.map

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.vibediary.app.container
import com.vibediary.app.data.Entry
import com.vibediary.app.ui.Vibe
import com.vibediary.app.ui.components.EntryPhoto
import com.vibediary.app.ui.components.RoundButton
import com.vibediary.app.ui.components.clock
import com.vibediary.app.ui.components.stationName
import com.vibediary.app.ui.home.dayKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Entry.latLng: LatLng? get() = if (lat != null && lng != null) LatLng(lat, lng) else null

private val SEOUL = LatLng(37.5665, 126.9780)
private val dateLabel = SimpleDateFormat("M월 d일", Locale.KOREA)

@Composable
fun RouteMapScreen(onBack: () -> Unit, onOpenEntry: (String) -> Unit) {
    val context = LocalContext.current
    val repo = context.container.repository
    val entries by remember { repo.observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val mapBlock = rememberMapBlock()
    LaunchedEffect(mapBlock) { if (mapBlock != null) onBack() }

    // Always open on today (even before the first stop), then step back through days that
    // have a route.
    val today = System.currentTimeMillis().dayKey()
    val days = remember(entries, today) {
        (entries.filter { it.hasLocation }.map { it.createdAt.dayKey() } + today).distinct().sorted()
    }
    var day by rememberSaveable { mutableStateOf<Long?>(null) }
    val shownDay = day?.takeIf { it in days } ?: today
    val stops = remember(entries, shownDay) {
        entries.filter { it.hasLocation && it.createdAt.dayKey() == shownDay }.sortedBy { it.createdAt }
    }
    // Stops at the same place share one badge ("1, 4"); the line still visits it twice.
    val groups = remember(stops) { groupStops(stops) }
    val groupOf = remember(groups) { IntArray(stops.size).also { arr -> groups.forEachIndexed { g, grp -> grp.indices.forEach { arr[it] = g } } } }
    var selectedId by remember(shownDay) { mutableStateOf<String?>(null) }
    val selectedIndex = stops.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 } ?: stops.lastIndex

    val camera = rememberCameraPositionState {
        // No stop today yet: start where the most recent route ended.
        val start = stops.lastOrNull()?.latLng ?: entries.firstOrNull { it.hasLocation }?.latLng ?: SEOUL
        position = CameraPosition.fromLatLngZoom(start, 14f)
    }
    var mapLoaded by remember { mutableStateOf(false) }
    val lastSeen = entries.firstOrNull { it.hasLocation }?.latLng
    LaunchedEffect(mapLoaded, shownDay, stops.size, lastSeen) {
        if (!mapLoaded) return@LaunchedEffect
        runCatching {
            // A day with no stops (usually "today, not yet"): show where the last route ended.
            if (stops.isEmpty()) {
                lastSeen?.let { camera.animate(CameraUpdateFactory.newLatLngZoom(it, 14f)) }
                return@runCatching
            }
            if (groups.size == 1) camera.animate(CameraUpdateFactory.newLatLngZoom(groups[0].position, 15f))
            else {
                val bounds = LatLngBounds.builder().apply { stops.forEach { include(it.latLng!!) } }.build()
                camera.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120))
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Vibe.Paper)) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            // Standard Google map (shops, restaurants, transit all shown). No blue "you are here"
            // dot: it would sit on top of the latest station's number.
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false),
            onMapLoaded = { mapLoaded = true },
            // Keep the framed route clear of the date bar above and the stop card below.
            contentPadding = PaddingValues(top = 96.dp, bottom = 230.dp),
        ) {
            if (groups.size > 1) {
                Polyline(
                    points = stops.indices.map { groups[groupOf[it]].position },
                    color = Vibe.Route, width = 22f, jointType = com.google.android.gms.maps.model.JointType.ROUND,
                )
            }
            groups.forEach { group ->
                key(stops[group.indices.first()].id) {
                    val selected = selectedIndex in group.indices
                    val label = group.indices.joinToString(", ") { "${it + 1}" }
                    MarkerComposable(
                        label, selected,
                        state = remember(group.position) { MarkerState(group.position) },
                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                        zIndex = if (selected) 2f else 1f,
                        onClick = { if (!selected) selectedId = stops[group.indices.last()].id; true },
                    ) { StopBadge(label = label, selected = selected) }
                }
            }
        }

        // Header: back to list + day switcher.
        Row(
            Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(Icons.AutoMirrored.Outlined.ViewList, "목록으로 돌아가기", onBack)
            Spacer(Modifier.width(8.dp))
            val idx = days.indexOf(shownDay)
            Row(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(Color.White)
                    .border(2.dp, Vibe.Ink, RoundedCornerShape(24.dp)).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { day = days[idx - 1] }, enabled = idx > 0) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "이전 날")
                }
                Text(
                    dateLabel.format(Date(shownDay)).let { if (shownDay == today) "$it, 오늘" else it },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = { day = days[idx + 1] }, enabled = idx in 0 until days.lastIndex) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "다음 날")
                }
            }
        }

        stops.getOrNull(selectedIndex)?.let { entry ->
            val siblings = groups[groupOf[selectedIndex]].indices
            val pos = siblings.indexOf(selectedIndex)
            StopCard(
                number = selectedIndex + 1,
                entry = entry,
                pageLabel = if (siblings.size > 1) "${pos + 1} / ${siblings.size}" else null,
                onPrev = siblings.getOrNull(pos - 1)?.let { i -> { selectedId = stops[i].id } },
                onNext = siblings.getOrNull(pos + 1)?.let { i -> { selectedId = stops[i].id } },
                onOpen = { onOpenEntry(entry.id) },
                modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 12.dp, end = 12.dp, bottom = 20.dp),
            )
        }

        if (stops.isEmpty()) {
            Column(
                Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 12.dp, end = 12.dp, bottom = 20.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp)).background(Color.White)
                    .border(2.dp, Vibe.Ink, RoundedCornerShape(18.dp)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    if (shownDay == today) "오늘은 아직 멈춘 곳이 없어요" else "이날은 위치가 남은 기록이 없어요",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    if (days.size > 1) "위의 ‹ 를 눌러 지난 노선을 볼 수 있어요" else "기록을 남기면 여기에 노선이 그려져요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Vibe.Muted,
                )
            }
        }
    }
}

/** Stops within this distance count as the same place. */
private const val SAME_PLACE_METERS = 30f

private class StopGroup(val position: LatLng, val indices: MutableList<Int>)

/** Clusters chronologically-ordered stops by place, keeping each stop's own number. */
private fun groupStops(stops: List<Entry>): List<StopGroup> {
    val groups = mutableListOf<StopGroup>()
    val d = FloatArray(1)
    stops.forEachIndexed { i, e ->
        val p = e.latLng!!
        val near = groups.firstOrNull {
            Location.distanceBetween(it.position.latitude, it.position.longitude, p.latitude, p.longitude, d)
            d[0] <= SAME_PLACE_METERS
        }
        if (near != null) near.indices += i else groups += StopGroup(p, mutableListOf(i))
    }
    return groups
}

/** Round for one stop, a pill for a revisited place ("1, 4"). */
@Composable
private fun StopBadge(label: String, selected: Boolean) {
    val height = if (selected) 40.dp else 32.dp
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.height(height).widthIn(min = height).clip(shape)
            .background(if (selected) Vibe.Ink else Color.White)
            .border(if (selected) 3.dp else 4.dp, if (selected) Color.White else Vibe.Route, shape)
            .padding(horizontal = if (label.length > 1) 10.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else Vibe.Ink)
    }
}

@Composable
private fun StopCard(
    number: Int,
    entry: Entry,
    pageLabel: String?,
    onPrev: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White)
            .border(2.dp, Vibe.Ink, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Several moments at this place: flip through them in time order.
        pageLabel?.let { label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("이곳에서 남긴 순간", style = MaterialTheme.typography.labelMedium, color = Vibe.Muted, modifier = Modifier.weight(1f))
                IconButton(onClick = { onPrev?.invoke() }, enabled = onPrev != null, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "이전 순간")
                }
                Text(label, style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "다음 순간")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            entry.photoFile?.let { EntryPhoto(it, Modifier.size(84.dp).clip(RoundedCornerShape(10.dp))) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$number. ${entry.stationName}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(entry.createdAt.clock(), style = MaterialTheme.typography.labelMedium, color = Vibe.Muted)
                }
                val text = entry.note.ifBlank { entry.transcript.orEmpty() }
                if (text.isNotBlank()) {
                    Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Button(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Vibe.Ink, contentColor = Color.White),
        ) { Text("이 순간 펼쳐 보기", style = MaterialTheme.typography.labelLarge) }
    }
}
