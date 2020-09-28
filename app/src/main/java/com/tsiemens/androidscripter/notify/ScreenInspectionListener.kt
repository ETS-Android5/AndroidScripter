package com.tsiemens.androidscripter.notify

import com.tsiemens.androidscripter.util.ColorCompat


interface ScreenInspectionListener {
    fun onPointInspected(x: Float, y: Float, color: ColorCompat, isPercent: Boolean = true)
}