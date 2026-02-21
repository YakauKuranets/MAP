package com.mapv12.dutytracker

import android.os.Bundle
import android.text.format.DateFormat
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private var gmap: GoogleMap? = null
    private var currentMarker: Marker? = null
    private var accuracyCircle: Circle? = null
    private var trackPolyline: Polyline? = null

    private var windowMinutes: Int = 15

    private lateinit var tvInfo: TextView
    private lateinit var chips: ChipGroup
    private lateinit var btnCenter: MaterialButton
    private lateinit var btnRefresh: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Карта"

        tvInfo = findViewById(R.id.tv_map_info)
        chips = findViewById(R.id.chips_window)
        btnCenter = findViewById(R.id.btn_center)
        btnRefresh = findViewById(R.id.btn_refresh)

        chips.check(R.id.chip_15)
        chips.setOnCheckedStateChangeListener { _, checkedIds ->
            windowMinutes = when (checkedIds.firstOrNull()) {
                R.id.chip_5 -> 5
                R.id.chip_60 -> 60
                else -> 15
            }
            refresh()
        }

        btnRefresh.setOnClickListener { refresh() }
        btnCenter.setOnClickListener { centerOnLast() }

        val frag = supportFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment
        frag.getMapAsync(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onMapReady(map: GoogleMap) {
        gmap = map
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = false
        refresh()
    }

    private fun refresh() {
        val map = gmap ?: return

        lifecycleScope.launch {
            val (points, last) = withContext(Dispatchers.IO) {
                val dao = App.db.trackPointDao()
                val since = System.currentTimeMillis() - windowMinutes * 60_000L
                val pts = dao.loadSinceLimited(sinceMs = since, limit = 2000)
                val l = pts.lastOrNull() ?: dao.loadLast()
                Pair(pts, l)
            }

            render(map, points, last)
        }
    }

    private fun render(map: GoogleMap, points: List<TrackPointEntity>, last: TrackPointEntity?) {
        // Clear previous overlays, but keep map object
        trackPolyline?.remove()
        currentMarker?.remove()
        accuracyCircle?.remove()
        trackPolyline = null
        currentMarker = null
        accuracyCircle = null

        if (last == null) {
            tvInfo.text = "Нет точек трека"
            return
        }

        val lastLatLng = LatLng(last.lat, last.lon)

        // Draw tail
        if (points.isNotEmpty()) {
            val path = points.map { LatLng(it.lat, it.lon) }
            trackPolyline = map.addPolyline(
                PolylineOptions()
                    .addAll(path)
                    .width(10f)
            )
        }

        // Marker + accuracy circle
        currentMarker = map.addMarker(
            MarkerOptions()
                .position(lastLatLng)
                .title("Текущая позиция")
        )

        val acc = (last.accuracyM ?: 25.0).coerceIn(5.0, 500.0)
        accuracyCircle = map.addCircle(
            CircleOptions()
                .center(lastLatLng)
                .radius(acc)
                .strokeWidth(3f)
        )

        val time = DateFormat.format("HH:mm:ss", last.tsEpochMs)
        val tail = windowMinutes
        val count = points.size
        tvInfo.text = "Последняя: ${'$'}time • точность ~${'$'}{acc.toInt()}м • хвост: ${'$'}tail мин • точек: ${'$'}count"
    }

    private fun centerOnLast() {
        val map = gmap ?: return
        lifecycleScope.launch {
            val last = withContext(Dispatchers.IO) {
                App.db.trackPointDao().loadLast()
            } ?: return@launch

            val acc = (last.accuracyM ?: 25.0)
            val zoom = when {
                acc <= 15 -> 17f
                acc <= 40 -> 16f
                acc <= 120 -> 15f
                else -> 14f
            }

            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(last.lat, last.lon), zoom))
        }
    }
}
