package com.tsiemens.androidscripter.widget

import android.support.v7.widget.AppCompatImageButton
import android.util.Log
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import com.tsiemens.androidscripter.R
import com.tsiemens.androidscripter.script.Api
import android.content.Context
import com.tsiemens.androidscripter.util.DrawableUtil
import com.tsiemens.androidscripter.util.UiUtil.Companion.forceToMainThread


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

class ScriptControllerUIHelper(val context: Context,
                               val startPauseButton: AppCompatImageButton,
                               val stopButton: AppCompatImageButton,
                               val restartButton: AppCompatImageButton,
                               val logText: TextView,
                               val logScrollView: ScrollView,
                               val controller: ScriptController) {

    companion object {
        val TAG = ScriptControllerUIHelper::class.java.simpleName

        private val MAX_LOG_SIZE_CHARS = 80 * 200 // Around 80 chars per line
        private val LOG_TRIM_TO_CHARS = 80 * 100 // Around 80 chars per line
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

    fun setButtonEnabled(i: AppCompatImageButton, enable: Boolean, res: Int) {
        i.isEnabled = enable
        val originalIcon = context.resources.getDrawable(res, null)
        val icon = if (enable) originalIcon else DrawableUtil.convertDrawableToGrayScale(originalIcon)
        i.setImageDrawable(icon)
    }

    fun notifyScriptStateChanged() {
        Log.d(TAG, "notifyScriptChanged: state: ${controller.getScriptState()}")
        val startPauseImgRes = when(controller.getScriptState()) {
                ScriptState.running -> R.drawable.ic_pause_black_48dp
                ScriptState.paused, ScriptState.stopped -> R.drawable.ic_play_arrow_black_48dp
            }

        val scriptState = controller.getScriptState()
        val runnable = controller.scriptIsRunnable()
        setButtonEnabled(startPauseButton, runnable, startPauseImgRes)
        setButtonEnabled(stopButton, scriptState != ScriptState.stopped, R.drawable.ic_stop_black_48dp)
        setButtonEnabled(restartButton, runnable && scriptState != ScriptState.stopped, R.drawable.ic_replay_black_48dp)
    }

    fun onLog(newLog: Api.LogEntry) {
        logText.append(newLog.toString() + "\n")
        val nChars = logText.length()
        if (nChars > MAX_LOG_SIZE_CHARS) {
            val nCharsToDrop = nChars - LOG_TRIM_TO_CHARS
            logText.text = logText.text.drop(nCharsToDrop)
            Log.i(TAG, "Dropped $nCharsToDrop characters from log")
        }
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

    fun notifyScriptStateChanged() {
        forceToMainThread {
            helpers.forEach {
                it.notifyScriptStateChanged()
            }
        }
    }

    fun onLog(newLog: Api.LogEntry) {
        forceToMainThread {
            helpers.forEach {
                it.onLog(newLog)
            }
        }
    }
}