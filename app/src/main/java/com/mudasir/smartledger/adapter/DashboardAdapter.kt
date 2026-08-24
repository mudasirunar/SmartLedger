package com.mudasir.smartledger.adapter

import android.graphics.Color // Add this
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mudasir.smartledger.R
import com.mudasir.smartledger.data.DashboardTile
import com.google.android.material.card.MaterialCardView

class DashboardAdapter(
    private val onTileClick: (DashboardTile) -> Unit
) : ListAdapter<DashboardTile, DashboardAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_dashboard_tile, parent, false)
        return ViewHolder(view)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tile = getItem(position)
        holder.tvName.text = tile.name

        when {
            tile.isAddTile -> {
                holder.ivIcon.setImageResource(R.drawable.ic_add)
                holder.ivIcon.setColorFilter(
                    ContextCompat.getColor(holder.itemView.context, R.color.teal_main)
                )
                holder.card.setCardBackgroundColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.tile_add_bg)
                )
                holder.card.strokeWidth = 1
                holder.card.cardElevation = 4f
            }
            tile.isCustom -> {
                val resId = holder.itemView.context.resources.getIdentifier(
                    tile.iconName, "drawable", holder.itemView.context.packageName
                )
                holder.ivIcon.setImageResource(if (resId != 0) resId else R.drawable.ic_star)
                holder.ivIcon.setColorFilter(
                    ContextCompat.getColor(holder.itemView.context, R.color.teal_main)
                )
                holder.card.setCardBackgroundColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.tile_bg)
                )
                holder.card.strokeWidth = 1
                holder.card.cardElevation = 4f
            }
            else -> {
                tile.iconRes?.let { holder.ivIcon.setImageResource(it) }
                holder.ivIcon.setColorFilter(
                    ContextCompat.getColor(holder.itemView.context, R.color.teal_main)
                )
                holder.card.setCardBackgroundColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.tile_bg)
                )
                holder.card.strokeWidth = 1
                holder.card.cardElevation = 4f
            }
        }

        holder.itemView.setOnClickListener { onTileClick(tile) }
    }

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val ivIcon: ImageView = v.findViewById(R.id.ivTileIcon)
        val tvName: TextView = v.findViewById(R.id.tvTileName)
        val card: MaterialCardView = v as MaterialCardView
    }

    class DiffCallback : DiffUtil.ItemCallback<DashboardTile>() {
        override fun areItemsTheSame(old: DashboardTile, new: DashboardTile) = old.id == new.id
        override fun areContentsTheSame(old: DashboardTile, new: DashboardTile) = old == new
    }
}