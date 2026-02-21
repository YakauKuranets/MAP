package com.mapv12.dutytracker

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class WatchdogWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork() = withContext(Dispatchers.IO) {
        val ctx = applicationContext

        // If tracking should be on but service is not running, restart it.
        val should = ForegroundLocationService.isTrackingOn(ctx)
        val running = StatusStore.isServiceRunning(ctx)
        if (should && !running) {
            try {
                JournalLogger.log(ctx, "watchdog", "restart_service", true, null, null, null)
                val i = Intent(ctx, ForegroundLocationService::class.java)
                ContextCompat.startForegroundService(ctx, i)
            } catch (e: Exception) {
                JournalLogger.log(ctx, "watchdog", "restart_service", false, null, e.message, null)
                return@withContext Result.retry()
            }
        }
        Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "dutytracker_watchdog"

        fun ensureScheduled(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                req
            )
        }
    }
}
