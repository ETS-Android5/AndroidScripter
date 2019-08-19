package com.tsiemens.androidscripter

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog

class OkNoDialogFragment : DialogFragment() {
    lateinit var msg: String
    var onOk: DialogInterface.OnClickListener? = null
    var onNo: DialogInterface.OnClickListener? = null

    fun setMessage(msg_: String): OkNoDialogFragment {
        msg = msg_
        return this
    }

    fun setOnOk(cl: DialogInterface.OnClickListener): OkNoDialogFragment {
        onOk = cl
        return this
    }

    fun setOnNo(cl: DialogInterface.OnClickListener): OkNoDialogFragment {
        onNo = cl
        return this
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            // Use the Builder class for convenient dialog construction
            val builder = AlertDialog.Builder(it)
            builder.setMessage(msg)
                .setPositiveButton(android.R.string.ok, onOk)
                .setNegativeButton(android.R.string.no, onNo)
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}
