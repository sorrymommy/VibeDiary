package com.vibediary.app.ui.lock

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibediary.app.R
import com.vibediary.app.container
import com.vibediary.app.data.AppLock
import com.vibediary.app.ui.Vibe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Full-screen gate shown on launch (and after a long time away) while a PIN is set. */
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val lock = context.container.lock
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var reset by remember { mutableIntStateOf(0) }
    var checking by remember { mutableStateOf(false) }
    var waitMs by remember { mutableLongStateOf(lock.waitMillis()) }
    var showForgot by remember { mutableStateOf(false) }

    LaunchedEffect(waitMs) {
        if (waitMs > 0) {
            delay(1000)
            waitMs = lock.waitMillis()
        }
    }
    // Back on the lock screen leaves the app rather than revealing what's behind it.
    BackHandler { (context as? Activity)?.finish() }

    PinPad(
        title = "비밀번호를 입력해 주세요",
        message = when {
            waitMs > 0 -> "여러 번 틀렸어요. ${(waitMs + 999) / 1000}초 뒤에 다시 입력할 수 있어요"
            else -> error
        },
        isError = waitMs > 0 || error != null,
        enabled = waitMs == 0L && !checking,
        resetKey = reset,
        onComplete = { pin ->
            scope.launch {
                checking = true
                val ok = withContext(Dispatchers.Default) { lock.verify(pin) }
                checking = false
                if (ok) {
                    onUnlocked()
                } else {
                    error = "비밀번호가 맞지 않아요"
                    reset++
                    waitMs = lock.waitMillis()
                }
            }
        },
        footer = {
            TextButton(onClick = { showForgot = true }) {
                Text("비밀번호를 잊었어요", style = MaterialTheme.typography.labelMedium, color = Vibe.Muted)
            }
        },
    )

    if (showForgot) {
        AlertDialog(
            onDismissRequest = { showForgot = false },
            title = { Text("비밀번호는 되찾을 수 없어요", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "비밀번호는 이 휴대폰에만 있어서 다시 알려 드릴 수 없어요. 휴대폰 설정 > 애플리케이션 > VibeDiary > 저장공간에서 " +
                        "데이터를 지우면 잠금이 풀리지만, 휴대폰에 있던 기록도 함께 지워져요. 구글 드라이브에 보관해 두었다면 " +
                        "다시 연결해서 되돌릴 수 있어요.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { showForgot = false }) { Text("알겠어요", color = Vibe.Ink, style = MaterialTheme.typography.labelLarge) }
            },
            containerColor = Color.White,
        )
    }
}

/**
 * Six dots + number pad. Calls [onComplete] once six digits are in; bump [resetKey]
 * to clear the entry (after a wrong PIN, or between "enter" and "confirm").
 */
@Composable
fun PinPad(
    title: String,
    message: String?,
    isError: Boolean,
    enabled: Boolean,
    resetKey: Int,
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
) {
    var digits by remember(resetKey) { mutableStateOf("") }

    fun press(d: Char) {
        if (!enabled || digits.length >= AppLock.LENGTH) return
        digits += d
        if (digits.length == AppLock.LENGTH) onComplete(digits)
    }

    Column(
        modifier.fillMaxSize().background(Vibe.Paper)
            // Swallow touches so nothing underneath the gate can be reached.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(112.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.semantics { contentDescription = "${digits.length}자리 입력됨" },
        ) {
            repeat(AppLock.LENGTH) { i ->
                val filled = i < digits.length
                Box(
                    Modifier.size(16.dp).clip(CircleShape)
                        .background(if (filled) Vibe.Ink else Color.Transparent)
                        .border(2.dp, if (isError && !filled) MaterialTheme.colorScheme.error else Vibe.Ink, CircleShape)
                )
            }
        }
        Text(
            message ?: " ",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else Vibe.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp).height(44.dp),
        )
        Spacer(Modifier.weight(1f))

        val rows = listOf("123", "456", "789", " 0<")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    row.forEach { key ->
                        when (key) {
                            ' ' -> Spacer(Modifier.size(72.dp))
                            '<' -> Surface(
                                onClick = { if (enabled) digits = digits.dropLast(1) },
                                shape = CircleShape,
                                color = Color.Transparent,
                                modifier = Modifier.size(72.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Outlined.Backspace, "한 자리 지우기", tint = Vibe.Ink)
                                }
                            }
                            else -> Surface(
                                onClick = { press(key) },
                                enabled = enabled,
                                shape = CircleShape,
                                color = Color.White,
                                modifier = Modifier.size(72.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("$key", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp), color = Vibe.Ink)
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) { footer() }
    }
}
