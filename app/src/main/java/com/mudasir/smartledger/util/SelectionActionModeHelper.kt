package com.mudasir.smartledger.util

import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mudasir.smartledger.R

/**
 * Utility helper to construct standard contextual ActionMode.Callback implementations.
 * Centralizes status bar color changes, drawer locking/unlocking, and FAB visibility toggling,
 * while leaving selection menu action handling and adapter teardown under activity control.
 */
object SelectionActionModeHelper {

    fun setupActionMode(
        activity: AppCompatActivity,
        drawerLayout: DrawerLayout,
        fabAdd: View? = null,
        menuResId: Int = R.menu.contextual_menu,
        onActionClicked: (mode: ActionMode, item: MenuItem) -> Boolean,
        onDestroy: () -> Unit
    ): ActionMode.Callback {
        return object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                mode.menuInflater.inflate(menuResId, menu)
                mode.title = "0 Selected"
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                activity.window.statusBarColor = activity.getColor(R.color.teal_dark)
                (fabAdd as? FloatingActionButton)?.hide() ?: fabAdd?.let { it.visibility = View.GONE }
                for (i in 0 until menu.size()) {
                    menu.getItem(i).icon?.setTint(activity.getColor(R.color.white))
                }
                activity.window.decorView.post {
                    val closeButton = activity.findViewById<android.widget.ImageView>(androidx.appcompat.R.id.action_mode_close_button)
                    closeButton?.setImageResource(R.drawable.ic_close)
                    closeButton?.setColorFilter(activity.getColor(R.color.white))
                }
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                return onActionClicked(mode, item)
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                activity.window.statusBarColor = activity.getColor(R.color.teal_main)
                (fabAdd as? FloatingActionButton)?.show() ?: fabAdd?.let { it.visibility = View.VISIBLE }
                onDestroy()
            }
        }
    }
}
