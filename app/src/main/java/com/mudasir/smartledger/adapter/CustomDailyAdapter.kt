package com.mudasir.smartledger.adapter

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mudasir.smartledger.R
import com.mudasir.smartledger.data.CustomDailyEntry
import com.mudasir.smartledger.data.CustomLedger

class CustomDailyAdapter(
    private val ledger: CustomLedger,
    val dailyEntries: List<CustomDailyEntry>,
    private val pricingMap: Map<String, Double>,
    private val onDataChanged: () -> Unit
) : RecyclerView.Adapter<CustomDailyAdapter.ViewHolder>() {

    init { setHasStableIds(true) }

    override fun getItemId(position: Int): Long = dailyEntries[position].day.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_custom_daily_day, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = dailyEntries[position]
        holder.tvDay.text = entry.day.toString()

        val fields = ledger.fields

        holder.etFields.forEachIndexed { index, et ->
            if (index < fields.size) {
                et.visibility = View.VISIBLE
                et.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL

                et.hint = "0"
                et.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                    et.hint = if (hasFocus) "" else "0"
                }

                holder.watchers[index]?.let { et.removeTextChangedListener(it) }

                val value = entry.values.getOrElse(index) { 0.0 }
                et.setText(if (value == 0.0) "" else value.toString().removeSuffix(".0"))

                val watcher = object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        if (et.hasFocus()) {
                            val qty = s.toString().toDoubleOrNull() ?: 0.0
                            if (entry.values.getOrElse(index) { 0.0 } != qty) {
                                entry.values[index] = qty
                                updateRowTotal(holder)
                                onDataChanged()
                            }
                        }
                    }
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                }
                et.addTextChangedListener(watcher)
                holder.watchers[index] = watcher
            } else {
                et.visibility = View.GONE
            }
        }
        updateRowTotal(holder)
    }

    private fun updateRowTotal(holder: ViewHolder) {
        val pos = holder.bindingAdapterPosition
        if (pos == RecyclerView.NO_POSITION) return

        val entry = dailyEntries[pos]
        var total = 0.0

        ledger.fields.forEachIndexed { index, field ->
            val qty = entry.values.getOrElse(index) { 0.0 }
            val price = pricingMap[field.fieldName] ?: pricingMap["MASTER_GLOBAL_PRICE"] ?: 0.0
            if (price > 0) total += (qty * price)
        }
        holder.tvDayPrice.text = "Rs ${total.toInt()}"
    }

    override fun getItemCount(): Int = dailyEntries.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDay: TextView = itemView.findViewById(R.id.tvDay) ?: throw NullPointerException("tvDay not found!")
        val etField1: EditText = itemView.findViewById(R.id.etField1) ?: throw NullPointerException("etField1 not found!")
        val etField2: EditText = itemView.findViewById(R.id.etField2) ?: throw NullPointerException("etField2 not found!")
        val etField3: EditText = itemView.findViewById(R.id.etField3) ?: throw NullPointerException("etField3 not found!")
        val tvDayPrice: TextView = itemView.findViewById(R.id.tvDayPrice) ?: throw NullPointerException("tvDayPrice not found!")

        val etFields = listOf(etField1, etField2, etField3)
        val watchers = mutableMapOf<Int, TextWatcher>()
    }
}