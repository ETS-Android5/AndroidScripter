package com.tsiemens.androidscripter.storage

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
    }
}
