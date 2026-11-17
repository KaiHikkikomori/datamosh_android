package ru.datamosh.radio

import android.app.Application

class RadioApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LikesStore.init(this)
    }
}
