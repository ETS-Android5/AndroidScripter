package com.tsiemens.androidscripter.util

import android.graphics.Color
import android.util.Log

class ColorCompat(val value: Int) {
    companion object {
        fun rgb(r: Int, g: Int, b: Int): ColorCompat {
            val cc = ColorCompat(Color.rgb(r, g, b))
            Log.d("ColorCompat", "$r, $g, $b, value: ${cc.value}")
            return cc
        }
    }

    init {
        Log.d("ColorCompat", "init: $value")
    }

    /** Returns value from 0 to xFF */
    fun red(): Int = Color.red(value)
    /** Returns value from 0 to xFF */
    fun green(): Int = Color.green(value)
    /** Returns value from 0 to xFF */
    fun blue(): Int = Color.blue(value)

    override fun toString(): String {
        return String.format("%02x%02x%02x", red(), green(), blue())
    }
}
