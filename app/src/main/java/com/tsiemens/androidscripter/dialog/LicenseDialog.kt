package com.tsiemens.androidscripter.dialog

import android.app.Dialog
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import android.view.View
import android.widget.EditText
import com.tsiemens.androidscripter.DataUtilHelper
import com.tsiemens.androidscripter.R

class LicenseDialog: DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let { fragActivity ->
            val builder = AlertDialog.Builder(fragActivity)
            builder.setTitle("Licenses")
            val licenseText = DataUtilHelper(fragActivity).getAssetUtf8Data("LICENSE.md")
            builder.setMessage(licenseText)

            builder.setPositiveButton(android.R.string.ok, null)
            builder.create()

        } ?: throw IllegalStateException("Activity cannot be null")
    }
}