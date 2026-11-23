package com.example.myhrms.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.myhrms.R

/**
 * SweetAlert-style result popup: a rounded card with a coloured circular icon
 * and a single coloured action button. Green for success, red for failure.
 */
object ResultDialog {

    fun success(context: Context, title: String = "Saved", message: String? = null,
                onDismiss: (() -> Unit)? = null) =
        show(context, true, title, message, onDismiss)

    fun error(context: Context, title: String = "Failed", message: String? = null,
              onDismiss: (() -> Unit)? = null) =
        show(context, false, title, message, onDismiss)

    private fun show(context: Context, isSuccess: Boolean, title: String,
                     message: String?, onDismiss: (() -> Unit)?) {
        val view    = LayoutInflater.from(context).inflate(R.layout.dialog_result, null)
        val ivIcon  = view.findViewById<ImageView>(R.id.ivResultIcon)
        val tvTitle = view.findViewById<TextView>(R.id.tvResultTitle)
        val tvMsg   = view.findViewById<TextView>(R.id.tvResultMsg)
        val btnOk   = view.findViewById<Button>(R.id.btnResultOk)

        if (isSuccess) {
            ivIcon.setImageResource(R.drawable.ic_check)
            ivIcon.setBackgroundResource(R.drawable.bg_circle_green)
            btnOk.setBackgroundResource(R.drawable.bg_result_btn_green)
        } else {
            ivIcon.setImageResource(R.drawable.ic_close_x)
            ivIcon.setBackgroundResource(R.drawable.bg_circle_red)
            btnOk.setBackgroundResource(R.drawable.bg_result_btn_red)
        }

        tvTitle.text   = title
        tvMsg.text     = message ?: ""
        tvMsg.visibility = if (message.isNullOrBlank()) View.GONE else View.VISIBLE

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        btnOk.setOnClickListener {
            dialog.dismiss()
            onDismiss?.invoke()
        }
        dialog.show()
    }
}
