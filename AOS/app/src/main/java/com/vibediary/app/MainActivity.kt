package com.vibediary.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.vibediary.app.ui.LaunchScreen
import kotlinx.coroutines.delay
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vibediary.app.ui.VibeTheme
import com.vibediary.app.ui.components.CaptureMode
import com.vibediary.app.ui.detail.EntryDetailScreen
import com.vibediary.app.ui.home.HomeScreen
import com.vibediary.app.ui.map.RouteMapScreen
import com.vibediary.app.ui.newentry.NewEntryScreen
import com.vibediary.app.ui.settings.SettingsScreen
import com.vibediary.app.data.AppLock
import com.vibediary.app.ui.lock.LockScreen

class MainActivity : ComponentActivity() {
    /** PIN gate. Up on launch, and again after [AppLock.RELOCK_AFTER_MS] in the background. */
    private val locked = mutableStateOf(false)
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always light (paper ground), so keep dark status/nav icons even when
        // the phone is in dark mode; otherwise the clock and battery turn white-on-white.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val coldStart = savedInstanceState == null
        locked.value = container.lock.isEnabled && (savedInstanceState?.getBoolean(KEY_LOCKED, true) ?: true)
        setContent {
            VibeTheme {
                // Stop the app above the system navigation (back/home) bar so that strip stays
                // Android's own; screens below this no longer draw underneath it.
                Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                    val nav = rememberNavController()
                    NavHost(navController = nav, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                onCapture = { mode -> nav.navigate("new/${mode.name}") },
                                onOpenEntry = { id -> nav.navigate("entry/$id") },
                                onOpenMap = { nav.navigate("map") },
                                onOpenSettings = { nav.navigate("settings") },
                            )
                        }
                        composable("new/{mode}") { backStack ->
                            val mode = runCatching { CaptureMode.valueOf(backStack.arguments?.getString("mode").orEmpty()) }
                                .getOrDefault(CaptureMode.MEMO)
                            NewEntryScreen(start = mode, onClose = { nav.popBackStack() })
                        }
                        composable("map") {
                            RouteMapScreen(
                                onBack = { nav.popBackStack("home", inclusive = false) },
                                onOpenEntry = { id -> nav.navigate("entry/$id") },
                            )
                        }
                        composable("entry/{id}") { backStack ->
                            EntryDetailScreen(
                                entryId = backStack.arguments?.getString("id").orEmpty(),
                                onBack = { nav.popBackStack() },
                            )
                        }
                        composable("settings") {
                            SettingsScreen(onBack = { nav.popBackStack() })
                        }
                    }

                    if (locked.value) {
                        LockScreen(onUnlocked = { locked.value = false })
                    }

                    // Tagline under the mark on a cold start, then fade into the timeline.
                    var showLaunch by rememberSaveable { mutableStateOf(coldStart) }
                    LaunchedEffect(Unit) {
                        delay(1400)
                        showLaunch = false
                    }
                    AnimatedVisibility(visible = showLaunch, enter = EnterTransition.None, exit = fadeOut(tween(400))) {
                        LaunchScreen()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val away = System.currentTimeMillis() - stoppedAt
        if (stoppedAt > 0 && away >= AppLock.RELOCK_AFTER_MS && container.lock.isEnabled) locked.value = true
    }

    override fun onStop() {
        super.onStop()
        // Short trips (camera, gallery picker, permission screens) don't re-lock.
        if (!isChangingConfigurations) stoppedAt = System.currentTimeMillis()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_LOCKED, locked.value)
    }

    private companion object {
        const val KEY_LOCKED = "locked"
    }
}
