package com.tsiemens.androidscripter

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
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.support.v4.content.LocalBroadcastManager
import android.util.DisplayMetrics
import android.media.projection.MediaProjection
import android.support.v4.content.ContextCompat.getSystemService
import android.media.projection.MediaProjectionManager
import android.media.MediaRecorder







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

class ScriptService2 : AccessibilityService() {
//    var lastNode : NodeHandle? = null
//    var lastWindowStateNode : NodeHandle? = null
    companion object {
        val TAG = ScriptService2::class.java.simpleName

        val ACTION_TO_SERVICE = "com.tsiemens.androidscripter.LBcastToScriptService"
        val ACTION_FROM_SERVICE = "com.tsiemens.androidscripter.LBcastFromScriptService"
    }

    var currWindowPackage : String? = null
    var currWindowActivity : String? = null

    private class ServiceBcastReceiver(val service: ScriptService2) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            service.onReceiveBcast(intent)
        }
    }
    private val bcastReceiver = ServiceBcastReceiver(this)

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected")

        LocalBroadcastManager.getInstance(this).registerReceiver(
            bcastReceiver, IntentFilter(ACTION_TO_SERVICE))
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // get the source node of the event
        var source = "null"
        var typeStr = "?"
        if (event.source?.text != null) {
            // nothing, just to remember
            source = event.source.text.toString()
        }
        if (event.source == null) {
            return
        }

//        lastNode?.dec()
//        lastNode = NodeHandle(event.source)

        when(event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                typeStr = "WINDOW_STATE_CHANGED"
                currWindowPackage = event.packageName?.toString()
                currWindowActivity = event.className?.toString()
//                lastWindowStateNode?.dec()
//                lastWindowStateNode = lastNode
//                lastNode?.inc()
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED ->
                typeStr = "VIEW_CLICKED"
        }

        Log.d(TAG, "eventType: %x (%s), text: %s".format(event.eventType, typeStr, source))
        Log.d(TAG, "package: ${event.packageName}, type: ${event.className}")

        if (event.source?.text != "DUMMY") {
            return
        }

        event.source?.apply {
            recycle()
        }
        return

        // Commented, but this does work
        // pressBackButton()

        defer(Runnable {
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

            val ret = dispatchGesture(tabGest, callback, null)
            Log.d(TAG, "dispatchGesture: $ret")

//            event.source?.apply {
//            lastWindowStateNode?.node.apply {



                // Use the event and node information to determine
                // what action to take

                // findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                // take action on behalf of the user
                // performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)

                // recycle the nodeInfo object
                // recycle()
//            }
        }, 1000)

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
            ServiceBcast.TYPE_RUN_SCRIPT -> {
                Thread(
                    Runnable {
                        val dataHelper = DataUtilHelper(this)
                        val scriptName = intent.getStringExtra("script")
                        val script = dataHelper.getAssetUtf8Data(scriptName)
                        Log.i(TAG, "Running script $scriptName")
                        ScriptDriver(this).runScript(script)
                    }).start()
            }
            ServiceBcast.TYPE_OCR_SCREENSHOT -> {
                doOcr()
            }
        }

        // Send ack
        val outIntent = Intent()
        outIntent.action = ACTION_FROM_SERVICE
        outIntent.putExtra("type", type)
        LocalBroadcastManager.getInstance(this).sendBroadcast(outIntent)
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

    fun pressBackButton() {
        performGlobalAction(GLOBAL_ACTION_BACK)
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
        val TYPE_RUN_SCRIPT = "runScript"
        val TYPE_OCR_SCREENSHOT = "ocrScreenshot"
    }
}

class ServiceBcastClient(val context: Context) {
    fun sendRunScript(scriptName: String) {
        val outIntent = Intent()
        outIntent.action = ScriptService2.ACTION_TO_SERVICE
        outIntent.putExtra("type", ServiceBcast.TYPE_RUN_SCRIPT)
        outIntent.putExtra("script", scriptName)
        LocalBroadcastManager.getInstance(context).sendBroadcast(outIntent)
    }

    fun sendDoOcr() {
        val outIntent = Intent()
        outIntent.action = ScriptService2.ACTION_TO_SERVICE
        outIntent.putExtra("type", ServiceBcast.TYPE_OCR_SCREENSHOT)
        LocalBroadcastManager.getInstance(context).sendBroadcast(outIntent)
    }
}

class RecorderHelper(val context: Context) {
//    val mMediaProjectionManager = MediaProjection()

    val mProjectionManager : MediaProjectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    var mMediaRecorder : MediaRecorder = MediaRecorder()

    fun otherstuff() {
        val metrics = DisplayMetrics()
//        getWindowManager().getDefaultDisplay().getMetrics(metrics)
//        mScreenDensity = metrics.densityDpi


    }

//    fun stuff() {
//        val TAG = "RecorderHelper"
//        Log.d(TAG, "startScreenRecord:sMuxer=$sMuxer")
//        synchronized(sSync) {
//            if (sMuxer == null) {
//                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
//                // get MediaProjection
//                val projection = mMediaProjectionManager.getMediaProjection(resultCode, intent)
//                if (projection != null) {
//                    val metrics = getResources().getDisplayMetrics()
//                    val density = metrics.densityDpi
//
//                    if (DEBUG) Log.v(TAG, "startRecording:")
//                    try {
//                        sMuxer = MediaMuxerWrapper(".mp4") // if you record audio only, ".m4a" is also OK.
//                        if (true) {
//                            // for screen capturing
//                            MediaScreenEncoder(
//                                sMuxer, mMediaEncoderListener,
//                                projection, metrics.widthPixels, metrics.heightPixels, density
//                            )
//                        }
//                        if (true) {
//                            // for audio capturing
//                            MediaAudioEncoder(sMuxer, mMediaEncoderListener)
//                        }
//                        sMuxer.prepare()
//                        sMuxer.startRecording()
//                    } catch (e: IOException) {
//                        Log.e(TAG, "startScreenRecord:", e)
//                    }
//
//                }
//            }
//        }
//    }
}