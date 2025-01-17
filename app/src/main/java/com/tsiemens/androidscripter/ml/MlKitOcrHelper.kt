package com.tsiemens.androidscripter.ml

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizerOptions
import com.tsiemens.androidscripter.util.SimpleFuture
import java.lang.System.currentTimeMillis


class MlKitOcrHelper {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    companion object {
        private val TAG = MlKitOcrHelper::class.java.simpleName
    }

    fun extractText(bm: Bitmap): Text? {
        val startTime = currentTimeMillis()
        val image = InputImage.fromBitmap(bm, 0)
        val future = SimpleFuture<Text>()
        val res = recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val endTime = currentTimeMillis()
                Log.d(TAG, "extractText text: " + visionText.text)
                Log.d(TAG, "extractText took ${endTime - startTime} ms")
                future.set(visionText)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "extractText failed: " + e)
                future.set(null)
            }
        return future.get()
    }
}