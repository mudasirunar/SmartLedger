package com.example.smartledger.adapter

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smartledger.R
import com.example.smartledger.data.DailyEntry

class MilkDailyAdapter(
    private val dailyEntries: List<DailyEntry>,
    private val pricePerLiter: Double,
    private val onDataChanged: () -> Unit
) : RecyclerView.Adapter<MilkDailyAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_milk_day, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = dailyEntries[position]
        holder.tvDay.text = "Day ${entry.day}"
        if (holder.currentWatcher != null) {
            holder.etLiters.removeTextChangedListener(holder.currentWatcher)
        }
        if (entry.liters == 0.0) {
            holder.etLiters.setText("")
        } else {
            val text = if (entry.liters % 1.0 == 0.0) entry.liters.toInt().toString() else entry.liters.toString()
            holder.etLiters.setText(text)
        }
        holder.etLiters.hint = "0"
        holder.etLiters.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                holder.etLiters.hint = ""
            } else {
                holder.etLiters.hint = "0"
            }
        }

        val price = entry.liters * pricePerLiter
        holder.tvDayPrice.text = "Rs ${price.toInt()}"
        val newWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString()
                val qty = if (input.isEmpty()) 0.0 else input.toDoubleOrNull() ?: 0.0
                if (entry.liters != qty) {
                    entry.liters = qty
                    val newPrice = qty * pricePerLiter
                    holder.tvDayPrice.text = "Rs ${newPrice.toInt()}"
                    onDataChanged()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        holder.etLiters.addTextChangedListener(newWatcher)
        holder.currentWatcher = newWatcher
    }

    override fun getItemCount(): Int = dailyEntries.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDay: TextView = itemView.findViewById(R.id.tvDay)
        val etLiters: EditText = itemView.findViewById(R.id.etLiters)
        val tvDayPrice: TextView = itemView.findViewById(R.id.tvDayPrice)
        var currentWatcher: TextWatcher? = null
    }
}