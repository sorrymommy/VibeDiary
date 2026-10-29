package com.vibediary.app.ui.map

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The map screens need both a Maps API key baked into the build and a live internet
 * connection (tiles are streamed). Without either, the map would render as a blank grid,
 * so the UI disables map entry points instead.
 */
object MapAvailability {
    private const val KEY_META = "com.google.android.geo.API_KEY"

    fun hasApiKey(context: Context): Boolean = runCatching {
        context.packageManager
            .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            .metaData?.getString(KEY_META)
    }.getOrNull().isNullOrBlank().not()

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun observeOnline(context: Context): Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(isOnline(context)) }
            override fun onLost(network: Network) { trySend(isOnline(context)) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(isOnline(context))
            }
        }
        trySend(isOnline(context))
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}

/** Why the map can't be shown right now, or null when it can. */
enum class MapBlock(val message: String) {
    NO_KEY("지도 API 키가 설정되지 않아 지도를 쓸 수 없어요"),
    OFFLINE("인터넷에 연결되면 지도를 볼 수 있어요"),
}

@Composable
fun rememberMapBlock(): MapBlock? {
    val context = LocalContext.current
    val hasKey = remember { MapAvailability.hasApiKey(context) }
    val online by remember { MapAvailability.observeOnline(context.applicationContext) }
        .collectAsStateWithLifecycle(initialValue = MapAvailability.isOnline(context))
    return when {
        !hasKey -> MapBlock.NO_KEY
        !online -> MapBlock.OFFLINE
        else -> null
    }
}
