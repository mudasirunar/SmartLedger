package com.example.smartledger.adapter

import android.R
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smartledger.data.CalcHistory

class HistoryAdapter(private val onClick: (String) -> Unit) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private var historyList = emptyList<CalcHistory>()

    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvExpression: TextView = itemView.findViewById(R.id.text1)
        val tvResult: TextView = itemView.findViewById(R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.simple_list_item_2, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = historyList[position]
        holder.tvExpression.text = item.expression
        holder.tvResult.text = "= ${item.result}"
        holder.tvResult.setTextColor(holder.itemView.context.getColor(com.example.smartledger.R.color.teal_main))

        // Click to reuse result
        holder.itemView.setOnClickListener { onClick(item.result) }
    }

    override fun getItemCount() = historyList.size

    fun setData(newList: List<CalcHistory>) {
        historyList = newList
        notifyDataSetChanged()
    }
}