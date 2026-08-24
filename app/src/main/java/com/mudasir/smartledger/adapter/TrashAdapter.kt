package com.mudasir.smartledger.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mudasir.smartledger.R
import com.mudasir.smartledger.data.TrashItem
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class TrashAdapter(
    private val onRecoverClick: (TrashItem) -> Unit,
    private val onDeleteClick: (TrashItem) -> Unit,
    private val onLongClick: () -> Unit,
    private val onSelectionChange: (Int) -> Unit
) : ListAdapter<TrashItem, TrashAdapter.TrashViewHolder>(TrashDiffCallback()) {

    private val selectedItems = mutableSetOf<TrashItem>()
    private var isSelectionMode = false

    fun toggleSelection(position: Int) {
        val item = getItem(position)
        if (selectedItems.contains(item)) selectedItems.remove(item) else selectedItems.add(item)
        notifyItemChanged(position)
        onSelectionChange(selectedItems.size)
    }

    fun startSelectionMode(position: Int? = null) {
        if (!isSelectionMode) {
            isSelectionMode = true
            if (position != null) toggleSelection(position) else onSelectionChange(0)
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
        selectedItems.addAll(currentList)
        notifyItemRangeChanged(0, itemCount)
        onSelectionChange(selectedItems.size)
    }

    fun deselectAll() {
        selectedItems.clear()
        notifyItemRangeChanged(0, itemCount)
        onSelectionChange(0)
    }

    fun isAllSelected(): Boolean = currentList.isNotEmpty() && selectedItems.size == currentList.size
    fun getSelectedItems(): List<TrashItem> = selectedItems.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrashViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_trash, parent, false)
        return TrashViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrashViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, isSelectionMode, selectedItems.contains(item))

        holder.itemView.setOnClickListener {
            if (isSelectionMode) toggleSelection(position)
        }
        holder.itemView.setOnLongClickListener {
            if (!isSelectionMode) { onLongClick(); startSelectionMode(position) }
            true
        }
        holder.btnRecover.setOnClickListener { onRecoverClick(item) }
        holder.btnDelete.setOnClickListener { onDeleteClick(item) }
    }

    class TrashViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvDaysLeft: TextView = itemView.findViewById(R.id.tvDaysLeft)
        val checkbox: CheckBox = itemView.findViewById(R.id.checkbox)
        val cardView: CardView = itemView as CardView
        val layoutActions: LinearLayout = itemView.findViewById(R.id.layoutActions)
        val btnRecover: ImageView = itemView.findViewById(R.id.btnRecover)
        val btnDelete: ImageView = itemView.findViewById(R.id.btnDelete)
        val tvBadge: TextView = itemView.findViewById(R.id.tvBadge)

        fun bind(item: TrashItem, isSelectionMode: Boolean, isSelected: Boolean) {
            val sdf = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())

            when(item) {
                is TrashItem.ElectricityItem -> {
                    tvBadge.text = "Electricity"
                    val sDate =
                        SimpleDateFormat("dd MMM", Locale.getDefault()).format(item.data.startDate)
                    val eDate =
                        SimpleDateFormat("dd MMM", Locale.getDefault()).format(item.data.endDate)
                    tvTitle.text = "$sDate - $eDate"
                    val amt = item.data.amount ?: 0.0
                    tvAmount.text = "Rs %.2f".format(amt)
                    tvDate.text = "${item.data.totalUnits ?: 0.0} Units"
                }
                    is TrashItem.MilkItem -> {
                    tvBadge.text = "Milk"
                    tvTitle.text = item.data.monthName
                    tvAmount.text = "Rs %.2f".format(item.data.totalAmount)
                    tvDate.text = "${item.data.totalLiters} Liters"
                }
                is TrashItem.ExpenseItem -> {
                    tvBadge.text = "Expense"
                    tvTitle.text = item.data.title
                    tvAmount.text = "Rs %.2f".format(item.data.amount)
                    tvDate.text = sdf.format(item.data.date)
                }
                is TrashItem.CustomEntryItem -> {
                    tvBadge.text = item.ledgerName
                    val dataMap = try { Gson().fromJson(item.entry.dataJson, Map::class.java) } catch(e: Exception) { null }
                    val userFields = dataMap?.filterKeys { it != "SYS_END_DATE" }
                    val firstNonEmpty = userFields?.values?.firstOrNull { it.toString().trim().isNotEmpty() }
                    tvTitle.text = firstNonEmpty?.toString() ?: item.ledgerName
                    tvAmount.text = "Rs ${item.entry.amount ?: 0.0}"
                    tvDate.text = sdf.format(item.entry.date)
                }
                is TrashItem.TrashedLedgerItem -> {
                    tvBadge.text = "Ledger"
                    tvTitle.text = item.ledger.name
                    tvAmount.text = "${item.entryCount} Record(s)"
                    tvAmount.setTextColor(ContextCompat.getColor(itemView.context, R.color.teal_main))
                    tvDate.text = sdf.format(item.ledger.createdAt)
                }
                is TrashItem.CustomDailyRecordItem -> {
                    tvBadge.text = item.ledgerName
                    tvTitle.text = item.record.monthName
                    tvAmount.text = "Rs ${item.record.totalAmount.toInt()}"
                    tvDate.text = "${item.record.dailyEntries.size} Days"
                }

            }

            val deletedAt = item.deletedAt ?: System.currentTimeMillis()
            val expiryTime = deletedAt + TimeUnit.DAYS.toMillis(15)
            val remainingMillis = expiryTime - System.currentTimeMillis()
            val totalDaysLeft = TimeUnit.MILLISECONDS.toDays(remainingMillis)
            val totalHoursLeft = TimeUnit.MILLISECONDS.toHours(remainingMillis)
            val totalMinutesLeft = TimeUnit.MILLISECONDS.toMinutes(remainingMillis)

            when {
                remainingMillis <= 0 -> {
                    tvDaysLeft.text = "Expiring Soon..."
                    tvDaysLeft.setTextColor(Color.RED)
                }
                totalHoursLeft < 1 -> {
                    tvDaysLeft.text = "$totalMinutesLeft Mins Left"
                    tvDaysLeft.setTextColor(ContextCompat.getColor(itemView.context, R.color.teal_main))
                    tvDaysLeft.setTypeface(null, android.graphics.Typeface.BOLD)
                }
                totalHoursLeft < 24 -> {
                    tvDaysLeft.text = "$totalHoursLeft Hours Left"
                    tvDaysLeft.setTextColor(ContextCompat.getColor(itemView.context, R.color.teal_main))
                    tvDaysLeft.setTypeface(null, android.graphics.Typeface.BOLD)
                }
                else -> {
                    tvDaysLeft.text = "$totalDaysLeft Days Left"
                    tvDaysLeft.setTextColor(ContextCompat.getColor(itemView.context, R.color.teal_main))
                    tvDaysLeft.setTypeface(null, android.graphics.Typeface.BOLD)
                }
            }

            if (isSelectionMode) {
                checkbox.visibility = View.VISIBLE; layoutActions.visibility = View.GONE; checkbox.isChecked = isSelected
                if (isSelected) {
                    cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.selected_item_bg))
                    tvTitle.setTextColor(Color.BLACK); tvDate.setTextColor(Color.DKGRAY)
                } else {
                    cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.tile_bg))
                    tvTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_primary))
                    tvDate.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_secondary))
                }
            } else {
                checkbox.visibility = View.GONE; layoutActions.visibility = View.VISIBLE
                cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.tile_bg))
                tvTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_primary))
                tvDate.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_secondary))
            }
        }
    }

    class TrashDiffCallback : DiffUtil.ItemCallback<TrashItem>() {
        override fun areItemsTheSame(oldItem: TrashItem, newItem: TrashItem): Boolean {
            return when {
                oldItem is TrashItem.ExpenseItem && newItem is TrashItem.ExpenseItem ->
                    oldItem.data.id == newItem.data.id
                oldItem is TrashItem.ElectricityItem && newItem is TrashItem.ElectricityItem ->
                    oldItem.data.id == newItem.data.id
                oldItem is TrashItem.MilkItem && newItem is TrashItem.MilkItem ->
                    oldItem.data.id == newItem.data.id
                oldItem is TrashItem.TrashedLedgerItem && newItem is TrashItem.TrashedLedgerItem ->
                    oldItem.ledger.id == newItem.ledger.id
                oldItem is TrashItem.CustomDailyRecordItem && newItem is TrashItem.CustomDailyRecordItem ->
                    oldItem.record.id == newItem.record.id
                else -> false
            }
        }
        override fun areContentsTheSame(oldItem: TrashItem, newItem: TrashItem) = oldItem == newItem
    }
}