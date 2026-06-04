package com.example.smartledger.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.smartledger.R
import com.example.smartledger.data.CustomDailyRecord
import com.example.smartledger.data.CustomEntry
import com.example.smartledger.data.CustomLedger
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GenericAdapter(
    private val ledger: CustomLedger,
    private val onNormalClick: (CustomEntry) -> Unit,
    private val onLongClick: () -> Unit,
    private val onSelectionChange: (Int) -> Unit
) : ListAdapter<CustomEntry, GenericAdapter.ViewHolder>(DiffCallback()) {

    private val selectedItems = mutableSetOf<CustomEntry>()
    private var isSelectionMode = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_generic_record, parent, false)
        return ViewHolder(view)
    }

    fun startSelectionMode(target: CustomEntry?) {
        isSelectionMode = true
        target?.let { selectedItems.add(it) }
        notifyDataSetChanged()
        onSelectionChange(selectedItems.size)
    }

    fun endSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
        notifyDataSetChanged()
        onSelectionChange(0)
    }

    fun toggleSelection(item: CustomEntry) {
        if (selectedItems.contains(item)) selectedItems.remove(item)
        else selectedItems.add(item)
        notifyItemChanged(currentList.indexOf(item))
        onSelectionChange(selectedItems.size)
    }

    fun selectAll() {
        selectedItems.clear()
        selectedItems.addAll(currentList)
        notifyDataSetChanged()
        onSelectionChange(selectedItems.size)
    }

    fun deselectAll() {
        selectedItems.clear()
        notifyDataSetChanged()
        onSelectionChange(0)
    }

    fun isAllSelected() = selectedItems.size == currentList.size
    fun getSelectedItems() = selectedItems.toList()

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, ledger, isSelectionMode, selectedItems.contains(item))

        // Movement-aware touch listener to distinguish between a tap and a swipe/scroll
        val touchListener = object : View.OnTouchListener {
            private var startX = 0f
            private var startY = 0f
            private val touchSlop = 20
            var longClickConsumed = false

            override fun onTouch(v: View, event: android.view.MotionEvent): Boolean {
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        startX = event.x
                        startY = event.y
                        longClickConsumed = false
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        val dx = Math.abs(event.x - startX)
                        val dy = Math.abs(event.y - startY)
                        if (dx < touchSlop && dy < touchSlop && !longClickConsumed) {
                            if (isSelectionMode) toggleSelection(item)
                            else onNormalClick(item)
                            v.performClick()
                        }
                    }
                }
                return false
            }
        }

        holder.itemView.setOnTouchListener(touchListener)

        holder.itemView.setOnLongClickListener {
            if (!isSelectionMode) {
                touchListener.longClickConsumed = true
                startSelectionMode(item)
                onLongClick()
            }
            true
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvPrimary: TextView = itemView.findViewById(R.id.tvPrimaryInfo)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivIcon)
        private val checkbox: CheckBox = itemView.findViewById(R.id.checkbox)
        private val cardView: MaterialCardView = itemView as MaterialCardView

        fun bind(item: CustomEntry, ledger: CustomLedger, isSelectionMode: Boolean, isSelected: Boolean) {
            val context = itemView.context
            val type = object : TypeToken<Map<String, String>>() {}.type
            val dataMap: Map<String, String>? = try {
                Gson().fromJson(item.dataJson, type)
            } catch (e: Exception) { null }
            val sdfDayMonth = SimpleDateFormat("dd MMM", Locale.getDefault())
            val sdfFull = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())

            if (ledger.dateMode == com.example.smartledger.data.DateMode.RANGE) {
                val startDateStr = sdfDayMonth.format(Date(item.date))
                val endDateMs = dataMap?.get("SYS_END_DATE")?.toLongOrNull()
                if (endDateMs != null) {
                    tvDate.text = "$startDateStr - ${sdfDayMonth.format(Date(endDateMs))}"
                } else {
                    tvDate.text = sdfFull.format(Date(item.date))
                }
            } else if (ledger.dateMode == com.example.smartledger.data.DateMode.MONTH) {
                tvDate.text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(item.date))
            } else {
                tvDate.text = sdfFull.format(Date(item.date))
            }

            tvAmount.text = if (item.amount != null) "Rs ${item.amount}" else "Rs 0"

            val iconResId = context.resources.getIdentifier(ledger.iconName, "drawable", context.packageName)
            ivIcon.setImageResource(if (iconResId != 0) iconResId else R.drawable.ic_star)

            val userFields = dataMap?.filterKeys { it != "SYS_END_DATE" }

            val firstNonEmpty = userFields?.values?.firstOrNull { it.trim().isNotEmpty() }
            tvPrimary.text = firstNonEmpty ?: ledger.name

            if (isSelectionMode) {
                checkbox.visibility = View.VISIBLE
                checkbox.isChecked = isSelected

                if (isSelected) {
                    cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.selected_item_bg))
                    tvPrimary.setTextColor(android.graphics.Color.BLACK)
                    tvDate.setTextColor(android.graphics.Color.DKGRAY)
                } else {
                    cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.tile_bg))
                    tvPrimary.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    tvDate.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                }
            } else {
                checkbox.visibility = View.GONE
                cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.tile_bg))
                tvPrimary.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                tvDate.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<CustomEntry>() {
        override fun areItemsTheSame(old: CustomEntry, new: CustomEntry) = old.id == new.id
        override fun areContentsTheSame(old: CustomEntry, new: CustomEntry) = old == new
    }
}


class DailyRecordAdapter(
    private val ledger: CustomLedger,
    private val onItemClick: (CustomDailyRecord) -> Unit,
    private val onLongClick: () -> Unit,
    private val onSelectionChange: (Int) -> Unit
) : ListAdapter<CustomDailyRecord, DailyRecordAdapter.ViewHolder>(DiffCallback()) {

    private val selectedItems = mutableSetOf<CustomDailyRecord>()
    private var isSelectionMode = false

    fun startSelectionMode(target: CustomDailyRecord?) {
        isSelectionMode = true
        target?.let { selectedItems.add(it) }
        notifyDataSetChanged()
        onSelectionChange(selectedItems.size)
    }

    fun endSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
        notifyDataSetChanged()
        onSelectionChange(0)
    }

    fun toggleSelection(item: CustomDailyRecord) {
        if (selectedItems.contains(item)) selectedItems.remove(item)
        else selectedItems.add(item)
        notifyItemChanged(currentList.indexOf(item))
        onSelectionChange(selectedItems.size)
    }

    fun selectAll() {
        selectedItems.clear()
        selectedItems.addAll(currentList)
        notifyDataSetChanged()
        onSelectionChange(selectedItems.size)
    }

    fun deselectAll() {
        selectedItems.clear()
        notifyDataSetChanged()
        onSelectionChange(0)
    }

    fun isAllSelected() = selectedItems.size == currentList.size
    fun getSelectedItems() = selectedItems.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_generic_record, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, ledger, isSelectionMode, selectedItems.contains(item))

        // Movement-aware touch listener to distinguish between a tap and a swipe/scroll
        holder.itemView.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0f
            private var startY = 0f
            private val touchSlop = 20 // 20 pixels threshold

            override fun onTouch(v: View, event: android.view.MotionEvent): Boolean {
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        startX = event.x
                        startY = event.y
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        val dx = Math.abs(event.x - startX)
                        val dy = Math.abs(event.y - startY)
                        if (dx < touchSlop && dy < touchSlop) {
                            if (isSelectionMode) toggleSelection(item)
                            else onItemClick(item)
                            v.performClick()
                        }
                    }
                }
                return false // Return false to allow other interactions like long click
            }
        })

        holder.itemView.setOnLongClickListener {
            if (!isSelectionMode) {
                startSelectionMode(item)
                onLongClick()
            }
            true
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvPrimary: TextView = itemView.findViewById(R.id.tvPrimaryInfo)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        private val checkbox: CheckBox = itemView.findViewById(R.id.checkbox)
        private val cardView: MaterialCardView = itemView as MaterialCardView

        fun bind(record: CustomDailyRecord, ledger: CustomLedger, isSelectionMode: Boolean, isSelected: Boolean) {
            val context = itemView.context

            tvPrimary.text = record.monthName
            tvAmount.text = "Rs ${record.totalAmount}"

            val totals = DoubleArray(ledger.fields.size)
            for (entry in record.dailyEntries) {
                for (i in entry.values.indices) {
                    if (i < totals.size) totals[i] += entry.values[i]
                }
            }

            val summaryList = mutableListOf<String>()
            ledger.fields.forEachIndexed { index, field ->
                val value = totals.getOrNull(index) ?: 0.0
                val formattedValue = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
                summaryList.add("$formattedValue ${field.fieldName}")
            }
            tvDate.text = if (summaryList.isEmpty()) "No fields defined" else summaryList.joinToString(", ")

            if (isSelectionMode) {
                checkbox.visibility = View.VISIBLE
                checkbox.isChecked = isSelected

                if (isSelected) {
                    cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.selected_item_bg))
                    tvPrimary.setTextColor(android.graphics.Color.BLACK)
                    tvDate.setTextColor(android.graphics.Color.DKGRAY)
                } else {
                    cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.tile_bg))
                    tvPrimary.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    tvDate.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                }
            } else {
                checkbox.visibility = View.GONE
                cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.tile_bg))
                tvPrimary.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                tvDate.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<CustomDailyRecord>() {
        override fun areItemsTheSame(o: CustomDailyRecord, n: CustomDailyRecord) = o.id == n.id
        override fun areContentsTheSame(o: CustomDailyRecord, n: CustomDailyRecord) = o == n
    }
}