package com.tsiemens.androidscripter.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Point
import android.os.Bundle
import android.os.Handler
import androidx.fragment.app.DialogFragment
import androidx.appcompat.app.AlertDialog
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.tsiemens.androidscripter.launchAccessibilitySettings

class NodeHandle(val node : AccessibilityNodeInfo) {
    var refCount = 1
    val timestamp = System.currentTimeMillis()

    init {
        Log.d("NodeHandle", "grabbing ref $refCount on node $timestamp")
    }

    fun inc() {
        if (refCount == 0) {
            throw java.lang.IllegalStateException("Already released")
        }
        refCount++
        Log.d("NodeHandle", "grabbing ref $refCount on node $timestamp")
    }

    fun dec() {
        Log.d("NodeHandle", "releasing ref $refCount on node $timestamp")
        refCount--
        if (refCount == 0) {
            Log.d("NodeHandle", "recycling node $timestamp")
            node.recycle()
        }
    }
}

class WindowState(val pkg: String?, val activity: String?)

class ScriptAccessService : AccessibilityService() {
//    var lastNode : NodeHandle? = null
//    var lastWindowStateNode : NodeHandle? = null
    companion object {
        val TAG = ScriptAccessService::class.java.simpleName

        val ACTION_TO_SERVICE = "com.tsiemens.androidscripter.LBcastToScriptService"
        val ACTION_FROM_SERVICE = "com.tsiemens.androidscripter.LBcastFromScriptService"

        var currWindowState: WindowState? = null
    }

    private class ServiceBcastReceiver(val service: ScriptAccessService) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            service.onReceiveBcast(intent)
        }
    }
    private val bcastReceiver =
        ServiceBcastReceiver(this)

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected")

        LocalBroadcastManager.getInstance(this).registerReceiver(
            bcastReceiver, IntentFilter(ACTION_TO_SERVICE))
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) {
            return
        }
        // get the source node of the event
        var sourceStr = "null"
        var typeStr = "?"

        val sourceObj = event.source ?: return
        val sourceText = sourceObj.text
        if (sourceText != null) {
            // nothing, just to remember
            sourceStr = sourceText.toString()
        }

//        lastNode?.dec()
//        lastNode = NodeHandle(event.source)

        when(event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                typeStr = "WINDOW_STATE_CHANGED"
                currWindowState =
                    WindowState(
                        event.packageName?.toString(),
                        event.className?.toString()
                    )
//                lastWindowStateNode?.dec()
//                lastWindowStateNode = lastNode
//                lastNode?.inc()
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED ->
                typeStr = "VIEW_CLICKED"
        }

        Log.d(TAG, "eventType: %x (%s), text: %s".format(event.eventType, typeStr, sourceStr))
        Log.d(TAG, "package: ${event.packageName}, type: ${event.className}")

        if (event.source?.text != "DUMMY") {
            return
        }

        event.source?.apply {
            recycle()
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        val ret = super.onUnbind(intent)
        Log.i(TAG, "onUnbind")
        LocalBroadcastManager.getInstance(this).unregisterReceiver(bcastReceiver)
        return ret
    }

    fun onReceiveBcast(intent: Intent) {
        val type = intent.getStringExtra("type")
        Log.d(TAG, "onReceiveBcast $type")
        when(type) {
            ServiceBcast.TYPE_OCR_SCREENSHOT -> {
                doOcr()
            }
            ServiceBcast.TYPE_CLICK -> {
                val x = intent.getFloatExtra("x", Float.MAX_VALUE)
                val y = intent.getFloatExtra("y", Float.MAX_VALUE)
                val isPercent = intent.getBooleanExtra("isPercent", false)
                if (x == Float.MAX_VALUE || y == Float.MAX_VALUE) {
                    Log.e(TAG, "TYPE_CLICK action provided with invalid x,y")
                } else {
                    doClick(x, y, isPercent)
                }
            }
            ServiceBcast.TYPE_PRESS_BACK -> { pressBackButton() }
            ServiceBcast.TYPE_PRESS_HOME -> { pressHomeButton() }
            ServiceBcast.TYPE_PRESS_RECENTS -> { pressRecentAppsButton() }
        }

        // Send ack
        val outIntent = Intent()
        outIntent.action =
            ACTION_FROM_SERVICE
        outIntent.putExtra("type", type)
        LocalBroadcastManager.getInstance(this).sendBroadcast(outIntent)
    }

    private fun simpleGestureDispatch(gesture: GestureDescription) {
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

        val ret = dispatchGesture(gesture, callback, null)
        Log.d(TAG, "dispatchGesture: $ret")
    }

    private fun doClick(x: Float, y: Float, isPercent: Boolean = false) {
        val gesture = if (isPercent) makeClickGesturePercent(x, y) else makeClickGesture(x, y)
        simpleGestureDispatch(gesture)
    }

    private fun makeClickGesturePercent(xPct: Float, yPct: Float): GestureDescription {
        if (xPct > 1.0 || yPct > 1.0) {
            throw IllegalArgumentException("Percent value must be from 0 to 1")
        }
        val wm =  getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val outPoint = Point()
        wm.defaultDisplay.getSize(outPoint)

        return makeClickGesture(outPoint.x * xPct, outPoint.y * yPct)
    }

    private fun makeClickGesture(x: Float, y: Float): GestureDescription {
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

    fun pressBackButton() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun pressHomeButton() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun pressRecentAppsButton() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    fun defer(runnable: Runnable, delayMillis: Long) {
        val handler = Handler()
        handler.postDelayed(runnable, delayMillis)
    }

    fun deferToMainThread(runnable: Runnable, delayMillis: Long) {
        val handler = Handler(mainLooper)
        handler.postDelayed(runnable, delayMillis)
    }

   fun doOcr() {

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

class ServiceBcast {
    companion object {
        const val TYPE_OCR_SCREENSHOT = "ocrScreenshot"
        const val TYPE_CLICK = "click"
        const val TYPE_PRESS_BACK = "pressBack"
        const val TYPE_PRESS_HOME = "pressHome"
        const val TYPE_PRESS_RECENTS = "pressRecents"
    }
}

class ServiceBcastClient(val context: Context) {
    private fun makeIntent(actionType: String): Intent {
        val outIntent = Intent()
        outIntent.action =
            ScriptAccessService.ACTION_TO_SERVICE
        outIntent.putExtra("type", actionType)
        return outIntent
    }

    fun sendDoOcr() {
        val outIntent = Intent()
        outIntent.action =
            ScriptAccessService.ACTION_TO_SERVICE
        outIntent.putExtra("type",
            ServiceBcast.TYPE_OCR_SCREENSHOT
        )
        LocalBroadcastManager.getInstance(context).sendBroadcast(outIntent)
    }

    fun sendClick(x: Float, y: Float, isPercent: Boolean = false) {
        val outIntent = makeIntent(ServiceBcast.TYPE_CLICK)
        outIntent.putExtra("x", x)
        outIntent.putExtra("y", y)
        outIntent.putExtra("isPercent", isPercent)
        LocalBroadcastManager.getInstance(context).sendBroadcast(outIntent)
    }

    fun pressBack() {
        val outIntent = makeIntent(ServiceBcast.TYPE_PRESS_BACK)
        LocalBroadcastManager.getInstance(context).sendBroadcast(outIntent)
    }

    fun pressHome() {
        val outIntent = makeIntent(ServiceBcast.TYPE_PRESS_HOME)
        LocalBroadcastManager.getInstance(context).sendBroadcast(outIntent)
    }

    fun pressRecentApps() {
        val outIntent = makeIntent(ServiceBcast.TYPE_PRESS_RECENTS)
        LocalBroadcastManager.getInstance(context).sendBroadcast(outIntent)
    }
}
