package com.tsiemens.androidscripter.script

import android.graphics.Bitmap
import android.graphics.Point
import android.util.Log
import java.security.MessageDigest

data class ScreenSignature(val width: Int, val height: Int, val hash: String)

@Suppress("UNUSED")
class ScreenUtil {
    companion object {
        val TAG = ScreenUtil::class.java.simpleName

        fun makePoint(x: Int, y: Int): Point {
            return Point(x, y)
        }

        @Suppress("UNUSED")
        fun scaledCoords(coord: Point, refCoord: Point, bitmap: Bitmap): Point {
            return Point(
                coord.x * bitmap.getWidth() / refCoord.x,
                coord.y * bitmap.getHeight() / refCoord.y)
        }

        /**
         * Helper to get a subset of pixels from a region of a bitmap.
         * Provide the upper left and lower right points of a rectangle on the screen.
         *
         * Returned is a Sequence for pixel coordinates that are to be sampled.
         *
         * Uses a dumb sampling algorithm right now, based on a percentage. Defaults to
         * 1 sample per 20 pixels (5% sample).
         *
         * filter, if provided, should return true if a pixel is to be used.
         */
        @Suppress("UNUSED")
        fun getPixelSampleCoords(upperLeft: Point, lowerRight: Point,
                                 pixPerSample: Int = 20,
                                 filter: ((Point)->Boolean)? = null): Sequence<Point> {
            val ul = upperLeft
            val lr = lowerRight

            val width = lr.x - ul.x
            val height = lr.y - ul.y

            val pixels = width * height
            val nSamples = (pixels/pixPerSample)

            return sequence {
                var totalPxs = 0
                var pxsUsed = 0
                var x = 0
                var y = 0
                for (i in 0 until nSamples) {
                    totalPxs++
                    val point = Point(x + ul.x, y + ul.y)
                    if (filter == null || filter(point)) {
                        pxsUsed++
                        yield(point)
                    }
                    x = (x + pixPerSample) % width
                    y += ((x + pixPerSample) / width)
                }
                Log.d(TAG, "getPixelSampleCoords: yielded $pxsUsed/$totalPxs samples")
            }
        }

        fun combineSampleCoordSequences(vararg seqs: Sequence<Point>): Sequence<Point> {
            return sequence{
                for (seq in seqs) {
                    yieldAll(seq)
                }
            }
        }

        fun pixelExcludeFilterFromWinDimen(wd: Api.WinDimen,
                                           screenWidth: Int, screenHeight: Int): (Point) -> Boolean {
            return fun(px: Point): Boolean {
                return !wd.contains(px, screenWidth, screenHeight)
            }
        }

        fun getBitmapScreenSigFromSeq(bm: Bitmap, seq: Sequence<Point>): ScreenSignature {
            val md = MessageDigest.getInstance("MD5")
            for (sample in seq) {
                val colorVal = bm.getPixel(sample.x, sample.y)
                md.update((colorVal and 0xFF).toByte())
                md.update(((colorVal.shr(8)) and 0xFF).toByte())
                md.update(((colorVal.shr(16)) and 0xFF).toByte())
                md.update(((colorVal.shr(24)) and 0xFF).toByte())
            }

            val hex = md.digest().joinToString("") { "%02x".format(it) }
            return ScreenSignature(bm.width, bm.height, hex)
        }
    }
}
