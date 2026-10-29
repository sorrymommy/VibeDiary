package com.vibediary.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.vibediary.app.data.AppPrefs
import java.util.concurrent.TimeUnit

object SyncScheduler {
    const val NOW = "sync-now"
    private const val PERIODIC = "sync-periodic"

    private fun constraints(prefs: AppPrefs) = Constraints.Builder()
        .setRequiredNetworkType(if (prefs.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()

    /** Push shortly after a local change, if the user wants automatic sync. */
    fun requestSync(context: Context, prefs: AppPrefs) {
        if (!prefs.syncEnabled || !prefs.autoSync) return
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints(prefs))
            .setInitialDelay(5, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** "지금 맞추기": runs as soon as there is any connection, ignoring the Wi-Fi-only setting. */
    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Periodic pull so entries made on other devices show up. Re-call after settings change. */
    fun enablePeriodic(context: Context, prefs: AppPrefs, replace: Boolean = false) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(prefs.pullHours.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints(prefs))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun disable(context: Context) {
        WorkManager.getInstance(context).run {
            cancelUniqueWork(PERIODIC)
            cancelUniqueWork(NOW)
        }
    }
}
