package com.tsiemens.androidscripter.overlay

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
import com.tsiemens.androidscripter.R
import com.tsiemens.androidscripter.inspect.ScreenProvider
import com.tsiemens.androidscripter.notify.ScreenInspectionListener
import com.tsiemens.androidscripter.script.Api
import com.tsiemens.androidscripter.script.ScriptLogManager
import com.tsiemens.androidscripter.util.ColorCompat
import com.tsiemens.androidscripter.util.UiUtil
import com.tsiemens.androidscripter.widget.ScriptController
import com.tsiemens.androidscripter.widget.ScriptControllerUIHelper
import kotlin.math.max

class OverlayContainer(val root: View,
                       val positionParams: WindowManager.LayoutParams) {
    val sizeFrame = root.findViewById<View>(R.id.overlay_details_all)!!
    val spinner = root.findViewById<Spinner>(R.id.overlay_detail_spinner)!!
    val logTv = root.findViewById<TextView>(R.id.log_tv)!!
}

// https://stackoverflow.com/questions/4481226/creating-a-system-overlay-window-always-on-top
class OverlayManager(val activity: Activity,
                     val scriptLogManager: ScriptLogManager,
                     val screenProvider: ScreenProvider?,
                     val screenInspectionListener: ScreenInspectionListener?):
    OverlayManagerBase(activity), Api.OverlayManager {
    companion object {
        val TAG: String = OverlayManager::class.java.simpleName
    }

    private var wm: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null
    private var overlay: OverlayContainer? = null

    var onDestroyListener: (()->Unit)? = null

    private val touchInterceptOverlayManager =
        TouchInterceptOverlayManager(
            activity,
            screenProvider,
            screenInspectionListener
        )

    @TargetApi(Build.VERSION_CODES.O)
    @SuppressWarnings("ClickableViewAccessibility")
    override fun showOverlay() {
        if (overlay != null) {
            return
        }
        Log.v(TAG, "showOverlay")
        val overlayRoot = LayoutInflater.from(context).inflate(R.layout.overlay_base, null)
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

        val newOverlay = OverlayContainer(
            overlayRoot,
            params!!
        )
        this.overlay = newOverlay

        val sizePositionController =
            OverlaySizeAndPositionController(
                newOverlay,
                activity,
                wm!!
            )

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

        newOverlay.spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(parent: AdapterView<*>?) {
                newOverlay.spinner.setSelection(0)
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

        val handle = overlayRoot.findViewById<View>(R.id.overlay_handle)
        handle.setOnTouchListener(
            ViewDragger(
                sizePositionController
            )
        )

        val sizeHandle = overlayRoot.findViewById<View>(R.id.overlay_sizing_handle)
        sizeHandle.setOnTouchListener(
            ViewResizer(
                newOverlay.sizeFrame,
                wm!!
            )
        )

        val screenSize = Point()
        wm!!.defaultDisplay.getSize(screenSize)
        params!!.x = 0
        params!!.y = (screenSize.y * 0.3).toInt()
        wm!!.updateViewLayout(overlayRoot, params)

        // Limit the default width of the overlay
        val sizeFrameParams = newOverlay.sizeFrame.layoutParams
        val largestScreenLength = max(screenSize.x, screenSize.y)
        sizeFrameParams.width = (largestScreenLength * 0.4).toInt()

        overlayRoot.findViewById<Button>(R.id.overlay_record_tap_button).setOnClickListener {
            if (!touchInterceptOverlayManager.started()) {
                touchInterceptOverlayManager.showOverlay()
            }
        }

        overlayRoot.addOnLayoutChangeListener(
            OverlayRootListener(
                sizePositionController
            )
        )
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
                R.id.overlay_ocr_panel
            )
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
        overlay?.root?.findViewById<ImageView>(R.id.bitmap_imgview)?.setImageBitmap(bm)
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
            overlay!!.root.findViewById(R.id.log_level_spinner),
            scriptLogManager,
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

    fun onPointInspected(x: Float, y: Float, color: ColorCompat, isPercent: Boolean) {
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

open class MobileViewChangerBase(val overlayContainer: OverlayContainer,
                                 val context: Context,
                                 val wm: WindowManager) {
    val cachedDisplaySize = DisplayMetrics()
    val cachedLocationInScreen = IntArray(2)

    companion object {
        val TAG = OverlayManager::class.java.simpleName
    }

    fun posParams(): WindowManager.LayoutParams {
        return overlayContainer.positionParams
    }

    fun view(): View {
        return overlayContainer.root
    }

    fun refreshDisplaySizeCache() {
        UiUtil.getDisplaySize(context, cachedDisplaySize)
    }

    fun refreshLocationOnScreenCache() {
        overlayContainer.root.getLocationOnScreen(cachedLocationInScreen)
    }

    fun getPixelsPastBottom(): Int {
        refreshDisplaySizeCache()
        // Note this is not perfect. If the overlay root's 0,0 isn't padded in (due to the status bar),
        // this value won't quite be precise. Could be improved later, but in terms of "usability",
        // this appears to be mostly good enough for now.
        return posParams().y + overlayContainer.root.height - cachedDisplaySize.heightPixels
    }

    fun updateRootPosition(x: Int, y: Int) {
        overlayContainer.positionParams.x = x
        overlayContainer.positionParams.y = y
        wm.updateViewLayout(overlayContainer.root, overlayContainer.positionParams)
    }
}

class OverlaySizeAndPositionController(overlayContainer: OverlayContainer,
                                       context: Context,
                                       wm: WindowManager):
    MobileViewChangerBase(overlayContainer, context, wm) {

    // *****************************
    // Drag touch state
    // *****************************
    var xOnDown: Int = 0
    var yOnDown: Int = 0
    var touchX: Float = 0f
    var touchY: Float = 0f
    var beingTouched = false

    fun onDragHandleTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                beingTouched = true
                // We have to use these, because view().x/y is always 0 for this overlay, for some
                // reason.
                xOnDown = posParams().x
                yOnDown = posParams().y
                touchX = event.rawX
                touchY = event.rawY
                return true
            }
            MotionEvent.ACTION_UP -> {
                beingTouched = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val newX = xOnDown + (event.rawX - touchX).toInt()
                val newY = yOnDown + (event.rawY - touchY).toInt()
                updateRootPosition(newX, newY)
                return true
            }
        }
        return false
    }

    fun onRootLayoutChange() {
        if (beingTouched) {
            return
        }

        // Note that these distances past the border are often not visible. When the positionParams
        // specifies a value too large, generally what would happens is that it just gets stuck
        // at the edge.
        var didChange = false
        val pixelsPastBottom = getPixelsPastBottom()
        val newX = posParams().x
        var newY = posParams().y
        if (pixelsPastBottom > 0) {
            newY = max(0, posParams().y - pixelsPastBottom)
            didChange = true
        }
        if (didChange) {
            Log.d(TAG, "onRootLayoutChange: fixing position")
            updateRootPosition(newX, newY)
        }
    }
}

class ViewDragger(val controller: OverlaySizeAndPositionController):
    View.OnTouchListener {

    @SuppressWarnings("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        return controller.onDragHandleTouch(v, event)
    }
}

class ViewResizer(val targetView: View,
                  val wm: WindowManager): View.OnTouchListener {
    var widthOnDown: Int = 0
    var heightOnDown: Int = 0
    var touchX: Float = 0f
    var touchY: Float = 0f

    private fun fixTargetViewSize(): ViewGroup.LayoutParams {
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
                val params = fixTargetViewSize()
                widthOnDown = params.width
                heightOnDown = params.height
                touchX = event.rawX
                touchY = event.rawY
                return true
            }
            MotionEvent.ACTION_UP -> {
                fixTargetViewSize()
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

class OverlayRootListener(val controller: OverlaySizeAndPositionController):
    View.OnLayoutChangeListener {

    override fun onLayoutChange(p0: View?, p1: Int, p2: Int, p3: Int, p4: Int,
                                p5: Int, p6: Int, p7: Int, p8: Int) {
        controller.onRootLayoutChange()
    }

}
