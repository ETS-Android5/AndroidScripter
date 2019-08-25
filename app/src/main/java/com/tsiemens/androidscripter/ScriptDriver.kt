package com.tsiemens.androidscripter

import android.content.Context
import android.util.Log
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.tsiemens.androidscripter.activity.MainActivity

@Deprecated("Use Script.run instead")
class ScriptDriver(val ctx: Context) {
    fun runScript(scriptCode: String) {
        try {
            val modBuilder = Python.getInstance().getModule("androidscripter.modbuilder")
            val pyApiMod = Python.getInstance().getModule("androidscripter.api")
            val example1Mod = modBuilder.callAttr("compile_module", "example1", scriptCode)
            example1Mod.callAttr("run", pyApiMod.callAttr("newApi", ctx))
        } catch (e: PyException) {
            Log.e(MainActivity.TAG, "$e")
        }
    }
}