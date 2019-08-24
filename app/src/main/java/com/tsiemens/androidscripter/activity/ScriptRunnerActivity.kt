package com.tsiemens.androidscripter.activity

import android.graphics.Bitmap
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.tsiemens.androidscripter.*
import android.view.KeyEvent


class ScriptRunnerActivity : ScreenCaptureActivityBase(),
    ScriptApi.LogChangeListener {
    // Set here for testing only
    val overlayManager = OverlayManager(this)

    lateinit var tessHelper: TesseractHelper

    lateinit var screenCapClient: ScreenCaptureClient

    val dataHelper = DataUtilHelper(this)

    var scriptThread: Thread? = null
    var script: Script? = null
    var scriptApi = ScriptApi(this, this)

    lateinit var logTv: TextView

    companion object {
        private val TAG = ScriptRunnerActivity::class.java.simpleName
        private val PREPARE_TESS_PERMISSION_REQUEST_CODE =
            MIN_REQUEST_CODE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scriptrunner)

        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        logTv = findViewById(R.id.log_tv)

        val startButton = findViewById<Button>(R.id.start_button)
        startButton.setOnClickListener { onStartButton() }

        val stopButton = findViewById<Button>(R.id.stop_button)
        stopButton.setOnClickListener { onStopButton() }

        screenCapClient = object : ScreenCaptureClient() {
                override fun onScreenCap(bm: Bitmap) {
//                    val imgText = tessHelper.extractText(bm)
//                    lastImgText = imgText
//                    Log.i(TAG, "Image text: \"$imgText\"")
//
//                    val action = {
//                        if (imgText != null) {
//                            overlayManager.updateOcrText(imgText)
//                        }
//                        mImgView!!.setImageBitmap(bm)
//                    }
//
//                    if (Looper.myLooper() == Looper.getMainLooper()) {
//                       action()
//                    } else {
//                        runOnUiThread(action)
//                    }
                }
            }
        setScreenCaptureClient(screenCapClient)

        tessHelper = TesseractHelper(
            this,
            PREPARE_TESS_PERMISSION_REQUEST_CODE
        )
        tessHelper.prepareTesseract(true)
    }

    override fun onBackPressed() {
        Log.d(TAG, "onBackPressed")
        super.onBackPressed()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        Log.d(TAG, "onKeyDown: $keyCode")
        //replaces the default 'Back' button action
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            onBackPressed()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "onActivityResult $requestCode")
        if (requestCode == PREPARE_TESS_PERMISSION_REQUEST_CODE) {
            tessHelper.prepareTesseract(false)
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    fun tryStartOverlay() {
        if (overlayManager.permittedToShow() && !overlayManager.started()) {
            overlayManager.showOverlay()

            overlayManager.setOnCaptureTextButtonClick(View.OnClickListener {
                Log.i(TAG, "Capture in overlay pressed")
                // The next image cap will return a response in the client
                screenCapClient.requestScreenCap()
            })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayManager.destroy()
    }

    fun onStartButton() {
        // startProjection()
        tryStartOverlay()

        if (script == null) {
            val scriptCode = dataHelper.getAssetUtf8Data("example1.py")
            script = Script(this, "example1", scriptCode)

            scriptThread = Thread(
                Runnable {
                    script?.run(scriptApi)
                })
            scriptThread!!.start()
        }
    }

    fun onStopButton() {
        // stopProjection()

        scriptThread?.interrupt()
        scriptThread = null
        script = null
    }

    // From LogChangeListener
    override fun onLogChanged(newLog: ScriptApi.LogEntry) {
        Handler(mainLooper).post {
            logTv.append(newLog.toString() + "\n")
            // TODO scroll to bottom if already at bottom
        }
    }
}