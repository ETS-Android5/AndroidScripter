package com.tsiemens.androidscripter.widget

import android.os.Handler
import android.os.Looper
import android.support.v7.widget.AppCompatImageButton
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import com.tsiemens.androidscripter.R
import com.tsiemens.androidscripter.ScriptApi

enum class ScriptState {
    running,
    paused,
    stopped,
}

interface ScriptController {
    fun onStartPressed()
    fun onPausePressed()
    fun onStopPressed()
    fun onRestartPressed()
    fun getScriptState(): ScriptState
    @Deprecated("")
    fun scriptIsRunning(): Boolean
    /** Should return true if the script is in a state where we could start it.
        Should still return true if the script is currently running */
    fun scriptIsRunnable(): Boolean
}

class ScriptControllerUIHelper(val startPauseButton: AppCompatImageButton,
                               val stopButton: AppCompatImageButton,
                               val restartButton: AppCompatImageButton,
                               val logText: TextView,
                               val logScrollView: ScrollView,
                               val controller: ScriptController) {

    companion object {
        val TAG = ScriptControllerUIHelper::class.java.simpleName
    }

    init {
        stopButton.setOnClickListener { controller.onStopPressed() }
        startPauseButton.setOnClickListener {
            when (controller.getScriptState()) {
                ScriptState.running -> controller.onPausePressed()
                ScriptState.paused, ScriptState.stopped -> controller.onStartPressed()
            }
        }
        restartButton.setOnClickListener { controller.onRestartPressed() }
        logScrollView.addOnLayoutChangeListener {
                view: View, i: Int, i1: Int, i2: Int, i3: Int, i4: Int, i5: Int, i6: Int, i7: Int ->
            scrollLogToBottom()
        }
        notifyScriptStateChanged()
    }

    fun notifyScriptStateChanged() {
        val scriptState = controller.getScriptState()
        val runnable = controller.scriptIsRunnable()
        startPauseButton.isEnabled = runnable
        stopButton.isEnabled = scriptState != ScriptState.stopped
        restartButton.isEnabled = runnable && scriptState != ScriptState.stopped

        Log.d(TAG, "notifyScriptChanged. state: ${controller.getScriptState()}")
        startPauseButton.setImageResource(when(controller.getScriptState()) {
            ScriptState.running -> R.drawable.ic_pause_black_48dp
            ScriptState.paused, ScriptState.stopped -> R.drawable.ic_play_arrow_black_48dp
        })
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

    fun notifyScriptStateChanged() {
        forceToMainThread {
            helpers.forEach {
                it.notifyScriptStateChanged()
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