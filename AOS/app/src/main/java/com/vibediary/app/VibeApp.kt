package com.vibediary.app

import android.app.Application
import android.content.Context
import com.vibediary.app.data.AppDatabase
import com.vibediary.app.data.AppLock
import com.vibediary.app.data.AppPrefs
import com.vibediary.app.data.EntryRepository
import com.vibediary.app.sync.SyncScheduler

class VibeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        if (container.prefs.syncEnabled) SyncScheduler.enablePeriodic(this, container.prefs)
    }
}

class AppContainer(context: Context) {
    val prefs = AppPrefs(context)
    val lock = AppLock(context)
    val repository = EntryRepository(context, AppDatabase.build(context).entryDao(), prefs)
}

val Context.container: AppContainer get() = (applicationContext as VibeApp).container
