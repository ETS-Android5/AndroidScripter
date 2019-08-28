package com.tsiemens.androidscripter.storage

import android.util.Log
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class StorageUtil {
    companion object {
        val TAG = StorageUtil::class.java.simpleName

        /*
         * @throws IOException
         */
        fun copyInToOut(ins: InputStream, out: OutputStream) {
            // Transfer bytes from in to out
            val buf = ByteArray(1024)
            var len: Int

            len = ins.read(buf)
            while (len > 0) {
                out.write(buf, 0, len);
                len = ins.read(buf)
            }
        }

        /**
         * Prepare directory on external storage
         *
         * @param path
         * @throws Exception
         */
        fun prepareDirectory(path: String) {
            val dir = File(path)
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    Log.e(
                        TAG,
                        "ERROR: Creation of directory $path failed, check does Android Manifest have permission to write to external storage."
                    )
                }
            } else {
                Log.i(TAG, "Created directory $path")
            }
        }
    }
}
