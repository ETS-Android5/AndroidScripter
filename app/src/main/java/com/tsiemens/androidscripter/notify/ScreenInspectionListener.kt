package com.tsiemens.androidscripter.notify

import android.graphics.Rect
import com.tsiemens.androidscripter.util.ColorCompat


interface ScreenInspectionListener {
    fun onPointInspected(x: Float, y: Float, color: ColorCompat, isPercent: Boolean = true)
    fun onAreaInspected(rect: Rect)
}