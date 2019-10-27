package com.tsiemens.androidscripter.util

import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Surface

class UiUtil {
    companion object {
        fun forceToMainThread(thing: ()->Unit) {
            val mainLooper = Looper.getMainLooper()
            if (mainLooper.thread != Thread.currentThread()) {
                Handler(mainLooper).post(thing)
            } else {
                thing()
            }
        }

        fun relativeDisplaySize(d: Display): Point {
            val screenSize = Point()
            d.getSize(screenSize)
            return when(d.rotation) {
                Surface.ROTATION_0, Surface.ROTATION_180 -> screenSize
                Surface.ROTATION_90, Surface.ROTATION_270 -> Point(screenSize.y, screenSize.x)
                else -> throw IllegalArgumentException("Invalid rotation: ${d.rotation}")
            }
        }
    }
}