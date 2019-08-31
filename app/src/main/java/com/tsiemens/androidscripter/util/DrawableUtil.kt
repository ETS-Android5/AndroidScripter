package com.tsiemens.androidscripter.util

import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable

class DrawableUtil {
    companion object {
        fun convertDrawableToGrayScale(drawable: Drawable?): Drawable? {
            if (drawable == null)
                return null

            val res = drawable.mutate()
            res.setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_IN)
            return res
        }
    }
}
