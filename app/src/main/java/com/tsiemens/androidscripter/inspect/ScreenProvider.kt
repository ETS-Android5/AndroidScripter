package com.tsiemens.androidscripter.inspect

import android.graphics.Bitmap


interface ScreenProvider {
    fun getScreenCap(cropPadding: Boolean=true): Bitmap?
}