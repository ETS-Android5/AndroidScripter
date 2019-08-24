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
import android.widget.Button
import kotlin.math.max

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
        Log.v(TAG, "showOverlay")
        overlay = LayoutInflater.from(context).inflate(R.layout.overlay_base, null)
        overlay!!.findViewById<View>(R.id.overlay_details_all).visibility = View.GONE

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
                val details = overlay!!.findViewById<View>(R.id.overlay_details_all)
                details.visibility = when(details.visibility) {
                    View.VISIBLE -> View.GONE
                    else -> View.VISIBLE
                }
            }
        }

        overlay!!.findViewById<Button>(R.id.overlay_ocr_clear_button).setOnClickListener {
            updateOcrText("")
        }

        handle.setOnTouchListener(ViewDragger(params!!, overlay!!, wm!!))

        val sizeFrame = overlay!!.findViewById<View>(R.id.overlay_size_grower_frame)
        val sizeRefView = overlay!!.findViewById<View>(R.id.overlay_details_and_grower)
        val sizeHandle = overlay!!.findViewById<View>(R.id.overlay_sizing_handle)
        sizeHandle.setOnTouchListener(ViewResizer(sizeFrame, sizeRefView, wm!!))
    }

    fun started(): Boolean {
        return overlay != null
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

    fun updateOcrText(text: String) {
        if (overlay != null) {
            val ocrTv = overlay!!.findViewById<TextView>(R.id.screen_textview)
            ocrTv.text = text
        }
    }

    fun setOnCaptureTextButtonClick(cl: View.OnClickListener) {
        overlay!!.findViewById<Button>(R.id.overlay_ocr_capture_button).setOnClickListener(cl)
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
                wm.updateViewLayout(view, params)
                return true
            }
        }
        return false
    }
}

class ViewResizer(val targetView: View,
                  val referenceView: View,
                  val wm: WindowManager): View.OnTouchListener {
    companion object {
        val TAG = ViewResizer::class.java.simpleName
    }
    var widthOnDown: Int = 0
    var heightOnDown: Int = 0
    var touchX: Float = 0f
    var touchY: Float = 0f

    private fun correctTargetViewSize(): ViewGroup.LayoutParams {
        val params = targetView.layoutParams
        params.width = max(targetView.width, referenceView.width)
        params.height = max(targetView.height, referenceView.height)
//        Log.d(TAG, "correct to ${params.width}, ${params.height}")
        targetView.layoutParams = params
        return params
    }

    @SuppressWarnings("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val params = correctTargetViewSize()
                widthOnDown = params.width
                heightOnDown = params.height
                touchX = event.rawX
                touchY = event.rawY
//                Log.d(TAG, "DOWN $widthOnDown, $heightOnDown, $touchX, $touchY")
                return true
            }
            MotionEvent.ACTION_UP -> {
                correctTargetViewSize()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
//                Log.d(TAG, "MOVE ${event.rawX}, ${event.rawY}")
                val params = targetView.layoutParams
                params.width = max(0, (widthOnDown + (event.rawX - touchX)).toInt())
                params.height = max(0, (heightOnDown + (event.rawY - touchY)).toInt())
//                Log.d(TAG, "MOVE change to ${params.width}, ${params.height}")
                targetView.layoutParams = params
                return true
            }
        }
        return false
    }
}
