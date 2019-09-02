package com.tsiemens.androidscripter.util

import android.os.Handler
import android.os.Looper

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
    }
}