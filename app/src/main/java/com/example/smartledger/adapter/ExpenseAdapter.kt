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
import com.example.smartledger.data.Expense
import java.text.SimpleDateFormat
import java.util.Locale

class ExpenseAdapter(
    private val onNormalClick: (Expense) -> Unit,
    private val onLongClick: () -> Unit,
    private val onSelectionChange: (Int) -> Unit
) : ListAdapter<Expense, ExpenseAdapter.ExpenseViewHolder>(ExpenseDiffCallback()) {

    private val selectedItems = mutableSetOf<Int>()
    private var isSelectionMode = false

    // Toggles the selection state for a given position
    fun toggleSelection(position: Int) {
        val expense = getItem(position)
        if (selectedItems.contains(expense.id)) {
            selectedItems.remove(expense.id)
        } else {
            selectedItems.add(expense.id)
        }
        notifyItemChanged(position)
        onSelectionChange(selectedItems.size)
    }

    // Starts selection mode
    fun startSelectionMode(position: Int? = null) {
        if (!isSelectionMode) {
            isSelectionMode = true

            // Only select an item if a specific position was passed (Long press)
            // If position is null (Menu click), we just enter the mode with 0 selected
            if (position != null) {
                toggleSelection(position)
            } else {
                // Notify main activity that 0 are selected initially
                onSelectionChange(0)
            }

            // Refresh list to show checkboxes
            notifyItemRangeChanged(0, itemCount)
        }
    }

    // Add this inside ExpenseAdapter class
    fun getSelectedExpenses(): List<Expense> {
        // Filter the current list to find the objects that match the selected IDs
        return currentList.filter { selectedItems.contains(it.id) }
    }

    // Ends selection mode and clears selections
    fun endSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
        notifyItemRangeChanged(0, itemCount)
        onSelectionChange(0)
    }

    // Gets the IDs of all selected expenses
    fun getSelectedExpenseIds(): List<Int> {
        return selectedItems.toList()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_expense, parent, false)
        return ExpenseViewHolder(view)
    }

    // 1. Check if everything is currently selected
    fun isAllSelected(): Boolean {
        return currentList.isNotEmpty() && selectedItems.size == currentList.size
    }

    // 2. Select Everything
    fun selectAll() {
        selectedItems.clear()
        // currentList is a built-in property of ListAdapter
        selectedItems.addAll(currentList.map { it.id })
        notifyItemRangeChanged(0, itemCount)
        onSelectionChange(selectedItems.size)
    }

    // 3. Deselect Everything (but keep mode open)
    fun deselectAll() {
        selectedItems.clear()
        notifyItemRangeChanged(0, itemCount)
        onSelectionChange(0)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        val expense = getItem(position)
        holder.bind(expense, isSelectionMode, selectedItems.contains(expense.id))

        // Handle clicks
        holder.itemView.setOnClickListener {
            if (isSelectionMode) {
                toggleSelection(position)
            } else {
                onNormalClick(expense)
            }
        }

        // Handle long press to start selection mode
        holder.itemView.setOnLongClickListener {
            if (!isSelectionMode) {
                onLongClick() // Notify activity to start action mode
                startSelectionMode(position)
            }
            true
        }
    }

    class ExpenseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val checkbox: CheckBox = itemView.findViewById(R.id.checkbox)
        private val cardView: CardView = itemView as CardView

        fun bind(expense: Expense, isSelectionMode: Boolean, isSelected: Boolean) {
            tvTitle.text = if (expense.title.isEmpty()) "(No Title)" else expense.title
            tvAmount.text = "Rs %.2f".format(expense.amount)

            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            tvDate.text = sdf.format(expense.date)

            if (isSelectionMode) {
                checkbox.visibility = View.VISIBLE
                checkbox.isChecked = isSelected

                if (isSelected) {
                    // 1. SET BACKGROUND TO OUR SPECIFIC COLOR (Light Teal)
                    cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.selected_item_bg))

                    // 2. FORCE TEXT TO BLACK (So it's readable on light background)
                    tvTitle.setTextColor(Color.BLACK)
                    tvDate.setTextColor(Color.DKGRAY)
                } else {
                    // Unselected state (Standard Theme Colors)
                    cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.tile_bg))

                    // Restore default text colors based on Theme (White in Dark Mode, Black in Light Mode)
                    tvTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_primary))
                    tvDate.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_secondary))
                }
            } else {
                // Normal Mode (No checkboxes)
                checkbox.visibility = View.GONE
                cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.tile_bg))

                // Restore default text colors
                tvTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_primary))
                tvDate.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_secondary))
            }
        }
    }

    class ExpenseDiffCallback : DiffUtil.ItemCallback<Expense>() {
        override fun areItemsTheSame(oldItem: Expense, newItem: Expense) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Expense, newItem: Expense) = oldItem == newItem
    }
}
