package com.tsiemens.androidscripter

import android.app.Application
import android.util.Log
import org.opencv.android.OpenCVLoader

class Application: Application() {
    companion object {
        val TAG = com.tsiemens.androidscripter.Application::class.java.simpleName
    }

    override fun onCreate() {
        super.onCreate()

        // Libraries are loaded from https://github.com/chaoyangnz/opencv3-android-sdk-with-contrib
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "Cannot load OpenCV library")
        } else {
            Log.d(TAG, "Loaded opencv")
        }
    }
}