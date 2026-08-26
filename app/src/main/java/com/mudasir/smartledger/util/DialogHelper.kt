package com.mudasir.smartledger.util

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.mudasir.smartledger.R

/**
 * Utility helper for building transparent background confirmation dialogs using R.layout.dialog_custom_confirmation.
 * Keeps exact title, message, detail container, and button text configuration strictly controlled by the calling Activity.
 */
object DialogHelper {

    data class DialogViews(
        val dialog: AlertDialog,
        val title: TextView,
        val message: TextView,
        val progress: LinearProgressIndicator,
        val details: View,
        val detailTitle: TextView,
        val detailAmount: TextView,
        val btnCancel: View,
        val btnConfirm: TextView
    )

    fun createConfirmationDialog(
        context: Context,
        title: String? = null,
        message: String? = null,
        isCancelable: Boolean = true,
        onConfigure: (views: DialogViews) -> Unit
    ): AlertDialog {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_custom_confirmation, null)
        val dialog = AlertDialog.Builder(context).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(isCancelable)

        val views = DialogViews(
            dialog = dialog,
            title = dialogView.findViewById(R.id.tvDialogTitle),
            message = dialogView.findViewById(R.id.tvDialogMessage),
            progress = dialogView.findViewById(R.id.dialogProgressBar),
            details = dialogView.findViewById(R.id.containerDetails),
            detailTitle = dialogView.findViewById(R.id.tvDetailTitle),
            detailAmount = dialogView.findViewById(R.id.tvDetailAmount),
            btnCancel = dialogView.findViewById(R.id.btnDialogCancel),
            btnConfirm = dialogView.findViewById(R.id.btnDialogConfirm)
        )

        if (title != null) views.title.text = title
        if (message != null) views.message.text = message

        onConfigure(views)
        return dialog
    }
}
