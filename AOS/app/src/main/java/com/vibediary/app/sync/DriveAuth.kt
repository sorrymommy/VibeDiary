package com.vibediary.app.sync

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

/**
 * Google authorization for the hidden, app-private Drive folder only
 * (`drive.appdata` scope: the app cannot see any of the user's other files).
 */
object DriveAuth {
    private const val SCOPE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"

    private val request: AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE_APPDATA))).build()

    /** May need UI: check [AuthorizationResult.hasResolution] and launch its pendingIntent. */
    suspend fun authorize(context: Context): AuthorizationResult =
        Identity.getAuthorizationClient(context).authorize(request).await()

    /** Access token when consent was already given; null if the user must re-connect. */
    suspend fun silentToken(context: Context): String? =
        runCatching { authorize(context) }.getOrNull()
            ?.takeIf { !it.hasResolution() }
            ?.accessToken
}
