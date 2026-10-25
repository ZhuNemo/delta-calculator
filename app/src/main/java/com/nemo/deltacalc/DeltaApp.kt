package com.nemo.deltacalc

import android.app.Application
import com.nemo.deltacalc.data.AppDatabase
import com.nemo.deltacalc.data.HistoryRepository
import com.nemo.deltacalc.data.SettingsRepository

class DeltaApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }

    val historyRepository: HistoryRepository by lazy {
        HistoryRepository(database.historyDao())
    }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
}
