package com.tsiemens.androidscripter

import android.animation.ValueAnimator
import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.tsiemens.androidscripter.script.Api
import com.tsiemens.androidscripter.script.ScreenUtil
import com.tsiemens.androidscripter.util.UiUtil
import java.util.*

enum class PointIndicatorStyle {
    CIRCLE,
    SQUARE
}

class FadingPointIndicator(val point: Point, val style: PointIndicatorStyle, val paint: Paint) {
    val animator: ValueAnimator = ValueAnimator.ofInt(255, 0)
    init {
        animator.duration = 2000 // millis
    }

    fun attachToView(v: View) {
        animator.addUpdateListener {
            v.invalidate()
        }
        animator.start()
    }
}

class DebugCanvasOverlayView(ctx: Context): View(ctx) {
    private var screenSize = DisplayMetrics()

    val linePaint = Paint()
    val clickPaint = Paint()
    val pointIndicatorPaint = Paint()
    val xPaint = Paint()

    val points = ArrayDeque<FadingPointIndicator>()
    // Cross and expire time
    val xs = ArrayDeque<Pair<ScreenUtil.Cross, Long>>()
    val timedLines = ArrayDeque<Pair<ScreenUtil.Line, Long>>()
    var lastXDetectImgWidth: Int = 1
    var lastXDetectImgHeight: Int = 1
    var screenWidth = 0
    var screenHeight = 0

    var lastDrawHeight = 0
    var lastDrawWidth = 0

    var cachedTopBarHeight: Int = 0

    val cachedLocationInScreen = IntArray(2)

    companion object {
        val TAG = DebugCanvasOverlayView::class.java.simpleName

        val X_EXPIRE_DURATION = 4000L

        val CIRCLE_RADIUS = 15f
        val SQUARE_HALF_LEN = 15f
    }

    init {
        linePaint.isAntiAlias = true
        linePaint.color = Color.RED
        linePaint.strokeWidth = 4f

        clickPaint.isAntiAlias = true
        clickPaint.color = Color.RED
        clickPaint.strokeWidth = 4f
        clickPaint.isDither = true
        clickPaint.style = Paint.Style.STROKE

        pointIndicatorPaint.isAntiAlias = true
        pointIndicatorPaint.color = Color.BLUE
        pointIndicatorPaint.strokeWidth = 4f
        pointIndicatorPaint.isDither = true
        pointIndicatorPaint.style = Paint.Style.STROKE

        xPaint.isAntiAlias = true
        xPaint.color = Color.CYAN
        xPaint.strokeWidth = 3f
        xPaint.isDither = true

        refreshScreenSize()
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        if (canvas == null) {
            Log.d(TAG, "onDraw: Canvas was null")
            return
        }
        Log.d(TAG, "onDraw width: $width height: $height")

        refreshCaches()

//        canvas!!.drawLine(20f, 20f, width - 20f, height - 20f, linePaint)
//        canvas.drawLine(100f, 0f, 100f, topBarHeight.toFloat(), linePaint)
//        canvas.drawLine(0f, 100f, (screenSize.x - width).toFloat(), 100f, linePaint)

        // debug lines
//        canvas.drawLine(0f, 3f, width.toFloat() - 3f, 3f, linePaint)
//        canvas.drawLine(3f, 0f, 3f, height.toFloat() - 3f, linePaint)

        val time = System.currentTimeMillis()
        while (timedLines.size > 0) {
            if (timedLines.first.second < time) {
                // Expired
                timedLines.removeFirst()
            } else {
                break
            }
        }
        for (x in timedLines) {
            drawLine(x.first, canvas, linePaint)
        }

        while (xs.size > 0) {
            if (xs.first.second < time) {
                // Expired
                xs.removeFirst()
            } else {
                break
            }
        }
        for (x in xs) {
            drawX(x.first, canvas)
        }

        for (p in points) {
            drawPointIndicator(p, canvas)
        }

        lastDrawHeight = height
        lastDrawWidth = width
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

    private fun drawPointIndicator(p: FadingPointIndicator, canvas: Canvas) {
        val alpha = p.animator.animatedValue as Int
        p.paint.alpha = alpha
        when (p.style) {
            PointIndicatorStyle.CIRCLE ->
                canvas.drawCircle(screenXToCanvasX(p.point.x.toFloat()),
                                  screenYToCanvasY(p.point.y.toFloat()), CIRCLE_RADIUS, p.paint)
            PointIndicatorStyle.SQUARE -> {
                val canvasXCenter = screenXToCanvasX(p.point.x.toFloat())
                val canvasYCenter = screenYToCanvasY(p.point.y.toFloat())
                Log.d(TAG, "drawPointIndicator - canvasSize: ${canvas.width}x${canvas.height} "+
                                "canvasPos: ${cachedLocationInScreen[0]}x${cachedLocationInScreen[1]} " +
                                "screen: $screenWidth x $screenHeight")
                Log.d(TAG, "drawPointIndicator - canvas x,y: $canvasXCenter, $canvasYCenter")
                canvas.drawRect(
                    canvasXCenter - SQUARE_HALF_LEN,
                    canvasYCenter - SQUARE_HALF_LEN,
                    canvasXCenter + SQUARE_HALF_LEN,
                    canvasYCenter + SQUARE_HALF_LEN,
                    p.paint
                )
            }
        }

        if (alpha == 0 && points.first == p) {
            Log.d(TAG, "Cleaned up fading point")
            points.removeFirst()
        }
    }

    private fun drawX(x: ScreenUtil.Cross, canvas: Canvas) {
        drawLine(x.nw, canvas, xPaint)
        drawLine(x.ne!!, canvas, xPaint)
        drawLine(x.se!!, canvas, xPaint)
        drawLine(x.sw!!, canvas, xPaint)
    }

    private fun drawLine(line: ScreenUtil.Line, canvas: Canvas, p: Paint) {
//        canvas.drawLine(line.x1().toFloat(), line.y1().toFloat() - cachedTopBarHeight,
//                        line.x2().toFloat(), line.y2().toFloat() - cachedTopBarHeight, p)
        canvas.drawLine(
            screenXToCanvasX((line.x1().toFloat() / lastXDetectImgWidth) * screenWidth),
            screenYToCanvasY((line.y1().toFloat() / lastXDetectImgHeight) * screenHeight),
            screenXToCanvasX((line.x2().toFloat() / lastXDetectImgWidth) * screenWidth),
            screenYToCanvasY((line.y2().toFloat() / lastXDetectImgHeight) * screenHeight),
            p)
    }

    private fun screenXToCanvasX(x: Float): Float {
        return x - cachedLocationInScreen[0]
    }

    private fun screenYToCanvasY(y: Float): Float {
        return y - cachedLocationInScreen[1]
    }

    fun addPointIndicator(p: Point) {
        val fc = FadingPointIndicator(p, PointIndicatorStyle.SQUARE, pointIndicatorPaint)
        points.add(fc)
        Log.d(TAG, "addPointIndicator at ${p.x}, ${p.y} (screen: ${screenSize.widthPixels}, ${screenSize.heightPixels})")
        fc.attachToView(this)
        invalidate()
    }

    fun addClick(p: Point) {
        val fc = FadingPointIndicator(p, PointIndicatorStyle.CIRCLE, clickPaint)
        points.add(fc)
        fc.attachToView(this)
        invalidate()
    }

    fun addCrosses(crosses: List<ScreenUtil.Cross>) {
        xs.clear()

        val time = System.currentTimeMillis()
        for (x in crosses) {
            xs.add(Pair(x, time + X_EXPIRE_DURATION))
        }
        invalidate()

        Handler().postDelayed({
            invalidate()
        }, X_EXPIRE_DURATION + 10)
    }

    fun addLines(lines: List<ScreenUtil.Line>) {
        timedLines.clear()

        val time = System.currentTimeMillis()
        for (l in lines) {
            timedLines.add(Pair(l, time + X_EXPIRE_DURATION))
        }
        invalidate()

        Handler().postDelayed({
            invalidate()
        }, X_EXPIRE_DURATION + 10)
    }
}

class DebugOverlayManager(val activity: Activity): OverlayManagerBase(activity), Api.DebugOverlayManager {
    companion object {
        val TAG = DebugOverlayManager::class.java.simpleName
    }

    class OverlayContainer(val root: DebugCanvasOverlayView)

    private var wm: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null
    private var overlay: OverlayContainer? = null

    @TargetApi(Build.VERSION_CODES.O)
    @SuppressWarnings("ClickableViewAccessibility")
    override fun showOverlay() {
        if (overlay != null) {
            return
        }

        Log.v(TAG, "showOverlay")
        wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

//        val overlayRoot = LayoutInflater.from(activity).inflate(R.layout.overlay_base, null)
        val overlayRoot = DebugCanvasOverlayView(context)
        val overlay = OverlayContainer(overlayRoot)
        this.overlay = overlay

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
//            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
//                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT)

        params!!.gravity = ( Gravity.START or Gravity.TOP )
        params!!.title = "DebugCanvasOverlay"
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

    fun bringToFront() {
        overlay?.root?.bringToFront()
        val rootView = overlay?.root
        if (rootView != null) {
            wm!!.removeView(rootView)
            wm!!.addView(rootView, params)
        }
    }

    fun getScreenPoint(x: Float, y: Float, isPercent: Boolean): Point {
        val realX: Int
        val realY: Int
        if (isPercent) {
            realX = ((overlay!!.root.screenWidth - 1) * x).toInt()
            realY = ((overlay!!.root.screenHeight - 1) * y).toInt()
        } else {
            realX = x.toInt()
            realY = y.toInt()
        }
        return Point(realX, realY)
    }

    override fun onPointInspected(x: Float, y: Float, isPercent: Boolean) {
        val point = getScreenPoint(x, y, isPercent)
        activity.runOnUiThread {
            overlay!!.root.addPointIndicator(point)
        }
    }

    override fun onClickSent(x: Float, y: Float, isPercent: Boolean) {
        val point = getScreenPoint(x, y, isPercent)
        activity.runOnUiThread {
            overlay!!.root.addClick(point)
        }
    }

    override fun onXsFound(res: ScreenUtil.XDetectResult) {
        // TODO make copy of res
        // val xsCopy = xs.subList(0, xs.size)
        activity.runOnUiThread {
            overlay!!.root.lastXDetectImgWidth = res.imgWidth
            overlay!!.root.lastXDetectImgHeight = res.imgHeight
            overlay!!.root.addCrosses(res.xs)
            overlay!!.root.addLines(res.allLines)

        }
    }
}
