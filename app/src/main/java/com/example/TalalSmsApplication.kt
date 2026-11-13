package com.example

import android.app.Application
import com.example.service.NotificationHelper

class TalalSmsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
    }
}
