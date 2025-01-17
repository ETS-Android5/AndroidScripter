package com.tsiemens.androidscripter.script

import android.content.Context
import android.util.Log
import com.chaquo.python.PyException
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.lang.Exception

class Script(context: Context, modName: String, scriptCode: String) {
    val module: PyObject

    /**
     * @throws: PyException
     */
    init {
        ensurePythonStarted(context)
        val modBuilder = Python.getInstance().getModule("androidscripter.modbuilder")
        module = modBuilder.callAttr("compile_module", modName, scriptCode)
    }

    fun run(api: Api) {
        try {
            val pyApiMod = Python.getInstance().getModule("androidscripter.api")
            val pyApi = pyApiMod.callAttr("newApi", api)
            val pyRunner = Python.getInstance().getModule("androidscripter.runner")
            pyRunner.callAttr("run_script", pyApi, module)
            api.logInternal("SCRIPT EXITED")
        } catch (e: PyException) {
            if (e.cause?.javaClass == InterruptedException::class.java) {
                Log.e(TAG, "Script interrupted")
                api.logInternal("SCRIPT INTERRUPTED")
            } else {
                Log.e(TAG, e.message?:"")
                api.logInternal("SCRIPT EXCEPTION: ${e.message}",
                    level=Api.LogLevel.ERROR)
            }
        } catch (e: Exception) {
            Log.e(TAG, e.message?:"")
            api.logInternal("JAVA EXCEPTION: ${e.message}",
                level=Api.LogLevel.ERROR)
        }
    }

    companion object {
        val TAG = Api::class.java.simpleName

        fun ensurePythonStarted(ctx: Context) {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(ctx))
            }
        }
    }
}