package com.vibediary.app.ui.lock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vibediary.app.container
import com.vibediary.app.ui.DarkSystemBarIcons
import com.vibediary.app.ui.components.RoundButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PinAction { SET, CHANGE, DISABLE }

private enum class Step { CURRENT, NEW, CONFIRM }

/**
 * SET: new → confirm. CHANGE: current → new → confirm. DISABLE: current, then clear.
 * [onDone] gets true when the lock state actually changed.
 */
@Composable
fun PinSetupDialog(action: PinAction, onDone: (Boolean) -> Unit) {
    val lock = LocalContext.current.container.lock
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(if (action == PinAction.SET) Step.NEW else Step.CURRENT) }
    var firstPin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var reset by remember { mutableIntStateOf(0) }

    fun fail(text: String) {
        message = text; isError = true; reset++
    }

    Dialog(
        onDismissRequest = { onDone(false) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DarkSystemBarIcons()
        Box {
            PinPad(
                title = when (step) {
                    Step.CURRENT -> "지금 비밀번호를 입력해 주세요"
                    Step.NEW -> "새 비밀번호 6자리를 정해 주세요"
                    Step.CONFIRM -> "한 번 더 입력해 주세요"
                },
                message = message ?: if (step == Step.NEW) "앱을 켤 때 이 숫자를 물어요" else null,
                isError = isError,
                enabled = !busy && lock.waitMillis() == 0L,
                resetKey = reset,
                onComplete = { pin ->
                    when (step) {
                        Step.CURRENT -> scope.launch {
                            busy = true
                            val ok = withContext(Dispatchers.Default) { lock.verify(pin) }
                            busy = false
                            when {
                                !ok -> fail(if (lock.waitMillis() > 0) "여러 번 틀렸어요. 잠시 뒤에 다시 해 주세요" else "비밀번호가 맞지 않아요")
                                action == PinAction.DISABLE -> { lock.clear(); onDone(true) }
                                else -> { step = Step.NEW; message = null; isError = false; reset++ }
                            }
                        }
                        Step.NEW -> { firstPin = pin; step = Step.CONFIRM; message = null; isError = false; reset++ }
                        Step.CONFIRM ->
                            if (pin == firstPin) scope.launch {
                                busy = true
                                withContext(Dispatchers.Default) { lock.setPin(pin) }
                                busy = false
                                onDone(true)
                            } else {
                                step = Step.NEW
                                fail("두 번 입력한 숫자가 달라요. 처음부터 다시 정해 주세요")
                            }
                    }
                },
            )
            RoundButton(
                Icons.Filled.Close, "취소", { onDone(false) },
                modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.statusBars).padding(16.dp),
            )
        }
    }
}
