package ru.datamosh.radio

import android.app.Application

class RadioApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        LikesStore.init(this)
        RadioRepo.delaySeconds = getSharedPreferences("settings", MODE_PRIVATE).getFloat("delay_seconds", 0f)
        PlayerWidgets.start(this)
    }
}
