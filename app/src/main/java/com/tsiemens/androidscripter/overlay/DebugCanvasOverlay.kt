package com.tsiemens.androidscripter.overlay

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import com.tsiemens.androidscripter.script.Api
import com.tsiemens.androidscripter.script.ScreenUtil
import java.util.*

enum class PointIndicatorStyle {
    CIRCLE,
    // Note that square point indicators are fundamentally different than a RECT
    // shape, since the key information is the point, which is more guaranteed to
    // translate properly. Indicating points by using the edges has the potential to
    // not render properly when translated to the canvas coordinates.
    SQUARE
}

enum class ShapeKind {
    POINT_INDICATOR,
    RECT,
}

// Not particularly space efficient, but fine due to limited shape types and quantity.
open class Shape(val kind: ShapeKind)

class PointIndicator(val point: Point, val style: PointIndicatorStyle) :
    Shape(ShapeKind.POINT_INDICATOR)

class RectIndicator(val rect: Rect) : Shape(ShapeKind.RECT)

open class FadingShape(val shape: Shape, val paint: Paint) {
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

class DebugCanvasOverlayView(ctx: Context): FullscreenOverlayView(ctx) {
    private var screenSize = DisplayMetrics()

    val linePaint = Paint()
    val clickPaint = Paint()
    val pointIndicatorPaint = Paint()
    val xPaint = Paint()

    val fadingShapes = ArrayDeque<FadingShape>()
    // Cross and expire time
    val xs = ArrayDeque<Pair<ScreenUtil.Cross, Long>>()
    val timedLines = ArrayDeque<Pair<ScreenUtil.Line, Long>>()
    var lastXDetectImgWidth: Int = 1
    var lastXDetectImgHeight: Int = 1

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
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        if (canvas == null) {
            Log.d(TAG, "onDraw: Canvas was null")
            return
        }
        Log.d(TAG, "onDraw width: $width height: $height")

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

        for (s in fadingShapes) {
            drawFadingShape(s, canvas)
        }

        lastDrawHeight = height
        lastDrawWidth = width
    }

    private fun drawPointIndicator(p: PointIndicator, paint: Paint, canvas: Canvas) {
        when (p.style) {
            PointIndicatorStyle.CIRCLE ->
                canvas.drawCircle(screenXToCanvasX(p.point.x.toFloat()),
                    screenYToCanvasY(p.point.y.toFloat()),
                    CIRCLE_RADIUS, paint)
            PointIndicatorStyle.SQUARE -> {
                val canvasXCenter = screenXToCanvasX(p.point.x.toFloat())
                val canvasYCenter = screenYToCanvasY(p.point.y.toFloat())
                Log.d(
                    TAG, "drawPointIndicator - canvasSize: ${canvas.width}x${canvas.height} "+
                            "canvasPos: ${cachedLocationInScreen[0]}x${cachedLocationInScreen[1]} " +
                            "screen: $screenWidth x $screenHeight")
                Log.d(TAG, "drawPointIndicator - canvas x,y: $canvasXCenter, $canvasYCenter")
                canvas.drawRect(
                    canvasXCenter - SQUARE_HALF_LEN,
                    canvasYCenter - SQUARE_HALF_LEN,
                    canvasXCenter + SQUARE_HALF_LEN,
                    canvasYCenter + SQUARE_HALF_LEN,
                    paint
                )
            }
        }
    }

    private fun drawRectIndicator(p: RectIndicator, paint: Paint, canvas: Canvas) {
        canvas.drawRect(
            screenXToCanvasX(p.rect.left.toFloat()),
            screenYToCanvasY(p.rect.top.toFloat()),
            screenXToCanvasX(p.rect.right.toFloat()),
            screenYToCanvasY(p.rect.bottom.toFloat()),
            paint
        )
    }

    private fun drawFadingShape(p: FadingShape, canvas: Canvas) {
        val alpha = p.animator.animatedValue as Int
        p.paint.alpha = alpha

        when (p.shape.kind) {
            ShapeKind.POINT_INDICATOR ->
                drawPointIndicator(p.shape as PointIndicator, p.paint, canvas)
            ShapeKind.RECT ->
                drawRectIndicator(p.shape as RectIndicator, p.paint, canvas)
        }

        if (alpha == 0 && fadingShapes.first == p) {
            Log.d(TAG, "Cleaned up fading shape")
            fadingShapes.removeFirst()
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

    fun addPointIndicator(p: Point) {
        val fc = FadingShape(
            PointIndicator(p, PointIndicatorStyle.SQUARE),
            pointIndicatorPaint
        )
        fadingShapes.add(fc)
        Log.d(TAG, "addPointIndicator at ${p.x}, ${p.y} (screen: ${screenSize.widthPixels}, ${screenSize.heightPixels})")
        fc.attachToView(this)
        invalidate()
    }

    fun addClick(p: Point) {
        val fc = FadingShape(
            PointIndicator(p, PointIndicatorStyle.CIRCLE),
            clickPaint
        )
        fadingShapes.add(fc)
        fc.attachToView(this)
        invalidate()
    }

    fun addInspectAreaIndicator(r: Rect) {
        val fc = FadingShape(
            RectIndicator(r),
            pointIndicatorPaint
        )
        fadingShapes.add(fc)
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

class DebugOverlayManager(activity: Activity):
    FullscreenOverlayManagerBase<DebugCanvasOverlayView>(activity, "DebugCanvasOverlay", false),
    Api.DebugOverlayManager {
    companion object {
        val TAG = DebugOverlayManager::class.java.simpleName
    }

    fun bringToFront() {
        overlay?.root?.bringToFront()
        val rootView = overlay?.root
        if (rootView != null) {
            wm!!.removeView(rootView)
            wm!!.addView(rootView, params)
        }
    }

    fun getScreenPoint(x: Float, y: Float, isPercent: Boolean): Point? {
        val realX: Int
        val realY: Int
        if (isPercent) {
            val overlayLocal = overlay ?: return null
            realX = ((overlayLocal.root.screenWidth - 1) * x).toInt()
            realY = ((overlayLocal.root.screenHeight - 1) * y).toInt()
        } else {
            realX = x.toInt()
            realY = y.toInt()
        }
        return Point(realX, realY)
    }

    fun onPointInspected(x: Float, y: Float, isPercent: Boolean) {
        val point = getScreenPoint(x, y, isPercent)
        if (point != null) {
            activity.runOnUiThread {
                val overlayRoot = overlay?.root
                if (overlayRoot != null) {
                    overlayRoot.addPointIndicator(point)
                } else {
                    Log.w(TAG, "onPointInspected: Could not get overlay root")
                }
            }
        } else {
            Log.w(TAG, "onPointInspected: Could not get point")
        }
    }

    fun onAreaInspected(rect: Rect) {
        activity.runOnUiThread {
            val overlayRoot = overlay?.root
            if (overlayRoot != null) {
                overlayRoot.addInspectAreaIndicator(rect)
            } else {
                Log.w(TAG, "onAreaInspected: Could not get overlay root")
            }
        }
    }

    override fun onClickSent(x: Float, y: Float, isPercent: Boolean) {
        val point = getScreenPoint(x, y, isPercent)
        if (point != null) {
            activity.runOnUiThread {
                val overlayRoot = overlay?.root
                if (overlayRoot != null) {
                    overlayRoot.addClick(point)
                    overlayRoot.addPointIndicator(point)
                } else {
                    Log.w(TAG, "onPointInspected: Could not get overlay root")
                }
            }
        } else {
            Log.w(TAG, "onClickSent: Could not get point")
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

    override fun inflateOverlayView(): DebugCanvasOverlayView {
        return DebugCanvasOverlayView(context)
    }
}
