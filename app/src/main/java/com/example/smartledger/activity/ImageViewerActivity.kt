package com.example.smartledger.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.example.smartledger.R
import com.github.chrisbanes.photoview.PhotoView
import kotlin.math.abs

class ImageViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        viewPager.offscreenPageLimit = 1
        val btnClose = findViewById<ImageView>(R.id.btnClose)

        val imagePaths = intent.getStringArrayListExtra("image_paths") ?: arrayListOf()
        val startPosition = intent.getIntExtra("position", 0)

        val adapter = FullScreenImageAdapter(imagePaths) {
            finish()
        }
        viewPager.adapter = adapter

        viewPager.setCurrentItem(startPosition, false)

        btnClose.setOnClickListener { finish() }
    }

    inner class FullScreenImageAdapter(
        private val paths: List<String>,
        private val onDismiss: () -> Unit
    ) : RecyclerView.Adapter<FullScreenImageAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val photoView: PhotoView = itemView.findViewById(R.id.photo_view)

            fun bind(path: String) {
                Glide.with(this@ImageViewerActivity)
                    .load(path)
                    .fitCenter()
                    .thumbnail(0.1f)
                    .into(photoView)

                // SWIPE DOWN LOGIC (Keep your existing fling logic)
                photoView.setOnSingleFlingListener { e1, e2, velocityX, velocityY ->
                    if (e1 != null && e2 != null) {
                        val diffY = e2.y - e1.y
                        val diffX = e2.x - e1.x
                        if (diffY > 150 && abs(diffY) > abs(diffX)) {
                            onDismiss()
                            true
                        } else false
                    } else false
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_fullscreen_image, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(paths[position])
        }

        override fun getItemCount(): Int = paths.size
    }
}