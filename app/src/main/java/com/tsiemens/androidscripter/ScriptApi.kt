package com.tsiemens.androidscripter

import android.content.Context
import android.util.Log
import java.lang.RuntimeException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

class ScriptUtilException(msg: String): RuntimeException(msg)

@Suppress("UNUSED")
class ScriptApi(val ctx: Context, val logChangeListener: LogChangeListener?) {
    val logLock = ReentrantReadWriteLock()
    val interrupted = AtomicBoolean(false)

    val serviceClient = ServiceBcastClient(ctx)

    companion object {
        val TAG = ScriptApi::class.java.simpleName

        private val dateFormat: SimpleDateFormat = createDateFormat()

        private fun createDateFormat(): SimpleDateFormat {
            val sdf = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())
            sdf.timeZone = TimeZone.getDefault()
            return sdf
        }
    }

    inner class LogEntry(val message: String) {
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

    private fun maybeEndThread() {
        if (Thread.currentThread().isInterrupted) {
            Log.i(TAG, "Interrupting thread")
            throw InterruptedException()
        }
    }

    // This should be used as a backup from foregroundWindowState
    fun foregroundActivityPackage(): String? {
        maybeEndThread()
        return getUsageStatsForegroundActivityName(ctx)
    }

    fun foregroundWindowState(): WindowState? {
        maybeEndThread()
        return ScriptService2.currWindowState
    }

    fun sendClick(x: Float, y: Float, isPercent: Boolean = false) {
        serviceClient.sendClick(x, y, isPercent)
    }

    fun pressBack() {
        serviceClient.pressBack()
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
        maybeEndThread()
    }
}