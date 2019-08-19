package com.tsiemens.androidscripter

import android.Manifest
import android.hardware.display.VirtualDisplay
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.content.Intent
import android.os.Bundle
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Point
import android.media.Image
import android.media.ImageReader
import android.os.Environment
import android.support.v4.content.ContextCompat
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class ScreenCaptureImageActivity : ScreenCaptureActivityBase() {
    private var lastCapTs = 0L
    private val minCapGap = 1000

    private var savedBitmap : Bitmap? = null

    private var mImgView : ImageView? = null

    private var lastImgText: String? = null

    // Set here for testing only
    val overlayManager = OverlayManager(this)

    lateinit var tessHelper: TesseractHelper

    lateinit var screenCapClient: ScreenCaptureClient

    companion object {
        private val TAG = ScreenCaptureImageActivity::class.java.simpleName
        private val PREPARE_TESS_PERMISSION_REQUEST_CODE = MIN_REQUEST_CODE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_imgcap)

        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        mImgView = findViewById<ImageView>(R.id.bitmap_imgview)

        // start projection
        val startButton = findViewById<Button>(R.id.startButton)
        startButton.setOnClickListener { startProjection() }

        // stop projection
        val stopButton = findViewById<Button>(R.id.stopButton)
        stopButton.setOnClickListener { stopProjection() }

        screenCapClient = object : ScreenCaptureClient() {
                override fun onScreenCap(bm: Bitmap) {
                    val imgText = tessHelper.extractText(bm)
                    lastImgText = imgText
                    Log.i(TAG, "Image text: \"$imgText\"")

                    runOnUiThread {
                        if (imgText != null) {
                            overlayManager.updateOcrText(imgText)
                        }
                        mImgView!!.setImageBitmap(bm)
                        // TODO can recycle now?
                    }
                }
            }
        setScreenCaptureClient(screenCapClient)

        tessHelper = TesseractHelper(this, PREPARE_TESS_PERMISSION_REQUEST_CODE)
        tessHelper.prepareTesseract(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "onActivityResult $requestCode")
        if (requestCode == PREPARE_TESS_PERMISSION_REQUEST_CODE) {
            tessHelper.prepareTesseract(false)
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onResume() {
        super.onResume()
//        if (savedBitmap != null) {
//            mImgView!!.setImageBitmap(savedBitmap)
//        }

        if (overlayManager.permittedToShow() && !overlayManager.started()) {
            overlayManager.showOverlay()

            overlayManager.setOnCaptureTextButtonClick(View.OnClickListener {
                Log.i(TAG, "Capture in overlay pressed")
                // The next image cap will return a response in the client
                screenCapClient.requestPending = true
            })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayManager.destroy()
    }
}