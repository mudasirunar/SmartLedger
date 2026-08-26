package com.mudasir.smartledger.util

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mudasir.smartledger.activity.ImageViewerActivity
import com.mudasir.smartledger.adapter.PhotoViewAdapter

/**
 * Utility helper to bind photo thumbnail galleries to RecyclerView and toggle section header visibility.
 */
object PhotoGridHelper {

    fun setupPhotoGrid(
        context: Context,
        recyclerView: RecyclerView,
        headerView: View? = null,
        images: List<String>,
        spanCount: Int = 2
    ) {
        if (images.isEmpty()) {
            headerView?.visibility = View.GONE
            recyclerView.visibility = View.GONE
        } else {
            headerView?.visibility = View.VISIBLE
            recyclerView.visibility = View.VISIBLE

            recyclerView.apply {
                setHasFixedSize(true)
                layoutManager = GridLayoutManager(context, spanCount)
                adapter = PhotoViewAdapter(images) { imagePath ->
                    val pos = images.indexOf(imagePath)
                    val intent = Intent(context, ImageViewerActivity::class.java).apply {
                        putStringArrayListExtra("image_paths", ArrayList(images))
                        putExtra("position", pos)
                    }
                    context.startActivity(intent)
                }
            }
        }
    }
}
