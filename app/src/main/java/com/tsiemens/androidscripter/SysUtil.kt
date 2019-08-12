package com.tsiemens.androidscripter

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
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
        ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }
}

fun launchAccessibilitySettings(ctx: Context) {
    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}

fun maybeLaunchOverlaySettings(ctx: Context) {
    // TODO might want to see if I can get overlay working like so:
    // https://stackoverflow.com/questions/4481226/creating-a-system-overlay-window-always-on-top
    if (!Settings.canDrawOverlays(ctx)) {
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
    }
}