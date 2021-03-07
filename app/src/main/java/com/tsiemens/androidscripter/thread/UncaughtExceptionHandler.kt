package com.tsiemens.androidscripter.thread

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

class UncaughtException(val dateStr: String, val desc: String) {
    companion object {
        fun make(t: Throwable): UncaughtException {
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            val date = Date()
            val dateStr = formatter.format(date)

            val buf = ByteArrayOutputStream()
            val ps = PrintStream(buf, true, StandardCharsets.UTF_8.name())
            t.printStackTrace(ps)

            return UncaughtException(dateStr, buf.toString())
        }
    }
}

class UncaughtExceptionHandler(val context: Context): Thread.UncaughtExceptionHandler {
    companion object {
        val TAG = UncaughtExceptionHandler::class.java.simpleName

        val EXCEPTION_LOGGING_PREFS = "exception_logging_prefs"
        val EXCEPTION_HISTORY_JSON = "exception_history"
        val EXCEPTIONS_LIST = "exceptions"
        val EXCEPTION_DATE = "date"
        val EXCEPTION_DESC = "desc"

        val MAX_EXCEPTIONS = 10

        var globalUncaughtExceptionHandler: UncaughtExceptionHandler? = null

        fun getGlobalUncaughtExceptionHandler(context: Context): UncaughtExceptionHandler {
            if (globalUncaughtExceptionHandler == null) {
                globalUncaughtExceptionHandler = UncaughtExceptionHandler(context.applicationContext)
            }
            return globalUncaughtExceptionHandler!!
        }
    }

    override fun uncaughtException(p0: Thread?, p1: Throwable?) {
        Log.w(TAG, "Throwable: $p1")
        if (p1 != null) {
            val exceptions = getExceptionHistory()

            while (exceptions.size >= MAX_EXCEPTIONS) {
                exceptions.removeAt(exceptions.size - 1)
            }
            exceptions.add(0, UncaughtException.make(p1))

            putExceptionHistory(exceptions)
        }
    }

    private fun prefs(): SharedPreferences {
        return context.getSharedPreferences(EXCEPTION_LOGGING_PREFS, Context.MODE_PRIVATE)
    }

    fun getExceptionHistory(): MutableList<UncaughtException> {
        val exceptionsJson = prefs().getString(EXCEPTION_HISTORY_JSON, null)
        val exceptions = arrayListOf<UncaughtException>()

        if (exceptionsJson != null) {
            try {
                val jsonObj = JSONObject(exceptionsJson)
                val exceptionsListJson = jsonObj.getJSONArray(EXCEPTIONS_LIST)
                for (i in 0 until exceptionsListJson.length()) {
                    val exJson = exceptionsListJson.getJSONObject(i)
                    exceptions.add(UncaughtException(
                        exJson.getString(EXCEPTION_DATE),
                        exJson.getString(EXCEPTION_DESC)))
                }

            } catch (e: JSONException) {
                Log.e(TAG, e.message?:"")
            } catch (e: Exception) {
                Log.e(TAG, "Unknown error in getExceptionHistory: " + e.message)
            }
        }

        return exceptions
    }

    fun putExceptionHistory(exceptions: List<UncaughtException>) {
        val exceptionsJson = JSONObject()
        val exceptionsListJson = JSONArray()
        exceptions.forEachIndexed { i, ex ->
            val exceptionJson = JSONObject()
            exceptionJson.put(EXCEPTION_DATE, ex.dateStr)
            exceptionJson.put(EXCEPTION_DESC, ex.desc)
            exceptionsListJson.put(i, exceptionJson)
        }
        exceptionsJson.put(EXCEPTIONS_LIST, exceptionsListJson)

        prefs().edit().putString(EXCEPTION_HISTORY_JSON, exceptionsJson.toString()).apply()
    }

    fun clearExceptionHistory() {
        putExceptionHistory(arrayListOf())
    }
}