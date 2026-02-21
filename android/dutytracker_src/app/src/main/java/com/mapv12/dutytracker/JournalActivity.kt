package com.mapv12.dutytracker

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JournalActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var adapter: JournalAdapter
    private lateinit var chips: ChipGroup

    private var all: List<EventJournalEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_journal)

        val tb = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(tb)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        tb.setNavigationOnClickListener { finish() }

        adapter = JournalAdapter()
        findViewById<RecyclerView>(R.id.rv).apply {
            layoutManager = LinearLayoutManager(this@JournalActivity)
            adapter = this@JournalActivity.adapter
        }

        chips = findViewById(R.id.chips)
        chips.setOnCheckedStateChangeListener { _, _ -> applyFilter() }

        load()
    }

    private fun load() {
        scope.launch {
            all = withContext(Dispatchers.IO) {
                try { App.db.eventJournalDao().last(500) } catch (_: Exception) { emptyList() }
            }
            applyFilter()
        }
    }

    private fun applyFilter() {
        val checkedId = chips.checkedChipId
        val filtered = when (checkedId) {
            R.id.chip_api -> all.filter { (it.kind ?: "").equals("api", ignoreCase = true) }
            R.id.chip_worker -> all.filter { (it.kind ?: "").equals("worker", ignoreCase = true) }
            R.id.chip_watchdog -> all.filter { (it.kind ?: "").equals("watchdog", ignoreCase = true) }
            R.id.chip_mode -> all.filter { (it.kind ?: "").equals("mode", ignoreCase = true) }
            R.id.chip_err -> all.filter { !it.ok }
            else -> all
        }
        // latest first already (dao returns DESC)
        adapter.submit(filtered)
    }
}
