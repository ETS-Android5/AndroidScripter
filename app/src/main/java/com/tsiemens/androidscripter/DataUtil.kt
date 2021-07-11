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
}

