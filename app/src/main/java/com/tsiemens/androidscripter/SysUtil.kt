package com.tsiemens.androidscripter

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.provider.Settings
import android.util.Log
import java.util.*

class SysUtil {
    companion object {
        val TAG = SysUtil::class.java.simpleName
    }
}

fun getUsageStatsForegroundActivityName(ctx: Context): String? {
    val mUsageStatsManager =
        ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val endTime = System.currentTimeMillis()
    val beginTime = endTime - 1000 * 60

    // result
    var topActivity: String? = null

    // We get usage stats for the last minute
    val stats = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, beginTime, endTime)

    // Sort the stats by the last time used
    if (stats != null) {
        val mySortedMap = TreeMap<Long, UsageStats>()
        for (usageStats in stats) {
            mySortedMap.put(usageStats.lastTimeUsed, usageStats)
        }
        if (!mySortedMap.isEmpty()) {
            topActivity = mySortedMap.get(mySortedMap.lastKey())!!.packageName
            Log.i(SysUtil.TAG, "topActivity: " + topActivity!!)
        }
    }
    return topActivity
}

fun tryGuaranteeUsageStatsAccess(ctx: Context) {
    if (getUsageStatsForegroundActivityName(ctx) == null) {
        val dialog = OkNoDialogFragment()
        dialog.setMessage("This app requires permission to access usage statistics. Allow?")
        dialog.setOnOk( DialogInterface.OnClickListener { _, _ ->
            ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        })
    }
}

fun launchAccessibilitySettings(ctx: Context) {
    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}
