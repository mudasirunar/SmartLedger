package com.mudasir.smartledger.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Utility extensions for applying system bar (status bar / navigation bar) and IME (keyboard)
 * insets to views across activities cleanly and consistently.
 */

fun View.applySystemBarPadding(includeIme: Boolean = false) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val bottomPadding = if (includeIme) {
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            maxOf(systemBars.bottom, imeInsets.bottom)
        } else {
            systemBars.bottom
        }
        v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomPadding)
        insets
    }
}
