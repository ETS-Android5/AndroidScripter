package com.tsiemens.androidscripter.overlay

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.tsiemens.androidscripter.inspect.ScreenProvider
import com.tsiemens.androidscripter.notify.ScreenInspectionListener
import com.tsiemens.androidscripter.util.ColorCompat

class TouchInterceptOverlayView(ctx: Context): FullscreenOverlayView(ctx) {
    companion object {
        val TAG = TouchInterceptOverlayView::class.java.simpleName
    }

    val borderPaint = Paint()

    init {
        borderPaint.isAntiAlias = true
        borderPaint.color = Color.CYAN
        borderPaint.strokeWidth = 10f
        borderPaint.isDither = true
        borderPaint.style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        if (canvas == null) {
            Log.d(TAG, "onDraw: Canvas was null")
            return
        }
        Log.d(TAG, "onDraw width: $width height: $height")

        canvas.drawRect(
            0f,
            0f,
            (width - 1).toFloat(),
            (height - 1).toFloat(),
            borderPaint
        )
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }
}

class TouchInterceptOverlayManager(activity: Activity,
                                   val screenProvider: ScreenProvider?,
                                   val inspectionListener: ScreenInspectionListener?):
    FullscreenOverlayManagerBase<TouchInterceptOverlayView>(
        activity, "touchInterceptOverlay", true), View.OnTouchListener {

    companion object {
        val TAG = TouchInterceptOverlayManager::class.java.simpleName
    }

    override fun inflateOverlayView(): TouchInterceptOverlayView {
        val view = TouchInterceptOverlayView(context)
        view.setOnTouchListener(this)
        return view
    }

    override fun onTouch(v: View?, motionEvent: MotionEvent?): Boolean {
        val ret = v?.performClick()
        if (this.started()) {
            Log.i(TAG, "onTouch: ${motionEvent?.action} ${motionEvent?.x}, ${motionEvent?.y}")
            val overlayView = overlay?.root
            if (motionEvent != null && overlayView != null) {
                val screenX = overlayView.canvasXToScreenX(motionEvent.x)
                val screenY = overlayView.canvasYToScreenY(motionEvent.y)
                val bm = screenProvider?.getScreenCap(cropPadding=true)
                var color = ColorCompat.rgb(0, 0, 0)

                if (bm != null) {
                    val bmX = (overlayView.screenXToPercent(screenX) * bm.width).toInt()
                    val bmY = (overlayView.screenYToPercent(screenY) * bm.height).toInt()
                    if (bmX < bm.width && bmY < bm.height) {
                        color = ColorCompat(bm.getPixel(bmX, bmY))
                    }
                }
                inspectionListener?.onPointInspected(screenX, screenY, color, isPercent = false)
            }
            this.destroy()
        }
        return ret ?: false
    }
}

