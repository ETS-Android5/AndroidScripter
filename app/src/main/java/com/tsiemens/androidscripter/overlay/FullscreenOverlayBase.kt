package com.tsiemens.androidscripter.overlay

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.tsiemens.androidscripter.util.UiUtil

open class FullscreenOverlayView(context: Context): View(context) {
    private var screenSize = DisplayMetrics()

    var screenWidth = 0
    var screenHeight = 0

    var lastDrawHeight = 0
    var lastDrawWidth = 0

    var cachedTopBarHeight: Int = 0

    val cachedLocationInScreen = IntArray(2)

    companion object {
        val TAG = FullscreenOverlayView::class.java.simpleName
    }

    init {
        refreshScreenSize()
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        refreshCaches()
    }

    private fun refreshCaches() {
        // divide by 3, assuming that the bottom bar is present, and is about twice the height of the top bar
        cachedTopBarHeight = (screenSize.heightPixels - height) / 3

        getLocationOnScreen(cachedLocationInScreen)

        if (lastDrawHeight != height || lastDrawWidth != width) {
            refreshScreenSize()
        }
    }

    private fun refreshScreenSize() {
        UiUtil.getDisplaySize(context, screenSize)

        if ((screenSize.widthPixels < screenSize.heightPixels) == (width < height)) {
            screenWidth = screenSize.widthPixels
            screenHeight = screenSize.heightPixels
        } else {
            screenHeight = screenSize.widthPixels
            screenWidth = screenSize.heightPixels
        }
    }

    fun screenXToCanvasX(x: Float): Float {
        return x - cachedLocationInScreen[0]
    }

    fun screenYToCanvasY(y: Float): Float {
        return y - cachedLocationInScreen[1]
    }

    fun canvasXToScreenX(x: Float): Float {
        return x + cachedLocationInScreen[0]
    }

    fun canvasYToScreenY(y: Float): Float {
        return y + cachedLocationInScreen[1]
    }

    fun screenXToPercent(x: Float): Float {
        return if (screenWidth != 0) x / screenWidth else 0f
    }

    fun screenYToPercent(y: Float): Float {
        return if (screenHeight != 0) y / screenHeight else 0f
    }
}

abstract class FullscreenOverlayManagerBase<T: View>(val activity: Activity,
                                            val title: String,
                                            val touchable: Boolean): OverlayManagerBase(activity) {

    companion object {
        val TAG = FullscreenOverlayManagerBase::class.java.simpleName
    }

    class OverlayContainer<T: View>(val root: T)

    protected var params: WindowManager.LayoutParams? = null
    protected var wm: WindowManager? = null
    protected var overlay: OverlayContainer<T>? = null

    @TargetApi(Build.VERSION_CODES.O)
    @SuppressWarnings("ClickableViewAccessibility")
    override fun showOverlay() {
        if (overlay != null) {
            return
        }

        Log.v(TAG, "showOverlay")
        wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val overlayRoot = inflateOverlayView()
        val overlay = OverlayContainer<T>(overlayRoot)
        this.overlay = overlay

        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (!touchable) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        )

        params!!.gravity = (Gravity.START or Gravity.TOP)
        params!!.title = title
        // Android 12 added security restrictions on overlays that can allow pass-through
        // touch events. It has an exception for windows with an alpha of less than 0.8 though.
        if (params!!.alpha >= 0.8) {
            params!!.alpha = 0.79f
        }
        wm!!.addView(overlayRoot, params)
    }

    override fun started(): Boolean {
        return overlay != null
    }

    override fun destroy() {
        if (overlay != null) {
            wm!!.removeView(overlay!!.root)
            overlay = null
        }
    }

    abstract fun inflateOverlayView(): T
}
