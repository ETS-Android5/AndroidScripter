package com.tsiemens.androidscripter.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONException
import org.json.JSONObject

// TODO this should be id (which is generated, and implies the filename), URL, and name
open class ScriptFile(var name: String)

class SampleScriptFile(val filename: String, name: String): ScriptFile(name)

class UserScriptFile(val id: Long,
                     name: String,
                     var url: String): ScriptFile(name)

class ScriptFileStorage(val context: Context) {
    companion object {
        val TAG = ScriptFileStorage::class.java.simpleName

        val SCRIPT_ENTRY_PREFS = "user_scripts_prefs"
        val SCRIPT_ENTRIES_JSON = "script_entries"
    }

    class ScriptJson {
        companion object {
            val NAME = "name"
            val URL = "filename"
        }
    }

    private fun getSampleScriptFiles(): List<ScriptFile> {
        return arrayListOf(SampleScriptFile("example1.py", "Example 1"))
    }

    private fun prefs(): SharedPreferences {
        return context.getSharedPreferences(SCRIPT_ENTRY_PREFS, Context.MODE_PRIVATE)
    }

    fun getUserScriptFilesMap(): MutableMap<Long, UserScriptFile> {
        val scriptsJson = prefs().getString(SCRIPT_ENTRIES_JSON, null)
        val scripts = hashMapOf<Long, UserScriptFile>()

        if (scriptsJson != null) {
            try {
                val jsonObj = JSONObject(scriptsJson)
                jsonObj.keys().forEach { id ->
                    val scriptJsonObj = jsonObj.getJSONObject(id)
                    val scriptFile = UserScriptFile(id.toLong(),
                        scriptJsonObj.getString(ScriptJson.NAME),
                        scriptJsonObj.getString(ScriptJson.URL))
                    scripts[scriptFile.id] = (scriptFile)
                }
            } catch (e: JSONException) {
                Log.e(TAG, e.message)
            }
        }

        return scripts
    }

    fun putUserScriptFilesMap(scripts: Map<Long, UserScriptFile>) {
        val scriptsJsonObj = JSONObject()
        scripts.forEach { id, scriptFile ->
            val scriptJsonObj = JSONObject()
            scriptJsonObj.put(ScriptJson.NAME, scriptFile.name)
            scriptJsonObj.put(ScriptJson.URL, scriptFile.url)
            scriptsJsonObj.put(scriptFile.id.toString(), scriptJsonObj)
        }

        prefs().edit().putString(SCRIPT_ENTRIES_JSON, scriptsJsonObj.toString()).apply()
    }

    private fun getUserScriptFiles(): List<ScriptFile> {
        val scripts = arrayListOf<ScriptFile>()
        getUserScriptFilesMap().values.forEach {
            scripts.add(it)
        }
        return scripts
    }

    fun getScriptFiles(): List<ScriptFile> {
        return getSampleScriptFiles() + getUserScriptFiles()
    }

    fun addScriptFile(script: UserScriptFile) {
        val userScripts = getUserScriptFilesMap()
        userScripts[script.id] = script
        putUserScriptFilesMap(userScripts)
    }
}

