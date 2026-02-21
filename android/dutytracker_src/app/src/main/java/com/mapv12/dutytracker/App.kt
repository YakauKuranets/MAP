package com.mapv12.dutytracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.room.Room

/**
 * Application class — точка входа.
 * Инициализирует Room БД, воркеры, каналы уведомлений.
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()

        // Room DB — offline queue + event journal
        _db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "dutytracker.db"
        )
            .fallbackToDestructiveMigration()
            .build()

        // Watchdog: перезапускает трекинг если сервис убит системой
        runCatching { WatchdogWorker.ensureScheduled(applicationContext) }

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)

        // Канал трекинга — уведомление всегда видно пока служба активна
        NotificationChannel(
            CHANNEL_TRACKING,
            "Трекинг дежурства",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Показывает статус активного GPS-трекинга"
            nm.createNotificationChannel(this)
        }

        // Канал SOS — высокий приоритет
        NotificationChannel(
            CHANNEL_SOS,
            "SOS / Экстренный вызов",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Уведомления об экстренных вызовах"
            nm.createNotificationChannel(this)
        }
    }

    companion object {
        private lateinit var _db: AppDatabase
        val db: AppDatabase get() = _db

        const val CHANNEL_TRACKING = "dutytracker_tracking"
        const val CHANNEL_SOS      = "dutytracker_sos"
    }
}
