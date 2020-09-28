package com.tsiemens.androidscripter.overlay

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.tsiemens.androidscripter.script.Api

// https://stackoverflow.com/questions/4481226/creating-a-system-overlay-window-always-on-top
abstract class OverlayManagerBase(protected val context: Context) {

    fun permittedToShow(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun launchOverlayPermissionsActivity() {
        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
    }

    abstract fun started(): Boolean

    abstract fun showOverlay()

    abstract fun destroy()
}
