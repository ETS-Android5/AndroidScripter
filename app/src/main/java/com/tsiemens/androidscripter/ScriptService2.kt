package com.tsiemens.androidscripter

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Point
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class ScriptService2 : AccessibilityService() {
    companion object {
        val TAG = ScriptService2::class.java.simpleName
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected")
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // get the source node of the event
        var source = "null"
        if (event.source?.text != null) {
            // nothing, just to remember
            source = event.source.text.toString()
        }

        Log.d(TAG, "eventType: %x, text: %s".format(event.eventType, source))

        if (event.source?.text != "DUMMY") {
            return
        }

        val tabGest = makeClickGesturePercent(0.5f, 0.8f)

        // callback invoked either when the gesture has been completed or cancelled
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                super.onCompleted(gestureDescription)
                Log.d(TAG, "gesture completed")
            }

            override fun onCancelled(gestureDescription: GestureDescription) {
                super.onCancelled(gestureDescription)
                Log.d(TAG, "gesture cancelled")
            }
        }

        event.source?.apply {

            val ret = dispatchGesture(tabGest, callback, null)
            Log.d(TAG, "dispatchGesture: $ret")
            // Use the event and node information to determine
            // what action to take

            // findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            // take action on behalf of the user
            // performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)

            // recycle the nodeInfo object
            recycle()
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        val ret = super.onUnbind(intent)
        Log.i(TAG, "onUnbind")
        return ret
    }

    fun makeClickGesturePercent(xPct: Float, yPct: Float): GestureDescription {
        if (xPct > 1.0 || yPct > 1.0) {
            throw IllegalArgumentException("Percent value must be from 0 to 1")
        }
        val wm =  getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val outPoint = Point()
        wm.defaultDisplay.getSize(outPoint)

        return makeClickGesture(outPoint.x * xPct, outPoint.y * yPct)
    }

    fun makeClickGesture(x: Float, y: Float): GestureDescription {
        val touchPath = Path().apply {
            moveTo(x, y)
        }

        return GestureDescription.Builder().addStroke(
            GestureDescription.StrokeDescription(
                touchPath,
                0L,
                10L
            )
        ).build()
    }
}

class AccessibilitySettingDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            // Use the Builder class for convenient dialog construction
            val builder = AlertDialog.Builder(it)
            builder.setMessage("This app must register an accessibility service in order to work correctly. Allow?")
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    launchAccessibilitySettings(context!!)
                }
                .setNegativeButton(android.R.string.no) { _, _ -> }
            // Create the AlertDialog object and return it
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}
