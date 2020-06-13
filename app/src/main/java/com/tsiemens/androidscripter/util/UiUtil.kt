package com.tsiemens.androidscripter.util

import android.content.Context
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import android.view.Surface
import android.view.WindowManager

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

        fun getDisplaySize(context: Context): DisplayMetrics {
            val screenSize = DisplayMetrics()
            getDisplaySize(context, screenSize)
            return screenSize
        }

        fun getDisplaySize(context: Context, metrics: DisplayMetrics) {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = wm.defaultDisplay
            display.getRealMetrics(metrics)
        }
    }
}