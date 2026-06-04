package com.example.smartledger.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.smartledger.R
import com.example.smartledger.data.Electricity
import java.text.SimpleDateFormat
import java.util.Locale

class ElectricityAdapter(
    private val onNormalClick: (Electricity) -> Unit,
    private val onLongClick: () -> Unit,
    private val onSelectionChange: (Int) -> Unit
) : ListAdapter<Electricity, ElectricityAdapter.ViewHolder>(DiffCallback()) {

    private val selectedItems = mutableSetOf<Int>()
    private var isSelectionMode = false

    fun toggleSelection(position: Int) {
        val item = getItem(position)
        if (selectedItems.contains(item.id)) selectedItems.remove(item.id) else selectedItems.add(item.id)
        notifyItemChanged(position)
        onSelectionChange(selectedItems.size)
    }
    fun startSelectionMode(pos: Int?) {
        if(!isSelectionMode) { isSelectionMode = true; if(pos != null) toggleSelection(pos) else onSelectionChange(0); notifyItemRangeChanged(0, itemCount) }
    }
    fun endSelectionMode() { isSelectionMode = false; selectedItems.clear(); notifyItemRangeChanged(0, itemCount); onSelectionChange(0) }
    fun selectAll() { selectedItems.clear(); selectedItems.addAll(currentList.map { it.id }); notifyItemRangeChanged(0, itemCount); onSelectionChange(selectedItems.size) }
    fun deselectAll() { selectedItems.clear(); notifyItemRangeChanged(0, itemCount); onSelectionChange(0) }
    fun isAllSelected() = currentList.isNotEmpty() && selectedItems.size == currentList.size
    fun getSelectedItems() = currentList.filter { selectedItems.contains(it.id) }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_electricity, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, isSelectionMode, selectedItems.contains(item.id))

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
                            if (isSelectionMode) toggleSelection(position)
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
            touchListener.longClickConsumed = true
            if (!isSelectionMode) {
                onLongClick()
                startSelectionMode(position)
            }
            true
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvUnits: TextView = itemView.findViewById(R.id.tvUnits)
        val tvTotalUnits: TextView = itemView.findViewById(R.id.tvTotalUnits) // NEW
        val tvDates: TextView = itemView.findViewById(R.id.tvDates)
        val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        val checkbox: CheckBox = itemView.findViewById(R.id.checkbox)
        val cardView: CardView = itemView as CardView

        fun bind(item: Electricity, isSelectionMode: Boolean, isSelected: Boolean) {
            fun fmt(v: Double?): String = if (v == null) "-" else if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

            val sUnits = fmt(item.startUnits)
            val eUnits = fmt(item.endUnits)
            tvUnits.text = "$sUnits - $eUnits"

            val total =fmt(item.totalUnits)
            tvTotalUnits.text = " ($total Units)"

            val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
            tvDates.text = "${sdf.format(item.startDate)} - ${sdf.format(item.endDate)}"
            tvAmount.text = "Rs %.2f".format(item.amount ?: 0.0)

            if (isSelectionMode) {
                checkbox.visibility = View.VISIBLE; checkbox.isChecked = isSelected
                if (isSelected) {
                    cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.selected_item_bg))
                    tvUnits.setTextColor(Color.BLACK); tvDates.setTextColor(Color.DKGRAY)
                } else {
                    cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.tile_bg))
                    tvUnits.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_primary))
                    tvDates.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_secondary))
                }
            } else {
                checkbox.visibility = View.GONE
                cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.tile_bg))
                tvUnits.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_primary))
                tvDates.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_secondary))
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Electricity>() {
        override fun areItemsTheSame(old: Electricity, new: Electricity) = old.id == new.id
        override fun areContentsTheSame(old: Electricity, new: Electricity) = old == new
    }
}