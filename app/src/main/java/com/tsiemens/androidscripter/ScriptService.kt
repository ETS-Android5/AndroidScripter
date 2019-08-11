package com.tsiemens.androidscripter

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.app.ActivityManager
import android.content.Context

class ScriptService : Service() {

    companion object {
        var running = false
        val TAG = ScriptService::class.java.simpleName
    }

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    inner class TestRunnable : Runnable {
        override fun run() {
            if (!ScriptService.running) {
                return
            }

            Log.d(TAG, getUsageStatsForegroundActivityName(this@ScriptService) ?: "no usage stats activity")
            // defer(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        ScriptService.running = true
        Log.i(TAG, "created")
        defer(TestRunnable(), 1000)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "destroyed")
        ScriptService.running = false
    }

    fun defer(runnable: Runnable, delayMillis: Long) {
        val handler = Handler()
        handler.postDelayed(runnable, delayMillis)
    }

    fun activityManager(): ActivityManager {
        return this.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }
}
