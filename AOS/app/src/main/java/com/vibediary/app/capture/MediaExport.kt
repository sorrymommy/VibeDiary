package com.vibediary.app.capture

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Optional public copies of captured media, so they also show up in the phone's
 * gallery / recordings. The app's own copy in files/media stays the source of truth.
 *
 * Photos → Pictures/VibeDiary, recordings → Recordings/VibeDiary (Music/VibeDiary before Android 12).
 * Android 10+ needs no permission; 8–9 need WRITE_EXTERNAL_STORAGE (asked from settings).
 */
object MediaExport {
    private const val FOLDER = "VibeDiary"
    private val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    /** Android 8–9 only: the toggle must ask for storage permission first. */
    val needsPermission: Boolean get() = Build.VERSION.SDK_INT < 29

    suspend fun exportPhoto(context: Context, file: File, takenAt: Long) =
        export(context, file, takenAt, "jpg", "image/jpeg", photo = true)

    /** WAV until analysis compresses it (see [AudioCompressor]); kept as WAV if that fails or can be retried. */
    suspend fun exportAudio(context: Context, file: File, takenAt: Long) = when (file.extension) {
        AudioCompressor.EXTENSION -> export(context, file, takenAt, AudioCompressor.EXTENSION, AudioCompressor.MIME_TYPE, photo = false)
        else -> export(context, file, takenAt, AudioRecorder.EXTENSION, AudioRecorder.MIME_TYPE, photo = false)
    }

    private suspend fun export(
        context: Context,
        file: File,
        takenAt: Long,
        ext: String,
        mime: String,
        photo: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext false
        val name = "VibeDiary_${stamp.format(Date(takenAt))}.$ext"
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) exportScoped(context, file, name, mime, takenAt, photo)
            else exportLegacy(context, file, name, mime, photo)
        }.isSuccess
    }

    private fun exportScoped(context: Context, file: File, name: String, mime: String, takenAt: Long, photo: Boolean) {
        val resolver = context.contentResolver
        val collection = if (photo) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val dir = when {
            photo -> Environment.DIRECTORY_PICTURES
            Build.VERSION.SDK_INT >= 31 -> Environment.DIRECTORY_RECORDINGS
            else -> Environment.DIRECTORY_MUSIC
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/$FOLDER")
            put(MediaStore.MediaColumns.DATE_TAKEN, takenAt)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun exportLegacy(context: Context, file: File, name: String, mime: String, photo: Boolean) {
        val base = Environment.getExternalStoragePublicDirectory(
            if (photo) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MUSIC
        )
        val target = File(File(base, FOLDER).apply { mkdirs() }, name)
        file.copyTo(target, overwrite = true)
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
    }
}
