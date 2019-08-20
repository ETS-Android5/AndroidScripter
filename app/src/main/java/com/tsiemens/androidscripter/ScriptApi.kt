package com.tsiemens.androidscripter

import android.content.Context
import android.util.Log
import java.lang.RuntimeException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

class ScriptUtilException(msg: String): RuntimeException(msg)

@Suppress("UNUSED")
class ScriptApi(val ctx: Context, val logChangeListener: LogChangeListener?) {
    val logLock = ReentrantReadWriteLock()
    val interrupted = AtomicBoolean(false)

    companion object {
        val TAG = ScriptApi::class.java.simpleName
    }

    inner class LogEntry(val message: String) {
        val time = System.currentTimeMillis()

        override fun toString(): String {
            return "$time: $message"
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

    fun foregroundActivityPackage(): String {
        maybeEndThread()
        val pack = getUsageStatsForegroundActivityName(ctx)
        if (pack == null) {
            throw ScriptUtilException("Could not get foreground activity")
        }
        return pack
    }

    fun log(str: String) {
        val newLog = LogEntry(str)
        logLock.write {
            logLines.add(LogEntry(str))
        }
        logChangeListener?.onLogChanged(newLog)
        maybeEndThread()
    }
}