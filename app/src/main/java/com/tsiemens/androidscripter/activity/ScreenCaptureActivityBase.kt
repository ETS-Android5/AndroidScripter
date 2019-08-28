package com.tsiemens.androidscripter.activity

import android.hardware.display.VirtualDisplay
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.content.Intent
import android.os.Looper
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.media.projection.MediaProjection
import android.content.Context
import android.graphics.Point
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.util.Log
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

class DisplayImage(val image: Image,
                   val width: Int,
                   val height: Int) {

    private val refLock = ReentrantReadWriteLock()
    private var ref = 1

    fun toBitmap(): Bitmap {
        val planes = image.getPlanes()
        val imageBuffer = planes[0].buffer.rewind()

        // Strides in bytes (bytes/pixel)
        val pixStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPaddingBytes = rowStride - (pixStride * width)

        // create bitmap
        val bitmap = Bitmap.createBitmap(
            width + (rowPaddingBytes/pixStride), height, Bitmap.Config.ARGB_8888)
        bitmap!!.copyPixelsFromBuffer(imageBuffer)
        // Trim off padding
        return Bitmap.createBitmap(bitmap, 0, 0, width,  height)
    }

    fun close() {
        image.close()
    }

    fun takeRef(): DisplayImage? {
        refLock.write {
            if (ref == 0) {
                return null
            }
            ref++
        }
        return this
    }

    fun releaseRef() {
        refLock.write {
            ref--
            if (ref == 0) {
                Log.d("DisplayImage", "Closing image")
                image.close()
            }
        }
    }
}

abstract class ScreenCaptureActivityBase : AppCompatActivity(), ImageReader.OnImageAvailableListener {

    private var projectionManager: MediaProjectionManager? = null
    private var projection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var looperHandler = Handler(Looper.getMainLooper())

    // VirtualDisplay properties
    private var vDisplayHeight = 0
    private var vDisplayWidth = 0

    private var lastImg: DisplayImage? = null

    companion object {
        private val TAG = ScreenCaptureActivityBase::class.java.simpleName
        private val SCREENCAP_REQUEST_CODE = 100

        // For child class use
        val MIN_REQUEST_CODE = 101
    }

    abstract inner class ScreenCaptureClient {
        var activity: ScreenCaptureActivityBase? = null
        private var requestPending = false

        protected abstract fun onScreenCap(bm: Bitmap)

        open fun replyToReqeust(bm: Bitmap) {
            requestPending = false
            onScreenCap(bm)
        }

        open fun isRequestPending(): Boolean {
            // By default, just use the flag
            return requestPending
        }

        fun requestScreenCap() {
            val lastImg = activity?.lastImg?.takeRef()
            try {
                if (lastImg != null) {
                    replyToReqeust(lastImg.toBitmap())
                } else {
                    requestPending = true
                }
            } finally {
                lastImg?.releaseRef()
            }
        }
    }

    private var client: ScreenCaptureClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // call for the projection manager
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // start capture handling thread
        object : Thread() {
            override fun run() {
                Looper.prepare()
                looperHandler = Handler()
                Looper.loop()
            }
        }.start()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "onActivityResult $requestCode")
        if (requestCode == SCREENCAP_REQUEST_CODE) {
            projection = projectionManager!!.getMediaProjection(resultCode, data!!)

            if (projection != null) {
                val metrics = resources.displayMetrics
                val density = metrics.densityDpi
                val flags =
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                val display = windowManager.defaultDisplay
                val size = Point()
                display.getSize(size)
                vDisplayWidth = size.x
                vDisplayHeight = size.y

                Log.i(TAG, "Create virtual display")
                // For some reason, the format is supposed to be 1, even though no constant in ImageFormat
                // has this value. Unsure why at this point.
                // Demo was using JPEG, but this caused a crash when getPlanes was called.
                imageReader = ImageReader.newInstance(vDisplayWidth, vDisplayHeight, 1, 3)
                projection!!.createVirtualDisplay(
                    "screencap",
                    vDisplayWidth,
                    vDisplayHeight,
                    density,
                    flags,
                    imageReader!!.getSurface(),
                    VirtualDisplayCallback(),
                    looperHandler
                )

                imageReader!!.setOnImageAvailableListener(this, looperHandler)
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    fun startProjection() {
        Log.i(TAG, "startProjection")
        startActivityForResult(projectionManager!!.createScreenCaptureIntent(),
            SCREENCAP_REQUEST_CODE
        )
    }

    fun stopProjection() {
        Log.i(TAG, "stopProjection")
        looperHandler.post { projection?.stop() }
    }

    // from OnImageAvailableListener
    override fun onImageAvailable(p0: ImageReader?) {
        Log.d(TAG, "onImageAvailable")
        // This MUST be released before we return
        val image: Image? = imageReader!!.acquireLatestImage()
        try {
            val clientRef = client

            if (image != null) {
                val displayImg =
                    DisplayImage(image, vDisplayWidth, vDisplayHeight)
                lastImg?.releaseRef()
                lastImg = displayImg

                if (clientRef?.isRequestPending() == true) {
                    Log.d(TAG, "onImageAvailable: serving request")

                    // This is passed on, so don't recycle the bitmap
                    clientRef.replyToReqeust(displayImg.toBitmap())
                }

            }
        } catch (e: Exception) {
            Log.e(TAG, Log.getStackTraceString(e))
        } finally {
//            image?.close()
        }
    }

    fun setScreenCaptureClient(client_: ScreenCaptureClient) {
        client = client_
        client_.activity = this
    }

    private inner class VirtualDisplayCallback : VirtualDisplay.Callback() {

        override fun onPaused() {
            super.onPaused()
            Log.e(TAG, "VirtualDisplayCallback: onPaused")
        }

        override fun onResumed() {
            super.onResumed()
            Log.e(TAG, "VirtualDisplayCallback: onResumed")
        }

        override fun onStopped() {
            super.onStopped()
            Log.e(TAG, "VirtualDisplayCallback: onStopped")
        }
    }
}