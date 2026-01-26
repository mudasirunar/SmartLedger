package com.example.smartledger.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.smartledger.R

class ThumbnailAdapter(
    private val imagePaths: MutableList<String>,
    private val onDeleteClick: (String) -> Unit
) : RecyclerView.Adapter<ThumbnailAdapter.ThumbnailViewHolder>() {

    inner class ThumbnailViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.imgPhoto)
        private val deleteButton: ImageView = itemView.findViewById(R.id.btnDelete)

        fun bind(path: String) {
            // OPTIMIZED: Glide handles background loading and scaling
            Glide.with(itemView.context)
                .load(path)
                .centerCrop()
                .placeholder(R.color.tile_bg)
                .into(imageView)

            deleteButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDeleteClick(imagePaths[position])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbnailViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo_thumbnail, parent, false)
        return ThumbnailViewHolder(view)
    }

    override fun onBindViewHolder(holder: ThumbnailViewHolder, position: Int) {
        holder.bind(imagePaths[position])
    }

    override fun getItemCount(): Int = imagePaths.size
}