package com.tsiemens.androidscripter.dialog

import android.app.Dialog
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import android.view.View
import android.widget.EditText
import com.tsiemens.androidscripter.R

class ScriptEditDialog: DialogFragment() {
    interface OnOkListener {
        fun onOk(name: String, url: String)
    }

    private lateinit var dialogView: View

    var onOkListener: OnOkListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let { fragActivity ->
            val builder = AlertDialog.Builder(fragActivity)
            val inflater = requireActivity().layoutInflater

            builder.setTitle("New Script")
            // Inflate and set the layout for the dialog
            // Pass null as the parent view because its going in the dialog layout
            dialogView = inflater.inflate(R.layout.dialog_script_edit, null)
            builder.setView(dialogView)
                // Positive click listener is set onStart to override the dismiss behaviour
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
            builder.create()

        } ?: throw IllegalStateException("Activity cannot be null")
    }

    override fun onStart() {
        super.onStart()
        val d = dialog as AlertDialog
        d.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener { doOk() }
    }

    fun doOk() {
        val nameEt = dialogView.findViewById<EditText>(R.id.name_et)
        val urlEt = dialogView.findViewById<EditText>(R.id.url_et)

        var setError = false
        if (nameEt.text.toString().trim().isEmpty()) {
            nameEt.error = "Name must not be empty"
            setError = true
        }
        if (urlEt.text.toString().trim().isEmpty()) {
            urlEt.error = "URL must not be empty"
            setError = true
        }

        if (!setError) {
            onOkListener?.let {
                it.onOk(nameEt.text.toString(), urlEt.text.toString())
            }
            dialog.dismiss()
        }
    }
}