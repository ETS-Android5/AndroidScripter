package com.tsiemens.androidscripter.activity

import android.annotation.SuppressLint
import android.hardware.display.VirtualDisplay
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.content.Intent
import android.os.Looper
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.media.projection.MediaProjection
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import com.tsiemens.androidscripter.service.ScreenCapNotificationService
import com.tsiemens.androidscripter.util.BitmapUtil
import com.tsiemens.androidscripter.util.NTObjPtr
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

class DisplayImage(val image: Image,
                   val imgId: Long,
                   val width: Int,
                   val height: Int,
                   val deviceSide1: Int,
                   val deviceSide2: Int,
                   val rotation: Int) {

    companion object {
        val MAX_OPEN_IMAGES = 3
        private val openImagesLock = ReentrantReadWriteLock()
        var openImages = 0

        val TAG = DisplayImage::class.java.simpleName
    }

    init {
        openImagesLock.write {
            openImages++
        }
    }

    fun toBitmap(): Bitmap {
        val planes = image.getPlanes()
        val imageBuffer = planes[0].buffer.rewind()

        // Strides in bytes (bytes/pixel)
        val pixStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPaddingBytes = rowStride - (pixStride * width)

        // create bitmap
        var bitmap = Bitmap.createBitmap(
            width + (rowPaddingBytes/pixStride), height, Bitmap.Config.ARGB_8888)
        bitmap!!.copyPixelsFromBuffer(imageBuffer)
        // Trim off padding
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, width,  height)
        bitmap = BitmapUtil.cropScreenshotFromSquareScreenshot(bitmap, deviceSide1, deviceSide2, rotation)
        Log.d(TAG, "toBitmap: w: ${bitmap.width}, h: ${bitmap.height}")

        return bitmap
    }

    fun close() {
        image.close()
        var openImagesNow: Int = 0
        openImagesLock.write {
            openImages--
            openImagesNow = openImages
        }
        Log.i("DisplayImage", "Closed image. Open now: $openImagesNow")
    }
}

abstract class ScreenCaptureActivityBase : AppCompatActivity(), ImageReader.OnImageAvailableListener {

    private var projectionManager: MediaProjectionManager? = null
    private var projection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var looperHandler = Handler(Looper.getMainLooper())

    var projecting = false

    // VirtualDisplay properties
    private var vDisplayHeight = 0
    private var vDisplayWidth = 0

    private var imgReaderHeight = 0
    private var imgReaderWidth = 0

    private var lastImgPtr = NTObjPtr.new<DisplayImage>()

    // This ties into the ref counting infrastructure, so we can debug if any instances
    // of this activity type are left undestroyed.
    private var selfCounter = NTObjPtr.new<ScreenCaptureActivityBase>()

    companion object {
        private val TAG = ScreenCaptureActivityBase::class.java.simpleName
        private val SCREENCAP_REQUEST_CODE = 100

        // For child class use
        val MIN_REQUEST_CODE = 101

        private var nextImageId: Long = 1
    }

    abstract inner class ScreenCaptureClient {
        var activity: ScreenCaptureActivityBase? = null
        private var requestPending = false

        protected abstract fun onScreenCap(bm: Bitmap, imgId: Long)

        open fun replyToReqeust(bm: Bitmap, imgId: Long) {
            requestPending = false
            onScreenCap(bm, imgId)
        }

        open fun isRequestPending(): Boolean {
            // By default, just use the flag
            return requestPending
        }

        fun requestScreenCap() {
            activity?.lastImgPtr?.trackFor { lastImg ->
                if (lastImg != null) {
                    replyToReqeust(lastImg.toBitmap(), lastImg.imgId)
                } else {
                    requestPending = true
                }
            }
        }
    }

    private var client: ScreenCaptureClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selfCounter.track(this) { _ -> }

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

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
        lastImgPtr.untrack()
        stopProjection()
        selfCounter.untrack()
    }

    @SuppressLint("WrongConstant")
    fun newImageReader(): ImageReader {
        // SuppressLint is for PixelFormat. The documentation says that this format can be any
        // constant from PixelFormat, but the implementation appears to not understand this.
        // We need a value of 1 here, which is RGBA_8888. Pretty much anything else seems to cause
        // a crash when getPlanes was called.
        // https://developer.android.com/reference/android/media/ImageReader#newInstance(int,%20int,%20int,%20int)
        return ImageReader.newInstance(imgReaderWidth, imgReaderHeight,
            PixelFormat.RGBA_8888,
            DisplayImage.MAX_OPEN_IMAGES)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "onActivityResult requestCode: $requestCode, data: $data")
        if (requestCode == SCREENCAP_REQUEST_CODE) {
            projection = if (data != null) projectionManager?.getMediaProjection(resultCode, data) else null

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

                // Take a square image, so that we don't scale down when the screen is in a different
                // rotation.
                imgReaderWidth = kotlin.math.max(size.x, size.y)
                imgReaderHeight = kotlin.math.max(size.x, size.y)

                Log.i(TAG, "Create virtual display")
                // For some reason, the format is supposed to be 1, even though no constant in ImageFormat
                // has this value. Unsure why at this point.
                // Demo was using JPEG, but this caused a crash when getPlanes was called.
                imageReader = newImageReader()
                projection!!.createVirtualDisplay(
                    "screencap",
                    imgReaderWidth,
                    imgReaderHeight,
                    density,
                    flags,
                    imageReader!!.getSurface(),
                    VirtualDisplayCallback(),
                    looperHandler
                )

                imageReader!!.setOnImageAvailableListener(this, looperHandler)
            } else {
                Toast.makeText(this, "Unable to start screen capture", Toast.LENGTH_LONG).show()
                stopService(Intent(this, ScreenCapNotificationService::class.java))
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    fun startProjection() {
        Log.i(TAG, "startProjection")

        val openImages = DisplayImage.openImages
        val supposedOpenImages = if (lastImgPtr.obj() == null) 0 else 1
        if (openImages != supposedOpenImages) {
            Toast.makeText(this,
                        "Warning: There are unexpected open screen images ($openImages). " +
                        "Expected $supposedOpenImages",
                            Toast.LENGTH_LONG).show()
        }

        projecting = true
        // This service is required after API 29 as a notification to the user that we're recording
        // the screen.
        startService(Intent(this, ScreenCapNotificationService::class.java))
        startActivityForResult(projectionManager!!.createScreenCaptureIntent(),
            SCREENCAP_REQUEST_CODE
        )
    }

    fun stopProjection() {
        Log.i(TAG, "stopProjection")
        projecting = false
        looperHandler.post {
            projection?.stop()
            projection = null
            stopService(Intent(this, ScreenCapNotificationService::class.java))
        }
    }

    // from OnImageAvailableListener
    override fun onImageAvailable(p0: ImageReader?) {
        Log.d(TAG, "onImageAvailable")
        val openImages = DisplayImage.openImages
        if (openImages >= DisplayImage.MAX_OPEN_IMAGES) {
            Log.w(TAG, "Open images $openImages at max.")
            return
        }
        val image: Image? = imageReader!!.acquireLatestImage()
        try {
            val clientRef = client

            if (image != null) {
                val rotation = windowManager.defaultDisplay.rotation
                val displayImg =
                    DisplayImage(image, nextImageId, imgReaderWidth, imgReaderHeight,
                        vDisplayHeight, vDisplayWidth, rotation)
                nextImageId++

                lastImgPtr.track(displayImg) { i -> i.close() }

                if (clientRef?.isRequestPending() == true) {
                    Log.d(TAG, "onImageAvailable: serving request")

                    // This is passed on, so don't recycle the bitmap
                    clientRef.replyToReqeust(displayImg.toBitmap(), displayImg.imgId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, Log.getStackTraceString(e))
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