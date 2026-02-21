package com.mapv12.dutytracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.room.Room

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        // Room DB (offline queue + event journal)
        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "dutytracker.db")
            .fallbackToDestructiveMigration()
            .build()

        // Periodic watchdog (restart tracking service if killed)
        try { WatchdogWorker.ensureScheduled(applicationContext) } catch (_: Exception) {}

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                CHANNEL_ID,
                "DutyTracker Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            ch.description = "Foreground tracking service notifications"
            nm.createNotificationChannel(ch)
        }
    }

    companion object {
        lateinit var db: AppDatabase
            private set

        const val CHANNEL_ID = "dutytracker_tracking"
    }
}
