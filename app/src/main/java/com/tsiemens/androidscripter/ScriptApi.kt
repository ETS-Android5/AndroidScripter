package com.tsiemens.androidscripter

import android.content.Context
import java.lang.RuntimeException

class ScriptUtilException(msg: String): RuntimeException(msg)

@Suppress("UNUSED")
class ScriptApi(val ctx: Context) {
    fun foregroundActivityPackage(): String {
        val pack = getUsageStatsForegroundActivityName(ctx)
        if (pack == null) {
            throw ScriptUtilException("Could not get foreground activity")
        }
        return pack
    }
}