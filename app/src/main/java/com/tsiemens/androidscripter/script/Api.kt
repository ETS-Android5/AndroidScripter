package com.tsiemens.androidscripter.script

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.tsiemens.androidscripter.getUsageStatsForegroundActivityName
import com.tsiemens.androidscripter.service.ScriptAccessService
import com.tsiemens.androidscripter.service.ServiceBcastClient
import com.tsiemens.androidscripter.service.WindowState
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write
import kotlin.math.min

@Suppress("UNUSED")
class Api(val ctx: Context,
          val logChangeListener: LogChangeListener?,
          val screenProvider: ScreenProvider?,
          val overlayManager: OverlayManager?) {
    val logLock = ReentrantReadWriteLock()
    val paused = AtomicBoolean(false)

    val serviceClient = ServiceBcastClient(ctx)

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

    class LogEntry(val message: String) {
        val time = System.currentTimeMillis()

        fun prettyTime(): String {
            return dateFormat.format(Date(time))
        }

        override fun toString(): String {
            return "${prettyTime()}: $message"
        }
    }

    val logLines = arrayListOf<LogEntry>()

    interface LogChangeListener {
        fun onLogChanged(newLog: LogEntry)
    }

    interface ScreenProvider {
        fun getScreenCap(): Bitmap?
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
        return screenProvider?.getScreenCap()
    }

    fun isNetworkMetered(): Boolean? {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(net)
        if (caps != null) {
            return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
        return null
    }

    fun sendClick(x: Float, y: Float, isPercent: Boolean = false) {
        serviceClient.sendClick(x, y, isPercent)
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

    fun logInternal(str: String) {
        val newLog = LogEntry(str)
        logLock.write {
            logLines.add(LogEntry(str))
        }
        logChangeListener?.onLogChanged(newLog)
    }

    fun log(str: String) {
        logInternal(str)
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