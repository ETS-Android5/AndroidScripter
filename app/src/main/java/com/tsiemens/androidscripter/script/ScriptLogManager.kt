package com.tsiemens.androidscripter.script

import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

interface ScriptLogLevelListener {
    fun onLogLevelChanged(logLevel: Api.LogLevel)
}

class ScriptLogManager {
    companion object{
        val MAX_LOGS = 200
    }
    val logs = HashMap<Api.LogLevel, Deque<Api.LogEntry>>()
    var logLevel = Api.LogLevel.DEBUG

    val logLevelListeners = ArrayList<ScriptLogLevelListener>()

    init {
        for (level in Api.LogLevel.values()) {
            logs[level] = ArrayDeque(MAX_LOGS)
        }
    }

    fun addLog(log: Api.LogEntry) {
        val deque = logs[log.level]!!

        if (deque.size == MAX_LOGS) {
            deque.remove()
        }
        deque.add(log)
    }

    fun levelEnabled(level: Api.LogLevel): Boolean {
        return logLevel.priority <= level.priority
    }

    fun logSequence(): Sequence<Api.LogEntry> {
        return sequence<Api.LogEntry> {
            val nextEntries = HashMap<Api.LogLevel, Api.LogEntry?>()
            val levelIters = HashMap<Api.LogLevel, Iterator<Api.LogEntry>>()
            for (level in Api.LogLevel.values()) {
                if (!levelEnabled(level)) {
                    continue
                }
                val iter = logs[level]!!.iterator()
                levelIters[level] = iter
                nextEntries[level] = if (iter.hasNext()) iter.next() else null
            }

            var logsLeft = true
            while (logsLeft) {
                logsLeft = false
                var oldestTime: Long = Long.MAX_VALUE - 1
                var oldestLog: Api.LogEntry? = null
//                var secondOldestTime: Long = Long.MAX_VALUE
//                var secondOldestLog: Api.LogEntry? = null

                for ((level, entry) in nextEntries) {
                    if (entry != null) {
                        logsLeft = true
                        if (entry.time < oldestTime) {
                            oldestTime = entry.time
                            oldestLog = entry
//                        } else if (entry.time < secondOldestTime) {
//                            secondOldestTime = entry.time
//                            secondOldestLog = entry
                        }
                    }
                }
                if (oldestLog != null) {
                    yield(oldestLog)
                    val levelIter = levelIters[oldestLog.level]
                    nextEntries[oldestLog.level] = if (levelIter!!.hasNext()) levelIter.next() else null
                }

//                if (oldestLog != null) {
//                    yield(oldestLog)
//                    val oldestLevelIter = levelIters[oldestLog.level]
//                    val nextLogInLevel = if (oldestLevelIter!!.hasNext()) oldestLevelIter.next() else null
//                    nextEntries[oldestLog.level] = nextLogInLevel
//                    if (nextLogInLevel != null && nextLogInLevel.time < secondOldestTime) {
//                        oldestLog = nextLogInLevel
//                        oldestTime = nextLogInLevel.time
//                    } else {
//                        oldestLog = secondOldestLog
//                        oldestTime = secondOldestTime
//                        secondOldestLog = null
//                        secondOldestTime = Long.MAX_VALUE
//                    }
//                }
            }


        }
    }

    fun addLogLevelListener(l: ScriptLogLevelListener) {
        logLevelListeners.add(l)
    }

    fun changeLogLevel(newLogLevel: Api.LogLevel) {
        if (newLogLevel == logLevel) {
            return
        }
        logLevel = newLogLevel
        for (listener in logLevelListeners) {
            listener.onLogLevelChanged(newLogLevel)
        }
    }
}