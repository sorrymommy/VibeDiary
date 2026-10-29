package com.vibediary.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class AnalysisState { PENDING, DONE, FAILED }

/**
 * One diary moment. Any combination of note, photo and voice recording.
 * Media is stored as a bare file name inside the app's `files/media` dir and,
 * when synced, as a same-named file in the Drive appDataFolder.
 */
@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val note: String = "",
    val transcript: String? = null,
    val photoFile: String? = null,
    val audioFile: String? = null,
    val audioDurationMs: Long = 0,
    val lat: Double? = null,
    val lng: Double? = null,
    val placeName: String? = null,
    /** The user's own name for this place ("회사 앞"); replaces the address-derived 동/구 name. */
    val stationLabel: String? = null,
    val tags: List<String> = emptyList(),
    val analysis: AnalysisState = AnalysisState.PENDING,
    val analysisMessage: String? = null,
    val deleted: Boolean = false,
    // Local-only sync bookkeeping.
    val dirty: Boolean = true,
    val driveJsonId: String? = null,
) {
    val hasLocation: Boolean get() = lat != null && lng != null
    val mediaFiles: List<String> get() = listOfNotNull(photoFile, audioFile)
}
