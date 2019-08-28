package com.tsiemens.androidscripter.widget

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ScrollView
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
                               val logScrollView: ScrollView,
                               val controller: ScriptController) {

    init {
        startButton.setOnClickListener { controller.onStartPressed() }
        stopButton.setOnClickListener { controller.onStopPressed() }
        logScrollView.addOnLayoutChangeListener {
                view: View, i: Int, i1: Int, i2: Int, i3: Int, i4: Int, i5: Int, i6: Int, i7: Int ->
            scrollLogToBottom()
        }
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

    private fun scrollLogToBottom() {
        logScrollView.apply {
            val lastChild = getChildAt(childCount - 1)
            val bottom = lastChild.bottom + paddingBottom
            val delta = bottom - (scrollY+ height)
            smoothScrollBy(0, delta)
        }
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