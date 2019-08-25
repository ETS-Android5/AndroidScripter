package com.tsiemens.androidscripter

import android.os.AsyncTask
import android.util.Log
import org.apache.commons.io.IOUtils
import java.lang.Exception
import java.net.URL

class RequestTask: AsyncTask<String, Unit, String>() {
    companion object {
        val TAG = RequestTask::class.java.simpleName
    }

    interface OnResponseListener {
        fun onResponse(resp: String?)
    }
    var listener: OnResponseListener? = null

    override fun doInBackground(vararg urls: String?): String? {
        try {
            Log.i(TAG, "Requesting ${urls[0]}")
            val url = URL(urls[0])
            val con = url.openConnection()
            val inStream = con.getInputStream()
//            val status = con.getHeaderField("Status")
//            Log.i(TAG, "Status: $status")
//            con.headerFields.forEach { k, l ->
//                Log.d(TAG, "header $k: $l")
//            }
            return IOUtils.toString(inStream, con.contentEncoding)
        } catch (e: Exception) {
            Log.e(TAG, "${e.javaClass}: ${e.message}")
        }
        return null
    }

    override fun onPostExecute(result: String?) {
        listener?.onResponse(result)
        super.onPostExecute(result)
    }
}