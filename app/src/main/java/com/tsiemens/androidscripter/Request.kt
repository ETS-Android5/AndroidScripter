package com.tsiemens.androidscripter

import android.os.AsyncTask
import android.util.Log
import org.apache.commons.io.IOUtils
import java.lang.Exception
import java.net.URL

class RequestResult(val respData: String?, val error: Exception?)

class RequestTask: AsyncTask<String, Unit, RequestResult>() {
    companion object {
        val TAG = RequestTask::class.java.simpleName
    }

    interface OnResponseListener {
        fun onResponse(resp: String)
        fun onError(e: Exception)
    }
    var listener: OnResponseListener? = null

    override fun doInBackground(vararg urls: String?): RequestResult {
        try {
            Log.i(TAG, "Requesting ${urls[0]}")
            val url = URL(urls[0])
            val con = url.openConnection()
            val inStream = con.getInputStream()
            return RequestResult(IOUtils.toString(inStream, con.contentEncoding), null)
        } catch (e: Exception) {
            Log.e(TAG, "${e.javaClass}: ${e.message}")
            return RequestResult(null, e)
        }
    }

    override fun onPostExecute(result: RequestResult) {
        if (result.respData != null) {
            listener?.onResponse(result.respData)
        } else if (result.error != null) {
            listener?.onError(result.error)
        }
        super.onPostExecute(result)
    }
}