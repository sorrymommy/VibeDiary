package com.vibediary.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vibediary.app.container
import com.vibediary.app.data.Entry
import com.vibediary.app.ui.Vibe
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Short, sign-like name for an entry's place: the user's own name if set, else "성동구 성수동2가" → "성수동2가". */
val Entry.stationName: String
    get() = stationLabel?.trim()?.takeIf { it.isNotEmpty() } ?: autoStationName

/** The name the address alone would give, ignoring any name the user typed. */
val Entry.autoStationName: String
    get() = placeName?.trim()?.split(" ")?.lastOrNull()?.takeIf { it.isNotBlank() }
        ?: if (hasLocation) "이름 없는 곳" else "어딘가"

private val hhmm = SimpleDateFormat("HH:mm", Locale.KOREA)
fun Long.clock(): String = hhmm.format(Date(this))

/** A station on the line. `current` = the most recent stop, drawn filled. */
@Composable
fun StationDot(current: Boolean, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (current) Vibe.Ink else Color.White)
            .border(3.dp, if (current) Vibe.Ink else Vibe.Route, CircleShape)
    )
}

@Composable
fun TagChip(tag: String, modifier: Modifier = Modifier) {
    Text(
        tag,
        style = MaterialTheme.typography.labelMedium,
        color = Vibe.LeafInk,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Vibe.Leaf)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** 48dp round, ink-outlined button used in headers. */
@Composable
fun RoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(48.dp)
            .then(if (outlined) Modifier.border(2.dp, if (enabled) Vibe.Ink else Vibe.Hairline, CircleShape) else Modifier),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.White,
            contentColor = Vibe.Ink,
            disabledContainerColor = Color.White,
            disabledContentColor = Color(0xFFA3ACA6),
        ),
    ) { Icon(icon, contentDescription) }
}

/** Photo with a calm placeholder colour while it loads. */
@Composable
fun EntryPhoto(fileName: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val repo = LocalContext.current.container.repository
    AsyncImage(
        model = repo.mediaFile(fileName),
        contentDescription = "사진",
        contentScale = contentScale,
        modifier = modifier.background(Vibe.Photo),
    )
}

enum class CaptureMode { PHOTO, VOICE, MEMO }

/** Floating ink pill: photo, memo (primary, the white pill), voice. Icons only. */
@Composable
fun CaptureDock(onCapture: (CaptureMode) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = Vibe.Ink,
        shape = RoundedCornerShape(32.dp),
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth().height(64.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DockIcon(Icons.Outlined.PhotoCamera, "사진 찍기") { onCapture(CaptureMode.PHOTO) }
            Surface(
                onClick = { onCapture(CaptureMode.MEMO) },
                color = Vibe.Paper,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.width(72.dp).height(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Edit, "메모 쓰기", tint = Vibe.Ink)
                }
            }
            DockIcon(Icons.Outlined.Mic, "음성 녹음") { onCapture(CaptureMode.VOICE) }
        }
    }
}

@Composable
private fun DockIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.width(56.dp).height(48.dp)) {
        Icon(icon, description, tint = Vibe.Paper)
    }
}
