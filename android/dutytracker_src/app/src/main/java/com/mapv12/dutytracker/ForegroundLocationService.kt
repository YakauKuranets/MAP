package com.mapv12.dutytracker

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.TimeUnit

class ForegroundLocationService : Service() {

    companion object {
        const val ACTION_START = "com.mapv12.dutytracker.ACTION_START"
        const val ACTION_STOP = "com.mapv12.dutytracker.ACTION_STOP"
        const val ACTION_UPDATE_MODE = "com.mapv12.dutytracker.ACTION_UPDATE_MODE"

        private const val NOTIF_ID = 2001
        private const val CH_ID = "dutytracker_location"

        const val PREF_FLAGS = "dutytracker_flags"
        const val KEY_TRACKING_ON = "tracking_on"

        fun isTrackingOn(ctx: android.content.Context): Boolean =
            ctx.getSharedPreferences(PREF_FLAGS, android.content.Context.MODE_PRIVATE).getBoolean(KEY_TRACKING_ON, false)

        fun setTrackingOn(ctx: android.content.Context, on: Boolean) {
            ctx.getSharedPreferences(PREF_FLAGS, android.content.Context.MODE_PRIVATE).edit().putBoolean(KEY_TRACKING_ON, on).apply()
        }

        fun enqueueUpload(ctx: android.content.Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val req = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(ctx)
                .enqueueUniqueWork("upload_now", ExistingWorkPolicy.KEEP, req)
        }

        fun requestModeUpdate(ctx: android.content.Context) {
            val i = Intent(ctx, ForegroundLocationService::class.java).apply { action = ACTION_UPDATE_MODE }
            try {
                ctx.startService(i)
            } catch (_: Exception) {}
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var healthJob: Job? = null

    private lateinit var fused: FusedLocationProviderClient

    private val qualityFilter = TrackingQualityFilter()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return

            // AUTO mode: adjust effective request based on movement
            try {
                if (TrackingModeStore.get(applicationContext) == TrackingMode.AUTO) {
                    val decided = AutoModeController.decide(
                        speedMps = if (loc.hasSpeed()) loc.speed.toDouble() else null,
                        accuracyM = if (loc.hasAccuracy()) loc.accuracy.toDouble() else null
                    )
                    val current = AutoModeController.getEffective(applicationContext)
                    if (decided != current) {
                        AutoModeController.setEffective(applicationContext, decided)
                        JournalLogger.log(applicationContext, "mode", "auto", true, null, null, "effective=${'$'}{decided.id}")
                        // Re-apply request quickly
                        requestModeUpdate(applicationContext)
                    }
                }
            } catch (_: Exception) { }

            StatusStore.setLastGps(applicationContext, Instant.now().toString())

            if (loc.hasAccuracy()) {
                StatusStore.setLastAccM(applicationContext, loc.accuracy.toInt())
            }

            val effMode = try {
                val base = TrackingModeStore.get(applicationContext)
                if (base == TrackingMode.AUTO) AutoModeController.getEffective(applicationContext) else base
            } catch (_: Exception) {
                TrackingMode.NORMAL
            }

            val decision = try { qualityFilter.process(loc, effMode) } catch (e: Exception) {
                // If filter fails, do not block tracking.
                TrackingQualityFilter.Decision(true, "ok", loc)
            }

            if (!decision.accept) {
                StatusStore.setLastFilter(applicationContext, decision.reason)
                StatusStore.incFilterRejects(applicationContext)
                return
            }

            StatusStore.setLastFilter(applicationContext, decision.reason)
            StatusStore.setLastAccepted(applicationContext, Instant.now().toString())
            try { StatusStore.resetFilterRejects(applicationContext) } catch (_: Exception) {}

            val outLoc = decision.out ?: loc

            try {
                StatusStore.setLastLatLon(applicationContext, outLoc.latitude, outLoc.longitude)
            } catch (_: Exception) {}

            val sessionId = SessionStore.getSessionId(applicationContext)
            val point = TrackPointEntity(
                sessionId = sessionId,
                tsEpochMs = outLoc.time,
                lat = outLoc.latitude,
                lon = outLoc.longitude,
                accuracyM = if (outLoc.hasAccuracy()) outLoc.accuracy.toDouble() else null,
                speedMps = if (outLoc.hasSpeed()) outLoc.speed.toDouble() else null,
                bearingDeg = if (outLoc.hasBearing()) outLoc.bearing.toDouble() else null,
                state = UploadState.PENDING
            )

            scope.launch {
                try {
                    App.db.trackPointDao().insert(point)
                    var left = App.db.trackPointDao().countQueued()
                    if (left > Config.MAX_PENDING_POINTS) {
                        val toDelete = left - Config.MAX_PENDING_POINTS
                        try {
                            App.db.trackPointDao().deleteOldestQueued(toDelete)
                        } catch (_: Exception) {}
                        left = App.db.trackPointDao().countQueued()
                    }
                    
                    StatusStore.setQueue(applicationContext, left)
                } catch (e: Exception) {
                    StatusStore.setLastError(applicationContext, e.message)
                }
            }

            // Trigger upload (throttled by ExistingWorkPolicy.KEEP)
            enqueueUpload(applicationContext)
        }
    }

    override fun onCreate() {
        super.onCreate()
        StatusStore.setServiceRunning(applicationContext, true)
        fused = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE_MODE -> {
                applyModeUpdate()
                return START_STICKY
            }
            ACTION_STOP -> {
                stopTracking()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startTracking()
                return START_STICKY
            }
            else -> {
                startTracking()
                return START_STICKY
            }
        }
    }

    private fun buildRequest(): LocationRequest {
        val stored = TrackingModeStore.get(applicationContext)
        val mode = if (stored == TrackingMode.AUTO) {
            val eff = AutoModeController.getEffective(applicationContext)
            if (eff == TrackingMode.AUTO) TrackingMode.NORMAL else eff
        } else stored
        return when (mode) {
            TrackingMode.ECO -> LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 15000L)
                .setMinUpdateIntervalMillis(10000L)
                .setMinUpdateDistanceMeters(20f)
                .setMaxUpdateDelayMillis(0)
                .build()

            TrackingMode.PRECISE -> LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
                .setMinUpdateIntervalMillis(1000L)
                .setMinUpdateDistanceMeters(0f)
                // Важно: если true, то в помещении/на "плохом" GPS система может долго
                // не выдавать никаких обновлений (в результате "Нет свежих GPS данных").
                // Точность мы и так контролируем фильтром.
                .setWaitForAccurateLocation(false)
                .setMaxUpdateDelayMillis(0)
                .build()

            TrackingMode.NORMAL -> LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                .setMinUpdateIntervalMillis(3000L)
                .setMinUpdateDistanceMeters(10f)
                .setMaxUpdateDelayMillis(0)
                .build()

            TrackingMode.AUTO -> LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                .setMinUpdateIntervalMillis(3000L)
                .setMinUpdateDistanceMeters(10f)
                .setMaxUpdateDelayMillis(0)
                .build()
        }
    }

    private fun applyModeUpdate() {
        // Меняем частоту без выключения смены
        if (!isTrackingOn(applicationContext)) return
        if (!hasLocationPermission()) return
        try {
            fused.removeLocationUpdates(callback)
        } catch (_: Exception) {}
        try {
            fused.requestLocationUpdates(buildRequest(), callback, Looper.getMainLooper())
        } catch (_: Exception) {}
    }


    private fun startHealthLoop() {
        if (healthJob != null) return
        healthJob = scope.launch {
            while (isTrackingOn(applicationContext)) {
                try {
                    val dao = App.db.trackPointDao()
                    val left = dao.countQueued()
                    StatusStore.setQueue(applicationContext, left)

                    val st = StatusStore.read(applicationContext)
                    val nowIso = Instant.now().toString()
                    val lu = st["last_upload"] as? String
                    val lastSend = if (!lu.isNullOrBlank() && lu != "—") lu else nowIso
                    val payload = DeviceStatus.collect(
                        ctx = applicationContext,
                        queueSize = left,
                        trackingOn = true,
                        accuracyM = StatusStore.getLastAccM(applicationContext),
                        lastSendAtIso = lastSend,
                        lastError = st["last_error"] as? String
                    )
                    val ok = ApiClient(applicationContext).sendHealth(payload)
                    if (ok) {
                        StatusStore.setLastHealth(applicationContext, Instant.now().toString())
                    }
                } catch (_: Exception) { }
                delay(15000)
            }
        }
    }

    private fun stopHealthLoop(sendFinal: Boolean = false) {
        try { healthJob?.cancel() } catch (_: Exception) { }
        healthJob = null

        if (sendFinal) {
            scope.launch {
                try {
                    val dao = App.db.trackPointDao()
                    val left = dao.countQueued()
                    StatusStore.setQueue(applicationContext, left)

                    val st = StatusStore.read(applicationContext)
                    val nowIso = Instant.now().toString()
                    val lu = st["last_upload"] as? String
                    val lastSend = if (!lu.isNullOrBlank() && lu != "—") lu else nowIso
                    val payload = DeviceStatus.collect(
                        ctx = applicationContext,
                        queueSize = left,
                        trackingOn = false,
                        accuracyM = StatusStore.getLastAccM(applicationContext),
                        lastSendAtIso = lastSend,
                        lastError = st["last_error"] as? String
                    )
                    val ok = ApiClient(applicationContext).sendHealth(payload)
                    if (ok) {
                        StatusStore.setLastHealth(applicationContext, Instant.now().toString())
                    }
                } catch (_: Exception) { }
            }
        }
    }

private fun startTracking() {
        if (!hasLocationPermission()) {
            StatusStore.setLastError(applicationContext, "Нет разрешения на геолокацию")
            stopSelf()
            return
        }

        createChannelIfNeeded()
        startForeground(NOTIF_ID, buildNotification())

        setTrackingOn(applicationContext, true)
        startHealthLoop()

        val req = buildRequest()

        try {
            fused.requestLocationUpdates(req, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            StatusStore.setLastError(applicationContext, e.message)
            stopSelf()
        }
    }

    private fun stopTracking() {
        stopHealthLoop(sendFinal = true)
        // Важно: Command Center должен увидеть «конец службы», а не «потерян сигнал».
        // Принудительно инициируем отправку финального health через UploadWorker.
        try { StatusStore.setLastHealth(applicationContext, "") } catch (_: Exception) {}
        try { enqueueUpload(applicationContext) } catch (_: Exception) {}
        setTrackingOn(applicationContext, false)
        try { fused.removeLocationUpdates(callback) } catch (_: Exception) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(CH_ID, "DutyTracker", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
        }
    }
    private fun buildNotification(): Notification {
        val stored = TrackingModeStore.get(applicationContext)
        val eff = if (stored == TrackingMode.AUTO) AutoModeController.getEffective(applicationContext) else stored
        val label = if (stored == TrackingMode.AUTO) "auto→${eff.id}" else stored.id
        val queue = StatusStore.getQueue(applicationContext)

        return NotificationCompat.Builder(this, CH_ID)
            .setContentTitle("DutyTracker")
            .setContentText("Трекинг: $label · очередь: $queue")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }


    override fun onDestroy() {
        StatusStore.setServiceRunning(applicationContext, false)
        stopTracking()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
