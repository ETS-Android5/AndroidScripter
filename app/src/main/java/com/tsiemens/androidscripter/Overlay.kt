package com.tsiemens.androidscripter

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.TextView
import android.view.MotionEvent
import android.view.WindowManager

// https://stackoverflow.com/questions/4481226/creating-a-system-overlay-window-always-on-top
class OverlayManager(val context: Context) {
    companion object {
        val TAG = OverlayManager::class.java.simpleName
    }

    var wm: WindowManager? = null
    var params: WindowManager.LayoutParams? = null
    var overlay: View? = null

    @TargetApi(Build.VERSION_CODES.O)
    @SuppressWarnings("ClickableViewAccessibility")
    fun showOverlay() {
        if (overlay != null) {
            return
        }
        overlay = LayoutInflater.from(context).inflate(R.layout.overlay_base, null)
        Log.v(TAG, "showOverlay")

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT)

        params!!.gravity = ( Gravity.START or Gravity.TOP )
        params!!.title = "Overlay"
        wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm!!.addView(overlay, params)

        val handle = overlay!!.findViewById<TextView>(R.id.overlay_handle)

        val expander = overlay!!.findViewById<TextView>(R.id.overlay_expand)
        expander.setOnClickListener {
            if (overlay != null) {
                val details = overlay!!.findViewById<View>(R.id.overlay_details)
                details.visibility = when(details.visibility) {
                    View.VISIBLE -> View.GONE
                    else -> View.VISIBLE
                }
            }
        }

        handle.setOnTouchListener(ViewDragger(params!!, overlay!!, wm!!))
    }

    fun permittedToShow(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun launchOverlayPermissionsActivity() {
        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
    }

    fun destroy() {
        if (overlay != null) {
            wm!!.removeView(overlay)
        }
    }
}

class ViewDragger(val params: WindowManager.LayoutParams,
                  val view: View,
                  val wm: WindowManager): View.OnTouchListener {
    companion object {
        val TAG = OverlayManager::class.java.simpleName
    }
    var xOnDown: Int = 0
    var yOnDown: Int = 0
    var touchX: Float = 0f
    var touchY: Float = 0f

    @SuppressWarnings("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                xOnDown = params.x
                yOnDown = params.y
                touchX = event.rawX
                touchY = event.rawY
                return true
            }
            MotionEvent.ACTION_UP -> {
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                params.x = xOnDown + (event.rawX - touchX).toInt()
                params.y = yOnDown + (event.rawY - touchY).toInt()
//                Log.d(TAG, "onTouch up. new x: ${params.x}, y: ${params.y}")
                wm.updateViewLayout(view, params)
                return true
            }
        }
        return false
    }
}
