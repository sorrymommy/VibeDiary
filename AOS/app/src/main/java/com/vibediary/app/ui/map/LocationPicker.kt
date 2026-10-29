package com.vibediary.app.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.vibediary.app.capture.Locator
import com.vibediary.app.capture.Place
import com.vibediary.app.ui.DarkSystemBarIcons
import com.vibediary.app.ui.Vibe
import com.vibediary.app.ui.components.RoundButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SEOUL = LatLng(37.5665, 126.9780)

/**
 * Full-screen "drag the map under the pin" picker. The pin stays in the centre; whatever
 * is under it when the user taps "여기로 정하기" becomes the entry's place.
 */
@Composable
fun LocationPickerDialog(
    initial: LatLng?,
    onPick: (LatLng, Place?) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DarkSystemBarIcons()
        LocationPicker(initial, onPick, onDismiss)
    }
}

@Composable
private fun LocationPicker(initial: LatLng?, onPick: (LatLng, Place?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initial ?: SEOUL, if (initial != null) 17f else 12f)
    }
    var place by remember { mutableStateOf<Place?>(null) }
    var resolving by remember { mutableStateOf(false) }

    // Name the spot under the pin once the map settles.
    LaunchedEffect(camera.isMoving) {
        if (camera.isMoving) return@LaunchedEffect
        resolving = true
        delay(250)
        val target = camera.position.target
        place = Locator.place(context, target.latitude, target.longitude)
        resolving = false
    }

    Box(Modifier.fillMaxSize().background(Vibe.Paper)) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false),
        )

        // Fixed pin: a station dot on a short ink stem, its foot exactly at the map centre.
        Column(
            Modifier.align(Alignment.Center).offset(y = (-22).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(Vibe.Ink).border(5.dp, Color.White, CircleShape)
            )
            Box(Modifier.width(4.dp).height(16.dp).background(Vibe.Ink))
        }

        Row(
            Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(Icons.Filled.Close, "위치 고르기 닫기", onDismiss)
            Spacer(Modifier.width(10.dp))
            Text(
                "지도를 움직여 위치를 맞춰요",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 12.dp).semantics { heading() },
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 12.dp, end = 12.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RoundButton(Icons.Outlined.MyLocation, "지금 위치로", onClick = {
                scope.launch {
                    Locator.current(context)?.let {
                        camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 17f))
                    }
                }
            })
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White)
                    .border(2.dp, Vibe.Ink, RoundedCornerShape(18.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column {
                    Text(
                        when {
                            resolving || camera.isMoving -> "어디인지 보는 중이에요"
                            place != null -> place!!.name.split(" ").last()
                            else -> "이름 없는 곳"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        place?.name?.takeIf { !resolving && !camera.isMoving } ?: "핀 아래 위치로 남겨요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Vibe.Muted,
                    )
                }
                Button(
                    onClick = { onPick(camera.position.target, place.takeIf { !resolving }) },
                    enabled = !camera.isMoving,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Vibe.Ink, contentColor = Color.White),
                ) { Text("여기로 정하기", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}
