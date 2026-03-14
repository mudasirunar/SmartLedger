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
import com.example.smartledger.data.MilkRecord

class MilkMonthAdapter(
    private val onNormalClick: (MilkRecord) -> Unit,
    private val onLongClick: () -> Unit,
    private val onSelectionChange: (Int) -> Unit
) : ListAdapter<MilkRecord, MilkMonthAdapter.ViewHolder>(DiffCallback()) {

    private val selectedItems = mutableSetOf<Int>()
    private var isSelectionMode = false


    fun toggleSelection(position: Int) {
        val item = getItem(position)
        if (selectedItems.contains(item.id)) {
            selectedItems.remove(item.id)
        } else {
            selectedItems.add(item.id)
        }
        notifyItemChanged(position)
        onSelectionChange(selectedItems.size)
    }

    fun startSelectionMode(pos: Int? = null) {
        if (!isSelectionMode) {
            isSelectionMode = true
            if (pos != null) {
                toggleSelection(pos)
            } else {
                onSelectionChange(0)
            }
            notifyItemRangeChanged(0, itemCount)
        }
    }

    fun endSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
        notifyItemRangeChanged(0, itemCount)
        onSelectionChange(0)
    }

    fun selectAll() {
        selectedItems.clear()
        selectedItems.addAll(currentList.map { it.id })
        notifyItemRangeChanged(0, itemCount)
        onSelectionChange(selectedItems.size)
    }

    fun deselectAll() {
        selectedItems.clear()
        notifyItemRangeChanged(0, itemCount)
        onSelectionChange(0)
    }

    fun isAllSelected(): Boolean {
        return currentList.isNotEmpty() && selectedItems.size == currentList.size
    }

    fun getSelectedItems(): List<MilkRecord> {
        return currentList.filter { selectedItems.contains(it.id) }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_milk_month, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, isSelectionMode, selectedItems.contains(item.id))

        holder.itemView.setOnClickListener {
            if (isSelectionMode) {
                toggleSelection(position)
            } else {
                onNormalClick(item)
            }
        }

        holder.itemView.setOnLongClickListener {
            if (!isSelectionMode) {
                onLongClick()
                startSelectionMode(position)
            }
            true
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMonth: TextView = itemView.findViewById(R.id.tvMonth)
        private val tvLiters: TextView = itemView.findViewById(R.id.tvLiters)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        private val checkbox: CheckBox = itemView.findViewById(R.id.checkbox)
        private val cardView: CardView = itemView as CardView

        fun bind(item: MilkRecord, isSelectionMode: Boolean, isSelected: Boolean) {
            tvMonth.text = item.monthName
            tvLiters.text = "${item.totalLiters} Liters"
            tvAmount.text = "Rs %.0f".format(item.totalAmount)

            if (isSelectionMode) {
                checkbox.visibility = View.VISIBLE
                checkbox.isChecked = isSelected

                if (isSelected) {
                    cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.selected_item_bg))
                    tvMonth.setTextColor(Color.BLACK)
                    tvLiters.setTextColor(Color.DKGRAY)
                } else {
                    cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.tile_bg))
                    tvMonth.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_primary))
                    tvLiters.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_secondary))
                }
            } else {
                checkbox.visibility = View.GONE
                cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.tile_bg))

                tvMonth.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_primary))
                tvLiters.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_secondary))
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<MilkRecord>() {
        override fun areItemsTheSame(oldItem: MilkRecord, newItem: MilkRecord) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: MilkRecord, newItem: MilkRecord) = oldItem == newItem
    }
}