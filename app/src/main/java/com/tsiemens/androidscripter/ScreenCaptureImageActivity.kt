package com.tsiemens.androidscripter

import android.Manifest
import android.hardware.display.VirtualDisplay
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.content.Intent
import android.os.Looper
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.media.projection.MediaProjection
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Point
import android.media.Image
import android.media.ImageReader
import android.os.Environment
import android.os.Handler
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class ScreenCaptureImageActivity : Activity() {

    private var mProjectionManager: MediaProjectionManager? = null
    private var mProjection: MediaProjection? = null
    private var mImageReader: ImageReader? = null
    private var mHandler = Handler(Looper.getMainLooper())
    private var imagesProduced: Int = 0
    private var startTimeInMillis: Long = 0

    private var lastCapTs = 0L
    private val minCapGap = 1000

    private var savedBitmap : Bitmap? = null

    private var mImgView : ImageView? = null

    private var lastImgText: String? = null

    // Set here for testing only
    val overlayManager = OverlayManager(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_imgcap)

        // call for the projection manager
        mProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        mImgView = findViewById(R.id.bitmap_imgview) as ImageView

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

        prepareTesseract(false)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        Log.d(TAG, "onActivityResult $requestCode")
        if (requestCode == REQUEST_CODE) {
            val curTime = System.currentTimeMillis()
            if ((curTime - minCapGap) < lastCapTs){
                return
            }
            lastCapTs = curTime

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
                            image = mImageReader!!.acquireLatestImage()
                            if (image != null) {
                                val planes = image.getPlanes()
                                val imageBuffer = planes[0].buffer.rewind()

                                // Strides in bytes (bytes/pixel)
                                val pixStride = planes[0].pixelStride
                                val rowStride = planes[0].rowStride
                                val rowPaddingBytes = rowStride - (pixStride * width)

                                // create bitmap
                                bitmap = Bitmap.createBitmap(
                                    width + (rowPaddingBytes/pixStride), height, Bitmap.Config.ARGB_8888)
                                bitmap!!.copyPixelsFromBuffer(imageBuffer)
                                bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                                // write bitmap to a file
//                                fos = FileOutputStream("$filesDir/myscreen.png")

                                val imgText = extractText(bitmap)
                                lastImgText = imgText
                                Log.i(TAG, "Image text: \"$imgText\"")

                                savedBitmap?.recycle()
                                savedBitmap = bitmap

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

                            if (savedBitmap != bitmap) {
                                bitmap?.recycle()
                            }

                            if (image != null)
                                image.close()

                        }
                    }

                }, mHandler)
            }
        } else if (requestCode == PERMISSION_REQUEST_CODE) {
            prepareTesseract(true)
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun startProjection() {
        startActivityForResult(mProjectionManager!!.createScreenCaptureIntent(), REQUEST_CODE)
    }

    private fun stopProjection() {
        mHandler.post(Runnable { mProjection!!.stop() })
    }

    override fun onResume() {
        super.onResume()
//        if (savedBitmap != null) {
//            mImgView!!.setImageBitmap(savedBitmap)
//        }

        if (overlayManager.permittedToShow() && !overlayManager.started()) {
            overlayManager.showOverlay()

            overlayManager.setOnCaptureTextButtonClick(View.OnClickListener {
                val imgText = lastImgText
                if (imgText != null) {
                    overlayManager.updateOcrText(imgText)
                }
                if (savedBitmap != null) {
                    mImgView!!.setImageBitmap(savedBitmap!!.copy(savedBitmap!!.config, false))
                }
            })
        }
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

    /**
     * Prepare directory on external storage
     *
     * @param path
     * @throws Exception
     */
    private fun prepareDirectory(path: String) {

        val dir = File(path)
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.e(
                    TAG,
                    "ERROR: Creation of directory $path failed, check does Android Manifest have permission to write to external storage."
                )
            }
        } else {
            Log.i(TAG, "Created directory $path")
        }
    }


    private fun prepareTesseract(requirePermission: Boolean) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED) {
            if (requirePermission) {
                Log.e(TAG, "Could not get write permission")
                return
            }
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSION_REQUEST_CODE)
            return
        }

        try {
            prepareDirectory(DATA_PATH + TESSDATA)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        copyTessDataFiles(TESSDATA)
    }

    /**
     * Copy tessdata files (located on assets/tessdata) to destination directory
     *
     * @param path - name of directory with .traineddata files
     */
    private fun copyTessDataFiles(path: String) {
        try {
            val fileList = assets.list(path)

            for (fileName in fileList!!) {

                // open file within the assets folder
                // if it is not already there copy it to the sdcard
                val pathToDataFile = DATA_PATH + path + "/" + fileName
                if (!(File(pathToDataFile)).exists()) {

                    val inp = assets.open(path + "/" + fileName)
                    val out = FileOutputStream(pathToDataFile)

                    // Transfer bytes from in to out
                    val buf = ByteArray(1024)
                    var len: Int

                    len = inp.read(buf)
                    while (len > 0) {
                        out.write(buf, 0, len);
                        len = inp.read(buf)
                    }
                    inp.close()
                    out.close()

                    Log.d(TAG, "Copied " + fileName + "to tessdata");
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Unable to copy files to tessdata " + e.toString());
        }
    }

    fun extractText(bitmap : Bitmap) : String? {
        var tessBaseApi : TessBaseAPI? = null
        try {
            tessBaseApi = TessBaseAPI()
        } catch (e: Exception) {
            Log.e(TAG, e.message?: "")
            if (tessBaseApi == null) {
                Log.e(TAG, "TessBaseAPI is null. TessFactory not returning tess object.");
            }
            return null
        }

        tessBaseApi.init(DATA_PATH, lang);

//       //EXTRA SETTINGS
//        //For example if we only want to detect numbers
//        tessBaseApi.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "1234567890");
//
//        //blackList Example
//        tessBaseApi.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, "!@#$%^&*()_+=-qwertyuiop[]}{POIU" +
//                "YTRWQasdASDfghFGHjklJKLl;L:'\"\\|~`xcvXCVbnmBNM,./<>?");

        Log.d(TAG, "Training file loaded");
        tessBaseApi.setImage(bitmap)
        var extractedText : String? = null
        try {
            extractedText = tessBaseApi.getUTF8Text()
        } catch (e : Exception) {
            Log.e(TAG, "Error in recognizing text: ${e.message}")
        }
        tessBaseApi.end()
        return extractedText;
    }

    companion object {

        private val TAG = ScreenCaptureImageActivity::class.java.simpleName
        private val REQUEST_CODE = 100
        private val PERMISSION_REQUEST_CODE = 101


        private val lang = "eng"
        private val DATA_PATH = Environment.getExternalStorageDirectory().toString() + "/TesseractSample/";
        private val TESSDATA = "tessdata"
    }

}