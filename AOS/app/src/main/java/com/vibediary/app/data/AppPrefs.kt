package com.vibediary.app.data

import android.content.Context
import androidx.core.content.edit

class AppPrefs(context: Context) {
    private val sp = context.getSharedPreferences("vibediary", Context.MODE_PRIVATE)

    var syncEnabled: Boolean
        get() = sp.getBoolean("syncEnabled", false)
        set(value) = sp.edit { putBoolean("syncEnabled", value) }

    /** Push to Drive right after each change (otherwise only on the periodic run / "지금 맞추기"). */
    var autoSync: Boolean
        get() = sp.getBoolean("autoSync", true)
        set(value) = sp.edit { putBoolean("autoSync", value) }

    /** How often to pull entries made on other devices. */
    var pullHours: Int
        get() = sp.getInt("pullHours", 6)
        set(value) = sp.edit { putInt("pullHours", value) }

    var wifiOnly: Boolean
        get() = sp.getBoolean("wifiOnly", false)
        set(value) = sp.edit { putBoolean("wifiOnly", value) }

    /** Off = only text and tags go to Drive. */
    var uploadMedia: Boolean
        get() = sp.getBoolean("uploadMedia", true)
        set(value) = sp.edit { putBoolean("uploadMedia", value) }

    /** Also put a copy of new photos/recordings in the phone's gallery and recordings folder. */
    var exportToGallery: Boolean
        get() = sp.getBoolean("exportToGallery", false)
        set(value) = sp.edit { putBoolean("exportToGallery", value) }

    var saveLocation: Boolean
        get() = sp.getBoolean("saveLocation", true)
        set(value) = sp.edit { putBoolean("saveLocation", value) }

    /** Highest Drive `modifiedTime` (RFC 3339) already pulled. */
    var lastPullTime: String?
        get() = sp.getString("lastPullTime", null)
        set(value) = sp.edit { putString("lastPullTime", value) }

    var lastSyncAt: Long
        get() = sp.getLong("lastSyncAt", 0)
        set(value) = sp.edit { putLong("lastSyncAt", value) }

    var lastSyncError: String?
        get() = sp.getString("lastSyncError", null)
        set(value) = sp.edit { putString("lastSyncError", value) }
}
