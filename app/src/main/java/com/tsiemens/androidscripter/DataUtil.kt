package com.tsiemens.androidscripter

import android.content.Context
import android.util.Log
import java.nio.charset.Charset
import java.io.BufferedReader
import java.io.InputStreamReader


class DataUtilHelper(val ctx: Context) {
    companion object {
        val TAG = DataUtilHelper::class.java.simpleName
    }

    fun getAssetUtf8Data(filepath: String): String {
        val inStream = ctx.assets.open(filepath)
        val reader = BufferedReader(
            InputStreamReader(inStream, "UTF8")
        )

        reader.use { reader ->
            return reader.readText()
        }
    }

    /*
    private fun copyTessDataFiles(path: String) {
        try {
            val fileList = getAssets().list(path)

            for (fileName in fileList) {

                // open file within the assets folder
                // if it is not already there copy it to the sdcard
                val pathToDataFile = DATA_PATH + path + "/" + fileName
                if (!File(pathToDataFile).exists()) {

                    val `in` = getAssets().open("$path/$fileName")

                    val out = FileOutputStream(pathToDataFile)

                    // Transfer bytes from in to out
                    val buf = ByteArray(1024)
                    var len: Int

                    while ((len = `in`.read(buf)) > 0) {
                        out.write(buf, 0, len)
                    }
                    `in`.close()
                    out.close()

                    Log.d(TAG, "Copied " + fileName + "to tessdata")
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Unable to copy files to tessdata " + e.toString())
        }
    }
    */

}

