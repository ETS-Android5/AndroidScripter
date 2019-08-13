package com.tsiemens.androidscripter

import android.hardware.display.VirtualDisplay
import android.support.v4.app.ActivityCompat.startActivityForResult
import android.graphics.Bitmap.CompressFormat
import android.graphics.Bitmap
import android.media.Image.Plane
import android.media.ImageReader.OnImageAvailableListener
import android.graphics.ImageFormat
import android.R.attr.y
import android.R.attr.x
import android.view.Display
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.content.Intent
import android.os.Looper
import android.content.Context.MEDIA_PROJECTION_SERVICE
import android.support.v4.content.ContextCompat.getSystemService
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.media.projection.MediaProjection
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Point
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.Button
import java.io.FileOutputStream
import java.io.IOException


class ScreenCaptureImageActivity : Activity() {

    private var mProjectionManager: MediaProjectionManager? = null
    private var mProjection: MediaProjection? = null
    private var mImageReader: ImageReader? = null
    private var mHandler = Handler(Looper.getMainLooper())
    private var imagesProduced: Int = 0
    private var startTimeInMillis: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_imgcap)

        // call for the projection manager
        mProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // start projection
        val startButton = findViewById(R.id.startButton) as Button
        startButton.setOnClickListener(object : View.OnClickListener {

            override fun onClick(v: View) {
                startProjection()
            }
        })

        // stop projection
        val stopButton = findViewById(R.id.stopButton) as Button
        stopButton.setOnClickListener(object : View.OnClickListener {

            override fun onClick(v: View) {
                stopProjection()
            }
        })

        // start capture handling thread
        object : Thread() {
            override fun run() {
                Looper.prepare()
                mHandler = Handler()
                Looper.loop()
            }
        }.start()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (requestCode == REQUEST_CODE) {
            // for statistics -- init
            imagesProduced = 0
            startTimeInMillis = System.currentTimeMillis()

            mProjection = mProjectionManager!!.getMediaProjection(resultCode, data)

            if (mProjection != null) {
                val metrics = resources.displayMetrics
                val density = metrics.densityDpi
                val flags =
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                val display = windowManager.defaultDisplay
                val size = Point()
                display.getSize(size)
                val width = size.x
                val height = size.y

                Log.i(TAG, "Create virtual display")
                // For some reason, the format is supposed to be 1, even though no constant in ImageFormat
                // has this value. Unsure why at this point.
                // Demo was using JPEG, but this caused a crash when getPlanes was called.
                mImageReader = ImageReader.newInstance(width, height, 1, 2)
                mProjection!!.createVirtualDisplay(
                    "screencap",
                    width,
                    height,
                    density,
                    flags,
                    mImageReader!!.getSurface(),
                    VirtualDisplayCallback(),
                    mHandler
                )
                mImageReader!!.setOnImageAvailableListener(object : ImageReader.OnImageAvailableListener {

                    override fun onImageAvailable(reader: ImageReader) {
                        var image: Image? = null
                        var fos: FileOutputStream? = null
                        var bitmap: Bitmap? = null

                        Log.d(TAG, "onImageAvailable")
                        try {
                            Log.d(TAG, "1")
                            image = mImageReader!!.acquireLatestImage()
                            Log.d(TAG, "2")
                            if (image != null) {
                                Log.d(TAG, "3")
                                val planes = image.getPlanes()
                                Log.d(TAG, "4")
                                val imageBuffer = planes[0].getBuffer().rewind()

                                // create bitmap
                                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                bitmap!!.copyPixelsFromBuffer(imageBuffer)
                                Log.d(TAG, "5")
                                // write bitmap to a file
//                                fos = FileOutputStream("$filesDir/myscreen.png")

                                /**
                                 * uncomment this if you want either PNG or JPEG output
                                 */
//                                bitmap.compress(CompressFormat.JPEG, 100, fos)
                                //bitmap.compress(CompressFormat.PNG, 100, fos);
                                val color = bitmap.getPixel(100, 100)
                                Log.d(TAG, "pixel at 100,100: (ARGB) ${Color.alpha(color)}, ${Color.red(color)}, ${Color.green(color)}, ${Color.blue(color)}")
                                // for statistics
                                imagesProduced++
                                val now = System.currentTimeMillis()
                                val sampleTime = now - startTimeInMillis
                                Log.e(
                                    TAG,
                                    "produced images at rate: " + imagesProduced / (sampleTime / 1000.0f) + " per sec"
                                )
                            }

                        } catch (e: Exception) {
                            Log.e(TAG, Log.getStackTraceString(e))
//                            e.printStackTrace()
                        } finally {
                            if (fos != null) {
                                try {
                                    fos.close()
                                } catch (ioe: IOException) {
                                    ioe.printStackTrace()
                                }

                            }

                            bitmap?.recycle()

                            if (image != null)
                                image.close()

                        }
                    }

                }, mHandler)
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun startProjection() {
        startActivityForResult(mProjectionManager!!.createScreenCaptureIntent(), REQUEST_CODE)
    }

    private fun stopProjection() {
        mHandler.post(Runnable { mProjection!!.stop() })
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

    companion object {

        private val TAG = ScreenCaptureImageActivity::class.java.simpleName
        private val REQUEST_CODE = 100
    }

}