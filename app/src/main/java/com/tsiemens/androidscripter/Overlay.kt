package com.tsiemens.androidscripter

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.*
import com.tsiemens.androidscripter.script.Api
import com.tsiemens.androidscripter.util.ColorCompat
import com.tsiemens.androidscripter.util.UiUtil
import com.tsiemens.androidscripter.widget.ScriptController
import com.tsiemens.androidscripter.widget.ScriptControllerUIHelper
import kotlin.math.max

class OverlayContainer(val root: View) {
    val spinner = root.findViewById<Spinner>(R.id.overlay_detail_spinner)
    val logTv = root.findViewById<TextView>(R.id.log_tv)
}

// https://stackoverflow.com/questions/4481226/creating-a-system-overlay-window-always-on-top
class OverlayManager(val activity: Activity): OverlayManagerBase(activity), Api.OverlayManager {
    companion object {
        val TAG = OverlayManager::class.java.simpleName
    }

    private var wm: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null
    private var overlay: OverlayContainer? = null

    var onDestroyListener: (()->Unit)? = null

    @TargetApi(Build.VERSION_CODES.O)
    @SuppressWarnings("ClickableViewAccessibility")
    override fun showOverlay() {
        if (overlay != null) {
            return
        }
        Log.v(TAG, "showOverlay")
        val overlayRoot = LayoutInflater.from(context).inflate(R.layout.overlay_base, null)
        val overlay = OverlayContainer(overlayRoot)
        this.overlay = overlay
        overlayRoot!!.findViewById<View>(R.id.overlay_details_all).visibility = View.GONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT)

        params!!.gravity = ( Gravity.START or Gravity.TOP )
        params!!.title = "Overlay"
        wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm!!.addView(overlayRoot, params)

        val handle = overlayRoot.findViewById<View>(R.id.overlay_handle)

        val expander = overlayRoot.findViewById<View>(R.id.overlay_expand)
        expander.setOnClickListener {
            if (this.overlay != null) {
                val details = overlayRoot.findViewById<View>(R.id.overlay_details_all)
                val closeButton = overlayRoot.findViewById<View>(R.id.overlay_close_button)
                val visibility = when(details.visibility) {
                        View.VISIBLE -> View.GONE
                        else -> View.VISIBLE
                    }
                details.visibility = visibility
                closeButton.visibility = visibility
                expander.rotation = when(details.visibility) {
                    View.VISIBLE -> 90f
                    else -> -90f
                }
            }
        }

        val closeButton = overlayRoot.findViewById<View>(R.id.overlay_close_button)
        closeButton.setOnClickListener {
            destroy()
            onDestroyListener?.let { it1 -> it1() }
        }

        overlay.spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(parent: AdapterView<*>?) {
                overlay.spinner.setSelection(0)
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                Log.d(TAG, "onItemSelected for panel spinner: $pos")
                val ids = context.resources.getStringArray(R.array.overlay_displays_ids)
                when (ids[pos]) {
                    "script_log" -> changePanelVisibility(R.id.overlay_log_panel)
                    "point_analysis" -> changePanelVisibility(R.id.overlay_point_analysis_panel)
                    "ocr" -> changePanelVisibility(R.id.overlay_ocr_panel)
                    else -> {
                        Log.d(TAG, "Overlay spinner onItemSelected: Invalid id ${ids[pos]}")
                        Toast.makeText(this@OverlayManager.context, "Error: Invalid Id", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        changePanelVisibility(R.id.overlay_log_panel)

        overlayRoot.findViewById<Button>(R.id.overlay_ocr_clear_button).setOnClickListener {
            updateOcrText("")
        }

        handle.setOnTouchListener(ViewDragger(params!!, overlayRoot, wm!!))

        val sizeFrame = overlayRoot.findViewById<View>(R.id.overlay_details_all)
        val sizeHandle = overlayRoot.findViewById<View>(R.id.overlay_sizing_handle)
        sizeHandle.setOnTouchListener(ViewResizer(sizeFrame, wm!!))

        val screenSize = Point()
        wm!!.defaultDisplay.getSize(screenSize)
        params!!.x = 0
        params!!.y = (screenSize.y * 0.3).toInt()
        wm!!.updateViewLayout(overlayRoot, params)

        // Limit the default width of the overlay
        val sizeFrameParams = sizeFrame.layoutParams
        val largestScreenLength = max(screenSize.x, screenSize.y)
        sizeFrameParams.width = (largestScreenLength * 0.4).toInt()
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

    private fun changePanelVisibility(panelId: Int) {
        if (overlay != null) {
            val panelIds = arrayOf(
                R.id.overlay_log_panel,
                R.id.overlay_point_analysis_panel,
                R.id.overlay_ocr_panel)
            panelIds.forEach { id ->
                val panel = overlay!!.root.findViewById<View>(id)
                panel.visibility = if (id == panelId) View.VISIBLE else View.GONE
            }
        }
    }

    // ************************* Screen Analysis methods *****************************
    @SuppressLint("SetTextI18n")
    fun updatePointDebugText(point: Point, screenSize: DisplayMetrics, color: ColorCompat) {
        overlay!!.root.findViewById<TextView>(R.id.screen_size_tv).setText(
            "${screenSize.widthPixels}x${screenSize.heightPixels}")

        overlay!!.root.findViewById<TextView>(R.id.location_coords_tv).setText(
            "${point.x}x${point.y}")

        overlay!!.root.findViewById<TextView>(R.id.color_code_tv).setText("#$color")
        overlay!!.root.findViewById<TextView>(R.id.color_square_tv).setTextColor(color.value)
    }

    fun updateScreenCaptureViewer(bm: Bitmap) {
        overlay!!.root.findViewById<ImageView>(R.id.bitmap_imgview).setImageBitmap(bm)
    }

    // ************************* END Point Analysis methods *****************************

    fun updateOcrText(text: String) {
        if (overlay != null) {
            val ocrTv = overlay!!.root.findViewById<TextView>(R.id.screen_textview)
            ocrTv.text = text
        }
    }

    fun setOnCaptureTextButtonClick(cl: View.OnClickListener) {
        overlay!!.root.findViewById<Button>(R.id.overlay_ocr_capture_button).setOnClickListener(cl)
    }

    fun createScriptControllerUIHelper(controller: ScriptController): ScriptControllerUIHelper {
        return ScriptControllerUIHelper(
            context,
            overlay!!.root.findViewById(R.id.start_pause_button),
            overlay!!.root.findViewById(R.id.stop_button),
            overlay!!.root.findViewById(R.id.restart_button),
            overlay!!.logTv,
            overlay!!.root.findViewById(R.id.log_scrollview),
            controller
        )
    }

    // From ScriptApi.OverlayManager
    override fun getOverlayDimens(): Api.WinDimen? {
        val overlayView = overlay?.root
        val _wm = wm
        if (overlayView != null && _wm != null) {
            val screenSize = UiUtil.relativeDisplaySize(_wm.defaultDisplay)

            val posArr = intArrayOf(0, 0)
            overlayView.getLocationOnScreen(posArr)
            Log.d(TAG, "getOverlayDimens: screen pos: ${posArr.contentToString()}")
            Log.d(TAG, "getOverlayDimens: size: ${overlayView.width}, ${overlayView.height}")
            Log.d(TAG, "getOverlayDimens: screen: ${screenSize.x}, ${screenSize.y}")
            return Api.WinDimen(
                posArr[0].toFloat() / screenSize.x.toFloat(),
                posArr[1].toFloat() / screenSize.y.toFloat(),
                overlayView.width.toFloat() / screenSize.x.toFloat(),
                overlayView.height.toFloat() / screenSize.y.toFloat()
            )
        }
        return null
    }

    override fun onPointInspected(x: Float, y: Float, color: ColorCompat, isPercent: Boolean) {
        val screenSize = UiUtil.getDisplaySize(context)

        val realX: Int
        val realY: Int
        if (isPercent) {
            realX = ((screenSize.widthPixels - 1) * x).toInt()
            realY = ((screenSize.heightPixels - 1) * y).toInt()
        } else {
            realX = x.toInt()
            realY = y.toInt()
        }
        val point = Point(realX, realY)

        activity.runOnUiThread {
            updatePointDebugText(point, screenSize, color)
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
                wm.updateViewLayout(view, params)
                return true
            }
        }
        return false
    }
}

class ViewResizer(val targetView: View,
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
        val parent = targetView.parent as ViewGroup
        params.width = targetView.width

        params.height = parent.measuredHeight - parent.paddingTop - parent.paddingBottom
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
                return true
            }
            MotionEvent.ACTION_UP -> {
                correctTargetViewSize()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val params = targetView.layoutParams
                params.width = max(0, (widthOnDown + (event.rawX - touchX)).toInt())
                params.height = max(0, (heightOnDown + (event.rawY - touchY)).toInt())
                targetView.layoutParams = params
                return true
            }
        }
        return false
    }
}
