package com.tsiemens.androidscripter

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Environment
import android.support.v4.content.ContextCompat
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import com.tsiemens.androidscripter.storage.StorageUtil
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class TesseractHelper(val activity: Activity, val permissionRequestCode: Int) {
    var prepared = false

    var tessBaseApi : TessBaseAPI? = null

    companion object {
        private val TAG = TesseractHelper::class.java.simpleName

        private val lang = "eng"
        private val DATA_PATH = Environment.getExternalStorageDirectory().toString() + "/TesseractSample/";
        private val TESSDATA = "tessdata"
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

    /**
     * Copy tessdata files (located on assets/tessdata) to destination directory
     *
     * @param path - name of directory with .traineddata files
     */
    private fun copyTessDataFiles(path: String) {
        try {
            val fileList = activity.assets.list(path)

            for (fileName in fileList!!) {

                // open file within the assets folder
                // if it is not already there copy it to the sdcard
                val pathToDataFile = DATA_PATH + path + "/" + fileName
                if (!(File(pathToDataFile)).exists()) {

                    val inp = activity.assets.open(path + "/" + fileName)
                    val out = FileOutputStream(pathToDataFile)

                    StorageUtil.copyInToOut(inp, out)
                    inp.close()
                    out.close()

                    Log.d(TAG, "Copied " + fileName + "to tessdata");
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Unable to copy files to tessdata " + e.toString());
        }
    }

    fun prepareTesseract(mayRequestPermission: Boolean) {
        if (prepared) {
            return
        }

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED) {
            if (!mayRequestPermission) {
                Log.e(TAG, "Could not get write permission")
                return
            }
            activity.requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), permissionRequestCode)
            return
        }

        try {
            prepareDirectory(DATA_PATH + TESSDATA)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        copyTessDataFiles(TESSDATA)

        try {
            tessBaseApi = TessBaseAPI()
        } catch (e: Exception) {
            Log.e(TAG, e.message?: "")
            Log.e(TAG, "TessBaseAPI is null. TessFactory not returning tess object.")
            return
        }

        tessBaseApi!!.init(DATA_PATH, lang)
        prepared = true
    }

    fun extractText(bitmap : Bitmap) : String? {
        if (!prepared) {
            Log.e(TAG, "extractText: Not prepared yet")
            return null
        }

//       //EXTRA SETTINGS
//        //For example if we only want to detect numbers
//        tessBaseApi.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "1234567890");
//
//        //blackList Example
//        tessBaseApi.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, "!@#$%^&*()_+=-qwertyuiop[]}{POIU" +
//                "YTRWQasdASDfghFGHjklJKLl;L:'\"\\|~`xcvXCVbnmBNM,./<>?");

        Log.d(TAG, "Training file loaded");
        tessBaseApi!!.setImage(bitmap)
        var extractedText : String? = null
        try {
            extractedText = tessBaseApi!!.getUTF8Text()
        } catch (e : Exception) {
            Log.e(TAG, "Error in recognizing text: ${e.message}")
        }
        tessBaseApi!!.clear()
//        tessBaseApi!!.end()
        return extractedText;
    }
}