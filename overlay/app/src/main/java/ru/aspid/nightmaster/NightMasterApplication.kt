package ru.aspid.nightmaster

import android.app.Application
import androidx.room.Room
import ru.aspid.nightmaster.core.inference.InferenceController
import ru.aspid.nightmaster.core.inference.LlamaInferenceController
import ru.aspid.nightmaster.data.database.NightMasterDatabase
import ru.aspid.nightmaster.data.preferences.SettingsRepository

class NightMasterApplication : Application() {
    val database: NightMasterDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            NightMasterDatabase::class.java,
            "night-master.db",
        ).build()
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(applicationContext)
    }

    val inferenceController: InferenceController by lazy {
        LlamaInferenceController(applicationContext)
    }
}
