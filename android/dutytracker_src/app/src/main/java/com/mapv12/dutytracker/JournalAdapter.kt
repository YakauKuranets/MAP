package com.mapv12.dutytracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JournalAdapter : RecyclerView.Adapter<JournalAdapter.VH>() {

    private val items = mutableListOf<EventJournalEntity>()
    private val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun submit(list: List<EventJournalEntity>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_journal, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], sdf)
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val tvTime: TextView = v.findViewById(R.id.tv_time)
        private val tvBadge: TextView = v.findViewById(R.id.tv_badge)
        private val tvOk: TextView = v.findViewById(R.id.tv_ok)
        private val tvEndpoint: TextView = v.findViewById(R.id.tv_endpoint)
        private val tvMessage: TextView = v.findViewById(R.id.tv_message)

        fun bind(e: EventJournalEntity, sdf: SimpleDateFormat) {
            tvTime.text = sdf.format(Date(e.tsEpochMs))
            tvBadge.text = e.kind.uppercase(Locale.getDefault())
            tvOk.text = if (e.ok) "OK" else "ERR"
            val code = e.statusCode?.toString() ?: "-"
            tvEndpoint.text = "${e.endpoint}  (code $code)"
            val msg = e.message?.trim().orEmpty()
            tvMessage.text = if (msg.isBlank()) (e.extra?.trim().orEmpty()) else msg
        }
    }
}
