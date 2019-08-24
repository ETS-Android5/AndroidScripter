package com.tsiemens.androidscripter.storage

import android.content.Context

class ScriptFile(val filename: String,
                 var name: String,
                 val isPermenantAsset: Boolean) {
}

class ScriptFileStorage(val context: Context) {
    fun getScriptFiles(): List<ScriptFile> {
        return arrayListOf(ScriptFile("example1.py", "Example 1", true))
    }
}

