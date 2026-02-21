package com.mapv12.dutytracker

import kotlinx.coroutines.runBlocking
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import android.content.Context
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.widget.LinearLayout
import android.view.ViewGroup
import java.time.Instant

class MainActivity : AppCompatActivity() {

private fun survivabilityIssues(): List<String> {
    val issues = mutableListOf<String>()

    // Permissions
    val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!fine && !coarse) issues.add("Нет разрешения на геолокацию")

    if (Build.VERSION.SDK_INT >= 29) {
        val bg = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!bg) issues.add("Нет ACCESS_BACKGROUND_LOCATION (фон)")
    }

    // Notifications (Android 13+)
    if (Build.VERSION.SDK_INT >= 33) {
        val notif = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!notif) issues.add("Нет разрешения на уведомления (Android 13+)")
    }

    // Network
    try {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork
        val caps = if (n != null) cm.getNetworkCapabilities(n) else null
        if (caps == null) issues.add("Нет сети (Wi‑Fi/Cell)")
    } catch (_: Exception) {}

    // Location services
    try {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!enabled) issues.add("Выключена геолокация (Location Services)")
    } catch (_: Exception) {}

    // Stale GPS while tracking
    try {
        if (ForegroundLocationService.isTrackingOn(this)) {
            val lastGpsIso = StatusStore.getLastGps(this)
            if (lastGpsIso.isNotBlank()) {
                val t = Instant.parse(lastGpsIso)
                val age = Instant.now().epochSecond - t.epochSecond
                if (age > 30) issues.add("Нет свежих GPS данных > 30s (проверь сигнал/разрешения)")
            }
        }
    } catch (_: Exception) {}

    // Filter status
    try {
        val lf = StatusStore.getLastFilter(this)
        val rej = StatusStore.getFilterRejects(this)
        if (lf.isNotBlank() && lf != "ok") {
            issues.add("Фильтр: ${'$'}lf (rej=${'$'}rej)")
        }
    } catch (_: Exception) {}

    // Battery optimizations
    try {
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            issues.add("Батарея: оптимизация включена (может убивать фон)")
        }
    } catch (_: Exception) {}

    return issues
}

    private lateinit var screenPair: ScrollView
    private lateinit var screenProfile: ScrollView
    private lateinit var screenHome: ScrollView

    private lateinit var etBaseUrl: TextInputEditText
    private lateinit var etPairCode: TextInputEditText
    private lateinit var btnPair: MaterialButton
    private lateinit var tvPairStatus: TextView

    private lateinit var etFullName: TextInputEditText
    private lateinit var etDutyNumber: TextInputEditText
    private lateinit var etUnit: TextInputEditText
    private lateinit var etPosition: TextInputEditText
    private lateinit var etRank: TextInputEditText
    private lateinit var etPhone: TextInputEditText
    private lateinit var btnSaveProfile: MaterialButton
    private lateinit var tvProfileStatus: TextView

    private lateinit var tvStatus: TextView
    private lateinit var btnStartStop: MaterialButton
    private lateinit var toggleMode: MaterialButtonToggleGroup
    private lateinit var btnDiag: MaterialButton
    private lateinit var btnJournal: MaterialButton
    private lateinit var btnSos: MaterialButton
    private lateinit var btnMap: MaterialButton
    private lateinit var btnEditProfile: MaterialButton
    private lateinit var btnResetPair: MaterialButton
    private lateinit var tvHomeDebug: TextView

    // Home tiles
    private lateinit var tvTileGps: TextView
    private lateinit var tvTileNet: TextView
    private lateinit var tvTileQueue: TextView
    private lateinit var tvTileAcc: TextView
    private lateinit var tvTileLast: TextView
    private lateinit var tvTileBattery: TextView

    // Problems block
    private lateinit var llProblems: LinearLayout
    private lateinit var btnOpenGuide: MaterialButton


    // Smart-start preflight
    private var pendingSmartStart: Boolean = false
    private var lastAutoStartAt: Long = 0L

    private val api by lazy { ApiClient(this) }
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val uiHandler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            updateHomeStatus()
            uiHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        wireActions()

        etBaseUrl.setText(Config.getBaseUrl(this))
        fillProfileForm()

        ensurePermissions()
        decideScreen()
        handleDeepLink(intent)
    }


    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(i: Intent?) {
        val uri: Uri = i?.data ?: return
        val scheme = (uri.scheme ?: "").lowercase()
        val host = (uri.host ?: "").lowercase()
        if (scheme != "dutytracker") return

        if (host == "pair") {
            val baseUrl = (uri.getQueryParameter("base_url") ?: "").trim()
            val code = (uri.getQueryParameter("code") ?: "").trim()
            if (baseUrl.isNotEmpty()) {
                Config.setBaseUrl(this, baseUrl)
                etBaseUrl.setText(Config.getBaseUrl(this))
            }
            if (code.isNotEmpty()) {
                etPairCode.setText(code)
            }
            decideScreen()
            if (code.length == 6) doPair()
            return
        }

        if (host != "bootstrap") return

        val baseUrl = (uri.getQueryParameter("base_url") ?: "").trim()
        val token = (uri.getQueryParameter("token") ?: "").trim()

        if (baseUrl.isNotEmpty()) {
            Config.setBaseUrl(this, baseUrl)
            etBaseUrl.setText(Config.getBaseUrl(this))
        }

        if (token.isEmpty()) {
            Toast.makeText(this, "bootstrap token пустой", Toast.LENGTH_SHORT).show()
            return
        }

        tvPairStatus.text = "Получен bootstrap-токен. Загружаю конфиг…"
        decideScreen()

        Thread {
            try {
                val api = ApiClient(this)
                val res = api.bootstrapConfig(token)
                runOnUiThread {
                    if (!res.ok) {
                        tvPairStatus.text = "Bootstrap ошибка: " + (res.error ?: "unknown")
                        return@runOnUiThread
                    }
                    if (!res.baseUrl.isNullOrBlank()) {
                        Config.setBaseUrl(this, res.baseUrl!!)
                        etBaseUrl.setText(Config.getBaseUrl(this))
                    }
                    if (!res.pairCode.isNullOrBlank()) {
                        etPairCode.setText(res.pairCode!!)
                    }
                    tvPairStatus.text = "Конфиг получен. Выполняю привязку…"
                    if (!res.pairCode.isNullOrBlank() && res.pairCode!!.length == 6) {
                        doPair()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvPairStatus.text = "Bootstrap исключение: " + e.message
                }
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        uiHandler.post(tick)
        maybeAutoStartAfterFix()
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(tick)
    }

    private fun bindViews() {
        screenPair = findViewById(R.id.screen_pair)
        screenProfile = findViewById(R.id.screen_profile)
        screenHome = findViewById(R.id.screen_home)

        etBaseUrl = findViewById(R.id.et_base_url)
        etPairCode = findViewById(R.id.et_pair_code)
        btnPair = findViewById(R.id.btn_pair)
        tvPairStatus = findViewById(R.id.tv_pair_status)

        etFullName = findViewById(R.id.et_full_name)
        etDutyNumber = findViewById(R.id.et_duty_number)
        etUnit = findViewById(R.id.et_unit)
        etPosition = findViewById(R.id.et_position)
        etRank = findViewById(R.id.et_rank)
        etPhone = findViewById(R.id.et_phone)
        btnSaveProfile = findViewById(R.id.btn_save_profile)
        tvProfileStatus = findViewById(R.id.tv_profile_status)

        tvStatus = findViewById(R.id.tv_status_line)
        btnStartStop = findViewById(R.id.btn_start_stop)
        toggleMode = findViewById(R.id.toggle_mode)
        btnSos = findViewById(R.id.btn_sos)
        btnMap = findViewById(R.id.btn_map)
        btnDiag = findViewById(R.id.btn_diag)
        btnJournal = findViewById(R.id.btn_journal)
        btnEditProfile = findViewById(R.id.btn_edit_profile)
        btnResetPair = findViewById(R.id.btn_reset_pair)
        tvHomeDebug = findViewById(R.id.tv_home_debug)

        tvTileGps = findViewById(R.id.tv_tile_gps)
        tvTileNet = findViewById(R.id.tv_tile_net)
        tvTileQueue = findViewById(R.id.tv_tile_queue)
        tvTileAcc = findViewById(R.id.tv_tile_acc)
        tvTileLast = findViewById(R.id.tv_tile_last)
        tvTileBattery = findViewById(R.id.tv_tile_battery)

        llProblems = findViewById(R.id.ll_problems)
        btnOpenGuide = findViewById(R.id.btn_open_guide)
    }

    private fun wireActions() {
        btnPair.setOnClickListener { doPair() }
        btnSaveProfile.setOnClickListener { doSaveProfile() }
        btnStartStop.setOnClickListener { toggleTracking() }
        btnMap.setOnClickListener { startActivity(Intent(this, MapActivity::class.java)) }
        setupSosHold()
        initModeToggle()
        btnDiag.setOnClickListener { startActivity(Intent(this, DiagnosticsActivity::class.java)) }
        btnJournal.setOnClickListener { startActivity(Intent(this, JournalActivity::class.java)) }
        btnEditProfile.setOnClickListener {
            fillProfileForm()
            showOnly(screenProfile)
        }
        btnResetPair.setOnClickListener { resetPairing() }
        btnOpenGuide.setOnClickListener { startActivity(Intent(this, OemGuideActivity::class.java)) }

        etBaseUrl.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                Config.setBaseUrl(this, etBaseUrl.text?.toString().orEmpty())
                Toast.makeText(this, "URL сохранён", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSosHold() {
        // Safety: SOS только по удержанию 2 секунды (случайные нажатия)
        val holdMs = 2000L
        val stepMs = 100L
        var startAt = 0L
        var armed = false

        val runnable = object : Runnable {
            override fun run() {
                if (!armed) return
                val elapsed = System.currentTimeMillis() - startAt
                val left = (holdMs - elapsed).coerceAtLeast(0)
                if (left <= 0) {
                    armed = false
                    btnSos.text = "SOS…"
                    btnSos.isEnabled = false
                    haptic()
                    sendSosQuick()
                } else {
                    val sec = (left / 1000.0)
                    btnSos.text = String.format("SOS (держи %.1fs)", sec)
                    uiHandler.postDelayed(this, stepMs)
                }
            }
        }

        btnSos.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (btnSos.isEnabled) {
                        armed = true
                        startAt = System.currentTimeMillis()
                        btnSos.text = "SOS (держи 2.0s)"
                        uiHandler.post(runnable)
                    }
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    if (armed) {
                        armed = false
                        btnSos.text = "SOS"
                        uiHandler.removeCallbacks(runnable)
                    }
                    true
                }
                else -> false
            }
        }

        // Double tap (optional) — открыть диалог с сообщением
        btnSos.setOnLongClickListener {
            doSos()
            true
        }
    }

    private fun haptic() {
        try {
            btnSos.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        } catch (_: Exception) {}
    }

    private fun sendSosQuick() {
        Thread {
            var lat: Double? = null
            var lon: Double? = null
            var acc: Float? = null
            try {
                val l = com.google.android.gms.tasks.Tasks.await(fused.lastLocation)
                if (l != null) {
                    lat = l.latitude
                    lon = l.longitude
                    acc = l.accuracy
                }
            } catch (_: Exception) { /* ignore */ }

            val res = if (lat != null && lon != null) api.sos(lat, lon, acc, null) else api.sosLast(null)

            runOnUiThread {
                btnSos.isEnabled = true
                btnSos.text = "SOS"
                if (res.ok) {
                    Toast.makeText(this, "SOS отправлен (#${res.sosId ?: ""})", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "SOS: ошибка (${res.error ?: "unknown"})", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun initModeToggle() {
        // Выбор режима трекинга (частота/батарея)
        val mode = TrackingModeStore.get(this)
        val id = when (mode) {
            TrackingMode.ECO -> R.id.btn_mode_eco
            TrackingMode.PRECISE -> R.id.btn_mode_precise
            TrackingMode.NORMAL -> R.id.btn_mode_normal
            TrackingMode.AUTO -> R.id.btn_mode_normal
        }
        try { toggleMode.check(id) } catch (_: Exception) {}

        toggleMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val newMode = when (checkedId) {
                R.id.btn_mode_eco -> TrackingMode.ECO
                R.id.btn_mode_precise -> TrackingMode.PRECISE
                else -> TrackingMode.NORMAL
            }
            TrackingModeStore.set(this, newMode)
            if (isTrackingOn()) {
                ForegroundLocationService.requestModeUpdate(this)
            }
            updateHomeStatus()
        }

        // AUTO режим по удержанию на "Норма" (чтобы не ломать UI кнопками).
        // Удержали: NORMAL <-> AUTO. В тексте статуса/нотификации будет auto→eco/normal/precise.
        val btnNormal = findViewById<MaterialButton>(R.id.btn_mode_normal)
        btnNormal.setOnLongClickListener {
            val cur = TrackingModeStore.get(this)
            val next = if (cur == TrackingMode.AUTO) TrackingMode.NORMAL else TrackingMode.AUTO
            TrackingModeStore.set(this, next)
            if (next == TrackingMode.AUTO && StatusStore.getEffectiveMode(this).isBlank()) {
                AutoModeController.setEffective(this, TrackingMode.NORMAL)
            }
            if (isTrackingOn()) ForegroundLocationService.requestModeUpdate(this)
            Toast.makeText(this, if (next == TrackingMode.AUTO) "Режим: AUTO (удержание)" else "Режим: NORMAL", Toast.LENGTH_SHORT).show()
            updateHomeStatus()
            true
        }

    }

    
    private fun exportJournalAndShare() {
        Thread {
            try {
                val rows = runBlocking { App.db.eventJournalDao().last(500) }.reversed()
                val st = StatusStore.read(this)

                val sb = StringBuilder()
                sb.append("DutyTracker log export\n")
                sb.append("time=").append(java.time.Instant.now().toString()).append("\n")
                sb.append("server=").append(Config.getBaseUrl(this)).append("\n")
                sb.append("device_id=").append(DeviceInfoStore.deviceId(this) ?: "—").append("\n")
                sb.append("user_id=").append(DeviceInfoStore.userId(this) ?: "—").append("\n")
                sb.append("label=").append(DeviceInfoStore.label(this) ?: "—").append("\n\n")
                sb.append("queue=").append(st["queue"]).append("\n")
                sb.append("last_gps=").append(st["last_gps"]).append("\n")
                sb.append("last_upload=").append(st["last_upload"]).append("\n")
                sb.append("last_health=").append(st["last_health"]).append("\n")
                sb.append("last_error=").append(st["last_error"]).append("\n\n")
                sb.append("journal_last_500:\n")
                if (rows.isEmpty()) sb.append("— пусто —\n")
                for (r in rows) sb.append(JournalLogger.formatLine(r)).append("\n")

                val f = java.io.File(cacheDir, "dutytracker_journal_${System.currentTimeMillis()}.txt")
                f.writeText(sb.toString(), Charsets.UTF_8)

                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", f)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runOnUiThread {
                    startActivity(Intent.createChooser(send, "Отправить лог"))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Экспорт лога: ошибка (${e.message})", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

private fun showDiagnostics() {
        // Fetch journal in background so UI doesn't freeze
        Thread {
            val st = StatusStore.read(this)
            val stored = TrackingModeStore.get(this)
            val eff = if (stored == TrackingMode.AUTO) AutoModeController.getEffective(this) else stored
            val modeLabel = if (stored == TrackingMode.AUTO) "auto→${eff.id}" else stored.id

            val devId = DeviceInfoStore.deviceId(this) ?: "—"
            val userId = DeviceInfoStore.userId(this) ?: "—"
            val label = DeviceInfoStore.label(this) ?: "—"

            val issues = survivabilityIssues()

            val journalLines = try {
                val rows = runBlocking { App.db.eventJournalDao().last(50) }.reversed()
                if (rows.isEmpty()) listOf("— пусто —") else rows.map { JournalLogger.formatLine(it) }
            } catch (e: Exception) {
                listOf("— не удалось прочитать журнал: ${'$'}{e.message} —")
            }

            val msg = buildString {
                append("Сервер: ").append(Config.getBaseUrl(this@MainActivity)).append("\n")
                append("Режим: ").append(modeLabel).append("\n")
                append("Device ID: ").append(devId).append("\n")
                append("User ID: ").append(userId).append("\n")
                append("Label: ").append(label).append("\n\n")
                append("Очередь: ").append(st["queue"]).append("\n")
                if ((st["last_gps"] as String).isNotBlank()) append("GPS: ").append(st["last_gps"]).append("\n")
                if ((st["last_upload"] as String).isNotBlank()) append("Отправка: ").append(st["last_upload"]).append("\n")
                if ((st["last_health"] as String).isNotBlank()) append("Health: ").append(st["last_health"]).append("\n")
                if ((st["last_error"] as String).isNotBlank()) append("\nОшибка: ").append(st["last_error"]).append("\n")
                append("\nЖурнал (последние 50):\n")
                for (line in journalLines) append(line).append("\n")
            }

            runOnUiThread {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Диагностика")
                    .setMessage(msg)
                    .setPositiveButton("Гайд") { _, _ ->
                        startActivity(Intent(this, OemGuideActivity::class.java))
                    }
                    .setNeutralButton("Экспорт логов") { _, _ ->
                        exportJournalAndShare()
                    }
                    .setNegativeButton("Закрыть", null)
                    .show()
            }
        }.start()
    }

    private fun showOnly(v: View) {
        screenPair.visibility = if (v == screenPair) View.VISIBLE else View.GONE
        screenProfile.visibility = if (v == screenProfile) View.VISIBLE else View.GONE
        screenHome.visibility = if (v == screenHome) View.VISIBLE else View.GONE
    }

    private fun decideScreen() {
        val paired = !SecureStores.getDeviceToken(this).isNullOrBlank()
        val prof = ProfileStore.isComplete(this)

        if (!paired) {
            showOnly(screenPair)
            btnStartStop.isEnabled = false
            return
        }

        if (!prof) {
            showOnly(screenProfile)
            btnStartStop.isEnabled = false
            return
        }

        showOnly(screenHome)
        btnStartStop.isEnabled = true
        updateHomeStatus()
    }

    private fun fillProfileForm() {
        val p = ProfileStore.load(this) ?: return
        etFullName.setText(p.fullName)
        etDutyNumber.setText(p.dutyNumber)
        etUnit.setText(p.unit)
        etPosition.setText(p.position)
        etRank.setText(p.rank)
        etPhone.setText(p.phone)
    }

    private fun doPair() {
        Config.setBaseUrl(this, etBaseUrl.text?.toString().orEmpty())

        val code = etPairCode.text?.toString().orEmpty().trim()
        if (code.length < 4) {
            tvPairStatus.text = "Введите код привязки"
            return
        }

        tvPairStatus.text = "Привязка…"
        Thread {
            val res = api.pair(code)
            runOnUiThread {
                if (res.ok && !res.deviceToken.isNullOrBlank()) {
                    SecureStores.setDeviceToken(this, res.deviceToken)
                    DeviceInfoStore.set(this, res.deviceId, res.userId, res.label)
                    tvPairStatus.text = "OK: ${res.label ?: res.deviceId ?: "device"}"
                    Toast.makeText(this, "Привязано", Toast.LENGTH_SHORT).show()
                    decideScreen()
                } else {
                    tvPairStatus.text = "Ошибка: ${res.error ?: "unknown"}"
                }
            }
        }.start()
    }

    private fun doSaveProfile() {
        val token = SecureStores.getDeviceToken(this)
        if (token.isNullOrBlank()) {
            Toast.makeText(this, "Сначала привяжите устройство", Toast.LENGTH_SHORT).show()
            decideScreen()
            return
        }

        val full = etFullName.text?.toString().orEmpty().trim()
        val duty = etDutyNumber.text?.toString().orEmpty().trim()
        val unit = etUnit.text?.toString().orEmpty().trim()
        val pos = etPosition.text?.toString().orEmpty().trim()
        val rank = etRank.text?.toString().orEmpty().trim()
        val phone = etPhone.text?.toString().orEmpty().trim()

        if (full.isBlank() || duty.isBlank() || unit.isBlank()) {
            tvProfileStatus.text = "Заполните минимум: ФИО, номер наряда, подразделение"
            return
        }

        tvProfileStatus.text = "Сохранение…"
        val profile = ProfileStore.Profile(full, duty, unit, pos, rank, phone)

        Thread {
            val ok = api.sendProfile(full, duty, unit, pos, rank, phone)
            runOnUiThread {
                if (ok) {
                    ProfileStore.save(this, profile)
                    tvProfileStatus.text = "Сохранено"
                    Toast.makeText(this, "Профиль сохранён", Toast.LENGTH_SHORT).show()
                    decideScreen()
                } else {
                    tvProfileStatus.text = "Ошибка сохранения (проверьте сервер/токен)"
                }
            }
        }.start()
    }

    private fun isTrackingOn(): Boolean = ForegroundLocationService.isTrackingOn(this)

    private fun toggleTracking() {
        val paired = !SecureStores.getDeviceToken(this).isNullOrBlank()
        val prof = ProfileStore.isComplete(this)
        if (!paired || !prof) {
            pendingSmartStart = false
            decideScreen()
            return
        }

        if (!isTrackingOn()) {
            // Smart start: show actionable diagnostics before turning on tracking.
            if (!hasLocationPermission()) {
                ensurePermissions()
                Toast.makeText(this, "Нужны разрешения на геолокацию", Toast.LENGTH_SHORT).show()
                return
            }
            smartStartPreflightAndStart()
        } else {
            pendingSmartStart = false
            stopTrackingFlow()
        }
    }

    private fun smartStartPreflightAndStart() {
        val issues = Survivability.collect(this)
            .filterNot { it.code == "filter" }
            .sortedBy { startIssueOrder(it.code) }

        if (issues.isNotEmpty()) {
            pendingSmartStart = true
            showSmartStartDialog(issues)
            return
        }

        pendingSmartStart = false
        startTrackingWithApiOrOffline()
    }

    private fun startIssueOrder(code: String): Int = when (code) {
        "no_location_permission" -> 0
        "no_notifications" -> 1
        "location_off" -> 2
        "no_network" -> 3
        "battery_optimizations" -> 4
        "no_background_location" -> 5
        else -> 9
    }

    private fun showSmartStartDialog(issues: List<Issue>) {
        val hasNet = issues.any { it.code == "no_network" }

        val view = ScrollView(this)
        val ll = LinearLayout(this)
        ll.orientation = LinearLayout.VERTICAL
        ll.setPadding(30, 10, 30, 10)
        for (issue in issues) ll.addView(problemRow(issue))
        view.addView(ll)

        val msg = if (hasNet) {
            "Сети нет — трекинг можно запустить в офлайн‑режиме (точки будут копиться и отправятся при появлении сети). Исправь проблемы или начни всё равно."
        } else {
            "Рекомендуется исправить проблемы, иначе трекинг может быть неточным или отключаться. Исправь или начни всё равно."
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Перед стартом")
            .setMessage(msg)
            .setView(view)
            .setNegativeButton("Отмена") { _, _ -> pendingSmartStart = false }
            .setNeutralButton("Диагностика") { _, _ ->
                // keep pendingSmartStart=true to auto-start after fixes
                startActivity(Intent(this, DiagnosticsActivity::class.java))
            }
            .setPositiveButton("Начать") { _, _ ->
                pendingSmartStart = false
                startTrackingWithApiOrOffline()
            }
            .show()
    }

    private fun stopTrackingFlow() {
        btnStartStop.isEnabled = false
        btnStartStop.text = "Стоп…"

        val sessionId = SessionStore.getSessionId(this)

        stopForegroundTracking()
        ForegroundLocationService.enqueueUpload(this) // final push

        Thread {
            val ok = api.stop(sessionId)
            SessionStore.setSessionId(this, null)
            runOnUiThread {
                btnStartStop.isEnabled = true
                Toast.makeText(this, if (ok) "Трекинг выключён" else "Стоп: ошибка (проверь сервер)", Toast.LENGTH_SHORT).show()
                updateHomeStatus()
            }
        }.start()
    }

    private fun startTrackingWithApiOrOffline() {
        // Android 13+: notifications permission strongly recommended for foreground service UX.
        // NOTE: NEARBY_WIFI_DEVICES is requested in ensurePermissions(); do not reference a local `need` here.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ensurePermissions()
            Toast.makeText(this, "Разрешите уведомления для стабильной работы", Toast.LENGTH_SHORT).show()
            return
        }

        btnStartStop.isEnabled = false
        btnStartStop.text = "Старт…"

        Thread {
            var lat: Double? = null
            var lon: Double? = null
            try {
                val loc = fused.lastLocation
                val l = com.google.android.gms.tasks.Tasks.await(loc)
                if (l != null) {
                    lat = l.latitude
                    lon = l.longitude
                }
            } catch (_: Exception) { /* ignore */ }

            val res = api.start(lat, lon)
            runOnUiThread {
                btnStartStop.isEnabled = true

                if (res.ok && !res.sessionId.isNullOrBlank()) {
                    SessionStore.setSessionId(this, res.sessionId)
                    startForegroundTracking()
                    Toast.makeText(this, "Трекинг включён", Toast.LENGTH_SHORT).show()
                } else {
                    val err = (res.error ?: "unknown")
                    val fatal = err.contains("HTTP 401") || err.contains("HTTP 403") || err.contains("No token", ignoreCase = true)

                    if (fatal) {
                        Toast.makeText(this, "Не удалось стартовать: токен недействителен. Перепривяжите устройство.", Toast.LENGTH_LONG).show()
                        btnStartStop.text = "Включить трекинг"
                        updateHomeStatus()
                        return@runOnUiThread
                    }

                    // OFFLINE START: keep collecting points; UploadWorker will create session later.
                    SessionStore.setSessionId(this, null)
                    startForegroundTracking()
                    Toast.makeText(this, "Трекинг включён (офлайн). Отправка при появлении сети.", Toast.LENGTH_LONG).show()
                }

                updateHomeStatus()
            }
        }.start()
    }

    private fun maybeAutoStartAfterFix() {
        if (!pendingSmartStart) return
        if (isTrackingOn()) { pendingSmartStart = false; return }

        val now = System.currentTimeMillis()
        if (now - lastAutoStartAt < 1500) return

        val paired = !SecureStores.getDeviceToken(this).isNullOrBlank()
        val prof = ProfileStore.isComplete(this)
        if (!paired || !prof) { pendingSmartStart = false; return }

        if (!hasLocationPermission()) return

        val issues = Survivability.collect(this).filterNot { it.code == "filter" }
        val blocking = issues.any { it.code == "no_location_permission" || it.code == "no_notifications" || it.code == "location_off" }
        if (blocking) return

        // Auto-start once user returned after fixing settings.
        pendingSmartStart = false
        lastAutoStartAt = now
        startTrackingWithApiOrOffline()
    }

    private fun startForegroundTracking() {
        val i = Intent(this, ForegroundLocationService::class.java).apply { action = ForegroundLocationService.ACTION_START }
        ContextCompat.startForegroundService(this, i)
    }

    private fun stopForegroundTracking() {
        val i = Intent(this, ForegroundLocationService::class.java).apply { action = ForegroundLocationService.ACTION_STOP }
        startService(i)
    }

    private fun doSos() {
        val paired = !SecureStores.getDeviceToken(this).isNullOrBlank()
        if (!paired) {
            Toast.makeText(this, "Сначала привяжите устройство", Toast.LENGTH_SHORT).show()
            decideScreen()
            return
        }

        if (!hasLocationPermission()) {
            ensurePermissions()
            Toast.makeText(this, "Нужны разрешения на геолокацию", Toast.LENGTH_SHORT).show()
            return
        }

        val noteInput = EditText(this)
        noteInput.hint = "Короткое сообщение (необязательно)"

        MaterialAlertDialogBuilder(this)
            .setTitle("Отправить SOS?")
            .setMessage("Админу сразу покажется большой экран SOS с вашими координатами.")
            .setView(noteInput)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Отправить") { _, _ ->
                val note = noteInput.text?.toString()?.trim()
                val wasOn = isTrackingOn()
                btnSos.isEnabled = false
                btnSos.text = "SOS…"

                Thread {
                    var lat: Double? = null
                    var lon: Double? = null
                    var acc: Float? = null
                    try {
                        val locTask = fused.lastLocation
                        val l = com.google.android.gms.tasks.Tasks.await(locTask)
                        if (l != null) {
                            lat = l.latitude
                            lon = l.longitude
                            acc = l.accuracy
                        }
                    } catch (_: Exception) { /* ignore */ }

                    val res = if (lat != null && lon != null) {
                        api.sos(lat, lon, acc, note)
                    } else {
                        api.sosLast(note)
                    }

                    runOnUiThread {
                        btnSos.isEnabled = true
                        btnSos.text = "SOS"
                        if (res.ok) {
                            Toast.makeText(this, "SOS отправлен (#${res.sosId ?: ""})", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "SOS: ошибка (${res.error ?: "unknown"})", Toast.LENGTH_LONG).show()
                        }
                        updateHomeStatus()
                        // Важно: SOS не должен останавливать трекинг (на некоторых OEM/Doze это может сбивать сервис).
                        if (wasOn) {
                            try { ForegroundLocationService.setTrackingOn(this, true) } catch (_: Exception) {}
                            try { startForegroundTracking() } catch (_: Exception) {}
                            try { ForegroundLocationService.enqueueUpload(this) } catch (_: Exception) {}
                        }
                    }
                }.start()
            }
            .show()
    }

    private fun resetPairing() {
        stopForegroundTracking()
        SessionStore.setSessionId(this, null)
        SecureStores.setDeviceToken(this, null)
        DeviceInfoStore.clear(this)
        ProfileStore.clear(this)
        TrackingModeStore.set(this, TrackingMode.NORMAL)
        ForegroundLocationService.setTrackingOn(this, false)
        Toast.makeText(this, "Сброшено", Toast.LENGTH_SHORT).show()
        decideScreen()
    }

    private fun updateHomeStatus() {
        val paired = !SecureStores.getDeviceToken(this).isNullOrBlank()
        val prof = ProfileStore.isComplete(this)

        // Tiles that do not depend on pairing state
        try {
            tvTileNet.text = getNetworkLabel()
            tvTileBattery.text = getBatteryLabel()
        } catch (_: Exception) {}

        if (!paired) {
            btnStartStop.text = "Включить трекинг"
            tvStatus.text = "Не привязано"
            tvHomeDebug.text = ""
            try {
                tvTileQueue.text = "0"
                tvTileAcc.text = "—"
                tvTileLast.text = "—"
                tvTileGps.text = getGpsLabel(trackingOn = false, lastGpsIso = "")
                renderProblems()
            } catch (_: Exception) {}
            return
        }

        val isOn = isTrackingOn()
        btnStartStop.text = if (isOn) "Выключить трекинг" else "Включить трекинг"
        btnStartStop.isEnabled = prof

        val st = StatusStore.read(this)
        val queue = st["queue"] as Int
        val lastGps = st["last_gps"] as String
        val lastUpload = st["last_upload"] as String
        val lastHealth = st["last_health"] as String
        val err = st["last_error"] as String

        // Home tiles
        try {
            tvTileQueue.text = queue.toString()
            tvTileAcc.text = StatusStore.getLastAccM(this)?.let { "${it.toInt()}м" } ?: "—"
            tvTileLast.text = when {
                lastUpload.isNotBlank() -> formatAgo(lastUpload)
                lastHealth.isNotBlank() -> formatAgo(lastHealth)
                else -> "—"
            }
            tvTileGps.text = getGpsLabel(trackingOn = isOn, lastGpsIso = lastGps)
            renderProblems()
        } catch (_: Exception) {}

        tvStatus.text = buildString {
            append(if (isOn) "Трекинг: ON" else "Трекинг: OFF")
            run {
                val stored = TrackingModeStore.get(this@MainActivity)
                val eff = if (stored == TrackingMode.AUTO) AutoModeController.getEffective(this@MainActivity) else stored
                val label = if (stored == TrackingMode.AUTO) "auto→${eff.id}" else stored.id
                append("\nРежим: $label")
            }
            append("\nОчередь: $queue")
            if (lastGps.isNotBlank()) append("\nGPS: ${formatAgo(lastGps)}")
            run {
                val lastAny = if (lastUpload.isNotBlank()) lastUpload else lastHealth
                if (lastAny.isNotBlank()) append("\nПоследняя отправка: ${formatAgo(lastAny)}")
            }
        }

        tvHomeDebug.text = if (err.isNotBlank()) "Ошибка: $err" else ""
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun ensurePermissions() {
        val need = mutableListOf<String>()

        if (!hasLocationPermission()) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION)
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        // Android 13+: permission for Wi‑Fi scanning (used for indoor fingerprints)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED
        ) {
            need.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            need.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (need.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, need.toTypedArray(), 701)
        }
    }


    private fun renderProblems() {
        try {
            llProblems.removeAllViews()
            val issues = Survivability.collect(this)
            if (issues.isEmpty()) {
                llProblems.addView(problemLine("Нет"))
            } else {
                for (issue in issues) llProblems.addView(problemRow(issue))
            }
        } catch (_: Exception) {}
    }

    private fun problemRow(issue: Issue): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        row.setPadding(0, 2, 0, 2)

        val tv = TextView(this)
        tv.text = "• ${issue.title}"
        tv.textSize = 13f
        val tvLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(tv, tvLp)

        if (issue.fix !is FixAction.None) {
            val btn = com.google.android.material.button.MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            )
            btn.text = "Исправить"
            btn.textSize = 12f
            btn.minHeight = 0
            btn.setPadding(18, 6, 18, 6)
            btn.setOnClickListener { performFix(issue.fix) }
            val blp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            blp.marginStart = 10
            row.addView(btn, blp)
        }

        return row
    }

    private fun performFix(fix: FixAction) {
        when (fix) {
            is FixAction.RequestPermissions -> SystemActions.requestPermissions(this, fix.perms, fix.requestCode)
            FixAction.OpenAppSettings -> SystemActions.openAppSettings(this)
            FixAction.OpenLocationSettings -> SystemActions.openLocationSettings(this)
            FixAction.OpenNotificationsSettings -> {
                // Prefer OS dialog (if possible), otherwise open settings.
                ensurePermissions()
                SystemActions.openNotificationsSettings(this)
            }
            FixAction.OpenInternetSettings -> SystemActions.openInternetSettings(this)
            FixAction.RequestIgnoreBatteryOpt -> SystemActions.requestIgnoreBatteryOptimizations(this)
            FixAction.OpenBackgroundLocationFlow -> SystemActions.startBackgroundLocationFlow(this)
            FixAction.None -> {}
        }
    }

    private fun problemLine(t: String): TextView {
        val tv = TextView(this)
        tv.text = t
        tv.textSize = 13f
        tv.setPadding(0, 2, 0, 2)
        return tv
    }

    private fun getNetworkLabel(): String {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val n = cm.activeNetwork ?: return "Нет"
            val caps = cm.getNetworkCapabilities(n) ?: return "Нет"
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cell"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Eth"
                else -> "?"
            }
        } catch (_: Exception) {
            "—"
        }
    }

    private fun getGpsLabel(trackingOn: Boolean, lastGpsIso: String): String {
        if (!hasLocationPermission()) return "perm"
        val enabled = try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            false
        }
        if (!enabled) return "off"
        if (trackingOn && lastGpsIso.isBlank()) return "wait"
        return "ok"
    }

    private fun getBatteryLabel(): String {
        return try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (pct in 0..100) "${pct}%" else "—"
        } catch (_: Exception) {
            "—"
        }
    }

    private fun formatAgo(iso: String): String {
        return try {
            val t = Instant.parse(iso)
            val now = Instant.now()
            val sec = (now.epochSecond - t.epochSecond).coerceAtLeast(0)
            when {
                sec < 10 -> "сейчас"
                sec < 60 -> "$sec s"
                sec < 3600 -> "${sec / 60} m"
                sec < 86400 -> "${sec / 3600} h"
                else -> "${sec / 86400} d"
            }
        } catch (_: Exception) {
            iso
        }
    }

}