package com.vibediary.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vibediary.app.R

/**
 * Picks up where the system splash leaves off: same paper ground, same mark in the
 * same place (the system draws the 108dp icon at 240dp), plus the tagline beneath.
 */
@Composable
fun LaunchScreen() {
    Box(Modifier.fillMaxSize().background(Vibe.Paper), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "VibeDiary",
            modifier = Modifier.size(240.dp),
        )
        Column(
            Modifier.offset(y = 104.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "느낌이 나는 순간을 위치와 함께",
                style = MaterialTheme.typography.bodyLarge,
                color = Vibe.Body,
            )
        }
    }
}
