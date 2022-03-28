package com.tsiemens.androidscripter.script

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.DisplayMetrics
import android.util.Log
import com.tsiemens.androidscripter.getUsageStatsForegroundActivityName
import com.tsiemens.androidscripter.inspect.ScreenProvider
import com.tsiemens.androidscripter.ml.MlKitOcrHelper
import com.tsiemens.androidscripter.notify.ScreenInspectionListener
import com.tsiemens.androidscripter.service.ScriptAccessService
import com.tsiemens.androidscripter.service.ServiceBcastClient
import com.tsiemens.androidscripter.service.WindowState
import com.tsiemens.androidscripter.util.BitmapUtil
import com.tsiemens.androidscripter.util.ColorCompat
import com.tsiemens.androidscripter.util.UiUtil
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

@Suppress("UNUSED")
class Api(val ctx: Context,
          val logChangeListener: LogChangeListener?,
          val screenProvider: ScreenProvider?,
          val screenInspectionListener: ScreenInspectionListener?,
          val overlayManager: OverlayManager?,
          val debugOverlayManager: DebugOverlayManager?) {
    val paused = AtomicBoolean(false)

    val serviceClient = ServiceBcastClient(ctx)
    val mlKitOcrHelper = MlKitOcrHelper()

    companion object {
        val TAG = Api::class.java.simpleName

        private val dateFormat: SimpleDateFormat =
            createDateFormat()

        private fun createDateFormat(): SimpleDateFormat {
            val sdf = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())
            sdf.timeZone = TimeZone.getDefault()
            return sdf
        }
    }

    enum class LogLevel(val priority: Int) {
        DEBUG(0), VERBOSE(1), INFO(2), WARNING(3), ERROR(4)
    }

    class LogEntry(val message: String, val level: LogLevel) {
        val time = System.currentTimeMillis()

        fun prettyTime(): String {
            return dateFormat.format(Date(time))
        }

        override fun toString(): String {
            return "${prettyTime()}: $message"
        }
    }

    interface LogChangeListener {
        fun onLogChanged(newLog: LogEntry)
    }

    // values are between 0 and 1.0
    class WinDimen(val xPct: Float, val yPct: Float,
                   val widthPct: Float, val heightPct: Float) {
        fun contains(pt: Point, screenWidth: Int, screenHeight: Int): Boolean {
            val ptXPct: Float = pt.x.toFloat() / screenWidth.toFloat()
            val ptYPct: Float = pt.y.toFloat() / screenHeight.toFloat()
            return (ptXPct >= xPct && ptYPct >= yPct &&
                    ptXPct < (xPct + widthPct) && ptYPct < (yPct + heightPct))
        }
    }

    interface OverlayManager {
        fun getOverlayDimens(): WinDimen?
    }

    interface DebugOverlayManager {
        fun onClickSent(x: Float, y: Float, isPercent: Boolean = false)
        fun onXsFound(res: ScreenUtil.XDetectResult)
    }

    private fun maybeEndThread() {
        if (Thread.currentThread().isInterrupted) {
            Log.i(TAG, "Interrupting thread")
            throw InterruptedException()
        }
    }

    private fun maybePauseThread() {
        if (paused.get()) {
            Log.i(TAG, "Pausing thread")
            logInternal("SCRIPT PAUSED")
            while (paused.get()) {
                Thread.sleep(1000)
                maybeEndThread()
            }
            Log.i(TAG, "Resuming thread")
            logInternal("SCRIPT RESUMED")
        }
    }

    private fun handlePendingSignals() {
        maybeEndThread()
        maybePauseThread()
    }

    // This should be used as a backup from foregroundWindowState
    fun foregroundActivityPackage(): String? {
        handlePendingSignals()
        return getUsageStatsForegroundActivityName(ctx)
    }

    fun foregroundWindowState(): WindowState? {
        handlePendingSignals()
        return ScriptAccessService.currWindowState
    }

    fun getOverlayDimens(): WinDimen? {
        val wd = overlayManager?.getOverlayDimens()
        if (wd != null) {
            Log.d(TAG, "wd: ${wd.xPct}, ${wd.yPct}, ${wd.widthPct}, ${wd.heightPct}")
        } else {
            Log.d(TAG, "wd: null")
        }
        return wd
    }

    fun getScreenCap(): Bitmap? {
        return screenProvider?.getScreenCap(cropPadding = true)
    }

    class ScreenXsResult(val xs: List<ScreenUtil.Cross>?,
                         val screenCapImgDimens: Point?,
                         val screenDimens: DisplayMetrics?,
                         val ok: Boolean) {
        fun getXScreenRelativeCenter(x: ScreenUtil.Cross): org.opencv.core.Point {
            val center = x.center()
            return org.opencv.core.Point(
                (center.x / screenCapImgDimens!!.x) * screenDimens!!.widthPixels,
                (center.y / screenCapImgDimens.y) * screenDimens.heightPixels
            )
        }
    }

    fun findXsInScreen(showDebugOverlay: Boolean = true): ScreenXsResult {
        val bm = getScreenCap()
        if (bm != null) {
            val croppedBitmap = BitmapUtil.cropScreenshotPadding(bm)
            val xs = ScreenUtil.findXs(croppedBitmap)
            if (showDebugOverlay) {
                debugOverlayManager?.onXsFound(xs)
            }
            return ScreenXsResult(xs.xs, Point(croppedBitmap.width, croppedBitmap.height),
                                  UiUtil.getDisplaySize(ctx), true)
        } else {
            Log.e(TAG, "findXsInScreen: could not get screen cap")
        }
        return ScreenXsResult(null, null, null, false)
    }

    fun extractTextInScreenCap(bm_: Bitmap, area: Rect?): List<String>? {
        if (area != null) {
            screenInspectionListener?.onAreaInspected(area)
        }
        Log.d(TAG, "extractTextInScreen with area: $area")
        var bm: Bitmap? = bm_
        if (area != null) {
            bm = BitmapUtil.cropScreenshotToSubArea(bm_, area, UiUtil.getDisplaySize(ctx))
            if (bm == null) {
                Log.e(TAG, "extractTextInScreen: could not crop bitmap.")
                return null
            }
        }

        val text = mlKitOcrHelper.extractText(bm!!) ?: return null
        val textBlocks = arrayListOf<String>()
        for (tb in text.textBlocks) {
            textBlocks.add(tb.text)
        }
        return textBlocks
    }

    fun extractTextInScreen(area: Rect?): List<String>? {
        val bm = getScreenCap()
        if (bm != null) {
            return extractTextInScreenCap(bm, area)
        }
        Log.e(TAG, "extractTextInScreen: could not get screen cap")
        return null
    }

    private fun networkCapabilities(): NetworkCapabilities? {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return null
        return cm.getNetworkCapabilities(net)
    }

    fun isNetworkMetered(): Boolean? {
        val caps = networkCapabilities()
        if (caps != null) {
            return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
        return null
    }

    fun getNetworkTransports(): Set<String> {
        val transportSet = hashSetOf<String>()
        val caps = networkCapabilities()
        if (caps != null) {
            val capsToStr = hashMapOf(
                NetworkCapabilities.TRANSPORT_BLUETOOTH to "bluetooth",
                NetworkCapabilities.TRANSPORT_CELLULAR to "cellular",
                NetworkCapabilities.TRANSPORT_ETHERNET to "ethernet",
                NetworkCapabilities.TRANSPORT_VPN to "vpn",
                NetworkCapabilities.TRANSPORT_WIFI to "wifi"
            )

            for ((transport, str) in capsToStr) {
                if (caps.hasTransport(transport)) {
                    transportSet.add(str)
                }
            }
        }
        return transportSet
    }

    fun notifyPointInspected(x: Float, y: Float, color: ColorCompat, isPercent: Boolean = false) {
        screenInspectionListener?.onPointInspected(x, y, color, isPercent)
    }

    fun sendClick(x: Float, y: Float, isPercent: Boolean = false) {
        serviceClient.sendClick(x, y, isPercent)
        debugOverlayManager?.onClickSent(x, y, isPercent)
    }

    fun pressBack() {
        serviceClient.pressBack()
    }

    fun pressHome() {
        serviceClient.pressHome()
    }

    fun pressRecentApps() {
        serviceClient.pressRecentApps()
    }

    fun logInternal(str: String, level: LogLevel = LogLevel.INFO) {
        val newLog = LogEntry(str, level)
        logChangeListener?.onLogChanged(newLog)
    }

    fun log(str: String, level: LogLevel = LogLevel.INFO) {
        logInternal(str, level)
        handlePendingSignals()
    }

    fun sleep(seconds: Float) {
        var remainingMillis = (seconds * 1000).toLong()
        val interval: Long = 1000
        while (remainingMillis > 0) {
            handlePendingSignals()
            val nextSleep = min(interval, remainingMillis)
            Thread.sleep(nextSleep)
            remainingMillis -= nextSleep
        }
    }
}