package com.mapv12.dutytracker

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecureStores {
    private const val PREF_SECURE = "dutytracker_secure"
    private const val KEY_DEVICE_TOKEN = "device_token"

    private fun securePrefs(ctx: Context) = try {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            ctx,
            PREF_SECURE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Worst case fallback (should not happen on normal devices)
        ctx.getSharedPreferences(PREF_SECURE, Context.MODE_PRIVATE)
    }

    fun getDeviceToken(ctx: Context): String? {
        return securePrefs(ctx).getString(KEY_DEVICE_TOKEN, null)
    }

    fun setDeviceToken(ctx: Context, token: String?) {
        val ed = securePrefs(ctx).edit()
        if (token.isNullOrBlank()) ed.remove(KEY_DEVICE_TOKEN) else ed.putString(KEY_DEVICE_TOKEN, token)
        ed.apply()
    }
}

object SessionStore {
    private const val PREF = "dutytracker_session"
    private const val KEY_SESSION_ID = "session_id"

    fun getSessionId(ctx: Context): String? =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_SESSION_ID, null)

    fun setSessionId(ctx: Context, sessionId: String?) {
        val ed = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
        if (sessionId.isNullOrBlank()) ed.remove(KEY_SESSION_ID) else ed.putString(KEY_SESSION_ID, sessionId)
        ed.apply()
    }
}

object StatusStore {
    private const val PREF = "dutytracker_status"
    private const val KEY_LAST_GPS = "last_gps"
    private const val KEY_LAST_UPLOAD = "last_upload"
    private const val KEY_LAST_ERROR = "last_error"
    private const val KEY_QUEUE = "queue"
    private const val KEY_LAST_HEALTH = "last_health"
    private const val KEY_EFFECTIVE_MODE = "effective_mode"
    private const val KEY_LAST_ACC_M = "last_acc_m"
    private const val KEY_SERVICE_RUNNING = "service_running"
    private const val KEY_LAST_FILTER = "last_filter"
    private const val KEY_FILTER_REJECTS = "filter_rejects"
    private const val KEY_LAST_ACCEPTED = "last_accepted"
    private const val KEY_LAST_LAT = "last_lat"
    private const val KEY_LAST_LON = "last_lon"
    private const val KEY_LAST_FP_SENT = "last_fp_sent"

    fun setLastGps(ctx: Context, iso: String) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_GPS, iso)
            .apply()
    }

    fun getLastGps(ctx: Context): String =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_LAST_GPS, "") ?: ""

    fun setLastUpload(ctx: Context, iso: String) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_UPLOAD, iso)
            .apply()
    }

    fun getLastUpload(ctx: Context): String =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_LAST_UPLOAD, "") ?: ""

    fun setLastError(ctx: Context, err: String?) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_ERROR, err ?: "")
            .apply()
    }

    fun getLastError(ctx: Context): String =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_LAST_ERROR, "") ?: ""

    fun setQueue(ctx: Context, n: Int) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putInt(KEY_QUEUE, n)
            .apply()
    }

    fun getQueue(ctx: Context): Int =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(KEY_QUEUE, 0)

    fun setLastHealth(ctx: Context, iso: String) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_HEALTH, iso)
            .apply()
    }

    fun getLastHealth(ctx: Context): String =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_LAST_HEALTH, "") ?: ""

    fun setLastAccM(ctx: Context, accM: Int) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putInt(KEY_LAST_ACC_M, accM)
            .apply()
    }

    fun getLastAccM(ctx: Context): Double? {
        val v = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(KEY_LAST_ACC_M, -1)
        return if (v >= 0) v.toDouble() else null
    }

    fun setEffectiveMode(ctx: Context, modeId: String) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_EFFECTIVE_MODE, modeId)
            .apply()
    }

    fun getEffectiveMode(ctx: Context): String =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_EFFECTIVE_MODE, "") ?: ""

    fun setServiceRunning(ctx: Context, running: Boolean) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SERVICE_RUNNING, running)
            .apply()
    }

    fun isServiceRunning(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_SERVICE_RUNNING, false)


    fun setLastFilter(ctx: Context, v: String) {
    ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
        .putString(KEY_LAST_FILTER, v)
        .apply()
    }

    fun getLastFilter(ctx: Context): String =
    ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_LAST_FILTER, "") ?: ""

    fun incFilterRejects(ctx: Context) {
    val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    val n = p.getInt(KEY_FILTER_REJECTS, 0) + 1
    p.edit().putInt(KEY_FILTER_REJECTS, n).apply()
    }

    fun resetFilterRejects(ctx: Context) {
    ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
        .putInt(KEY_FILTER_REJECTS, 0)
        .apply()
    }

    fun getFilterRejects(ctx: Context): Int =
    ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(KEY_FILTER_REJECTS, 0)

    fun setLastAccepted(ctx: Context, iso: String) {
    ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
        .putString(KEY_LAST_ACCEPTED, iso)
        .apply()
    }

    fun getLastAccepted(ctx: Context): String =
    ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_LAST_ACCEPTED, "") ?: ""



    fun setLastLatLon(ctx: Context, lat: Double, lon: Double) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_LAT, lat.toString())
            .putString(KEY_LAST_LON, lon.toString())
            .apply()
    }

    fun getLastLatLon(ctx: Context): Pair<Double, Double>? {
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val slat = p.getString(KEY_LAST_LAT, "") ?: ""
        val slon = p.getString(KEY_LAST_LON, "") ?: ""
        return try {
            if (slat.isBlank() || slon.isBlank()) null else Pair(slat.toDouble(), slon.toDouble())
        } catch (_: Exception) { null }
    }

    fun setLastFingerprintSent(ctx: Context, iso: String) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_FP_SENT, iso)
            .apply()
    }

    fun getLastFingerprintSent(ctx: Context): String =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_LAST_FP_SENT, "") ?: ""
    fun read(ctx: Context): Map<String, Any> {
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val acc = p.getInt(KEY_LAST_ACC_M, -1)
        return mapOf(
            "last_gps" to (p.getString(KEY_LAST_GPS, "") ?: ""),
            "last_upload" to (p.getString(KEY_LAST_UPLOAD, "") ?: ""),
            "last_error" to (p.getString(KEY_LAST_ERROR, "") ?: ""),
            "last_health" to (p.getString(KEY_LAST_HEALTH, "") ?: ""),
            "queue" to p.getInt(KEY_QUEUE, 0),
            "effective_mode" to (p.getString(KEY_EFFECTIVE_MODE, "") ?: ""),
            "last_acc_m" to (if (acc >= 0) acc else 0),
            "service_running" to p.getBoolean(KEY_SERVICE_RUNNING, false),
            "last_filter" to (p.getString(KEY_LAST_FILTER, "") ?: ""),
            "filter_rejects" to p.getInt(KEY_FILTER_REJECTS, 0),
            "last_accepted" to (p.getString(KEY_LAST_ACCEPTED, "") ?: "")
            ,"last_fp_sent" to (p.getString(KEY_LAST_FP_SENT, "") ?: "")
            ,"last_lat" to (p.getString(KEY_LAST_LAT, "") ?: "")
            ,"last_lon" to (p.getString(KEY_LAST_LON, "") ?: "")
        )
    }
}

