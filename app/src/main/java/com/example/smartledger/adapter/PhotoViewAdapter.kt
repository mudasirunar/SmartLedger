package com.example.smartledger.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.smartledger.R

class PhotoViewAdapter(
    private val imagePaths: List<String>,
    private val onImageClick: (String) -> Unit
) : RecyclerView.Adapter<PhotoViewAdapter.PhotoViewHolder>() {

    inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imgPhoto)

        fun bind(path: String) {
            // Glide handles the background loading and memory management
            Glide.with(itemView.context)
                .load(path)
                .centerCrop() // Fills the square nicely
                .transition(DrawableTransitionOptions.withCrossFade()) // Smooth appearance
                .placeholder(R.color.tile_bg) // Neutral color while loading
                .error(R.drawable.ic_error_outline) // Fallback if file is missing
                .into(imageView)

            itemView.setOnClickListener { onImageClick(path) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo_view, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(imagePaths[position])
    }

    override fun getItemCount(): Int = imagePaths.size
}