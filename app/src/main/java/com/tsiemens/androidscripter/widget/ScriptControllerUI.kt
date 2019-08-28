package com.tsiemens.androidscripter.widget

import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import com.tsiemens.androidscripter.ScriptApi

interface ScriptController {
    fun onStartPressed()
    fun onStopPressed()
    fun scriptIsRunning(): Boolean
    fun scriptIsRunnable(): Boolean
}

class ScriptControllerUIHelper(val startButton: Button,
                               val stopButton: Button,
                               val logText: TextView,
                               val controller: ScriptController) {

    init {
        startButton.setOnClickListener { controller.onStartPressed() }
        stopButton.setOnClickListener { controller.onStopPressed() }
        notifyScriptRunningStateChanged()
    }

    fun notifyScriptRunningStateChanged() {
        val runnable = !controller.scriptIsRunning() && controller.scriptIsRunnable()
        startButton.isEnabled = runnable
        stopButton.isEnabled = !runnable
    }

    fun onLog(newLog: ScriptApi.LogEntry) {
        logText.append(newLog.toString() + "\n")
    }
}

class ScriptControllerUIHelperColl {
    val helpers = arrayListOf<ScriptControllerUIHelper>()

    private fun forceToMainThread(thing: ()->Unit) {
        val mainLooper = Looper.getMainLooper()
        if (mainLooper.thread != Thread.currentThread()) {
            Handler(mainLooper).post(thing)
        } else {
            thing()
        }
    }

    fun notifyScriptRunnabilityStateChanged() {
        forceToMainThread {
            helpers.forEach {
                it.notifyScriptRunningStateChanged()
            }
        }
    }

    fun onLog(newLog: ScriptApi.LogEntry) {
        forceToMainThread {
            helpers.forEach {
                it.onLog(newLog)
            }
        }
    }
}