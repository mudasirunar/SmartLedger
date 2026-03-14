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

    fun startSelectionMode(position: Int? = null) {
        if (!isSelectionMode) {
            isSelectionMode = true
            if (position != null) {
                toggleSelection(position)
            } else {
                onSelectionChange(0)
            }

            notifyItemRangeChanged(0, itemCount)
        }
    }

    fun getSelectedExpenses(): List<Expense> {
        return currentList.filter { selectedItems.contains(it.id) }
    }

    fun endSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
        notifyItemRangeChanged(0, itemCount)
        onSelectionChange(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_expense, parent, false)
        return ExpenseViewHolder(view)
    }

    fun isAllSelected(): Boolean {
        return currentList.isNotEmpty() && selectedItems.size == currentList.size
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

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        val expense = getItem(position)
        holder.bind(expense, isSelectionMode, selectedItems.contains(expense.id))

        holder.itemView.setOnClickListener {
            if (isSelectionMode) {
                toggleSelection(position)
            } else {
                onNormalClick(expense)
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
                    cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.selected_item_bg))
                    tvTitle.setTextColor(Color.BLACK)
                    tvDate.setTextColor(Color.DKGRAY)
                } else {
                    cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.tile_bg))
                    tvTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_primary))
                    tvDate.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_secondary))
                }
            } else {
                checkbox.visibility = View.GONE
                cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.tile_bg))
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
