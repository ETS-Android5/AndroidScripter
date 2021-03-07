package com.tsiemens.androidscripter.storage

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.util.Log
import com.tsiemens.androidscripter.TesseractHelper
import com.tsiemens.androidscripter.storage.StorageUtil.Companion.copyInToOut
import com.tsiemens.androidscripter.storage.StorageUtil.Companion.prepareDirectory
import org.apache.commons.io.IOUtils
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.lang.IllegalArgumentException
import java.lang.Long.max
import java.lang.Long.remainderUnsigned

enum class ScriptType {
    sample,
    user;

    companion object {
        fun fromString(str: String): ScriptType {
            ScriptType.values().forEach {
                if (it.toString() == str) {
                    return it
                }
            }
            throw IllegalArgumentException("$str is not a ScriptType")
        }
    }
}

class ScriptKey(val type: ScriptType, val index: Long) {
    override fun toString(): String {
        return "${type}_$index"
    }

    companion object {
        fun fromString(str: String): ScriptKey {
            val parts = str.split("_")
            require(parts.size == 2) { "Parts ${parts.size} != 2" }
            val type = ScriptType.fromString(parts[0])
            val index = parts[1].toLong()

            return ScriptKey(type, index)
        }
    }
}

open class ScriptFile(val key: ScriptKey, var name: String)

class SampleScriptFile(index: Long, val filename: String, name: String):
    ScriptFile(ScriptKey(ScriptType.sample, index), name)

class UserScriptFile(index: Long,
                     name: String,
                     var url: String): ScriptFile(ScriptKey(ScriptType.user, index), name) {
    fun storageFilename(): String {
        return key.index.toString() + ".py"
    }
}

class ScriptFileStorage(val context: Context) {
    companion object {
        val TAG = ScriptFileStorage::class.java.simpleName

        val SCRIPT_ENTRY_PREFS = "user_scripts_prefs"
        val SCRIPT_ENTRIES_JSON = "script_entries"

        private val CODE_DIRECTORY = Environment.getExternalStorageDirectory().toString() + "/AndroidScripterUserScripts/"
    }

    class ScriptJson {
        companion object {
            val NAME = "name"
            val URL = "filename"
        }
    }

    init {
        prepareDirectory(CODE_DIRECTORY)
    }

    private fun getSampleScriptFilesMap(): MutableMap<Long, SampleScriptFile> {
        val map = hashMapOf<Long, SampleScriptFile>()
        map[1L] = SampleScriptFile(1L, "example1.py", "Example 1")
        return map
    }

    private fun getSampleScriptFiles(): List<ScriptFile> {
        val scripts = arrayListOf<ScriptFile>()
        getSampleScriptFilesMap().values.forEach {
            scripts.add(it)
        }
        return scripts
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
                    scripts[scriptFile.key.index] = (scriptFile)
                }
            } catch (e: JSONException) {
                Log.e(TAG, e.message?:"")
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
            scriptsJsonObj.put(scriptFile.key.index.toString(), scriptJsonObj)
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
        return getUserScriptFiles() + getSampleScriptFiles()
    }

    fun putUserScriptFile(script: UserScriptFile) {
        val userScripts = getUserScriptFilesMap()
        userScripts[script.key.index] = script
        putUserScriptFilesMap(userScripts)
    }

    fun nextAvailableIndex(type: ScriptType, scripts: Collection<ScriptFile>): Long {
        var highestIndex = 0L
        scripts.forEach {
            if (it.key.type == type) {
                highestIndex = max(highestIndex, it.key.index)
            }
        }
        return highestIndex + 1
    }

    fun getScript(key: ScriptKey): ScriptFile? {
        return when (key.type) {
            ScriptType.sample -> getSampleScriptFilesMap()[key.index]
            ScriptType.user -> getUserScriptFilesMap()[key.index]
        }
    }

    fun putUserScriptCode(script: UserScriptFile, code: String) {
        val pathToCodeFile = CODE_DIRECTORY + "/" + script.storageFilename()
        val out = FileOutputStream(pathToCodeFile)
        val ins = StringReader(code)
        IOUtils.copy(ins, out, "utf-8")
        out.close()
        Log.i(TAG, "Saved code to $pathToCodeFile")
    }

    fun getUserScriptCode(script: UserScriptFile): String? {
        val pathToCodeFile = CODE_DIRECTORY + "/" + script.storageFilename()
        if (File(pathToCodeFile).exists()) {
            val ins = FileInputStream(pathToCodeFile)
            val out = StringWriter()
            IOUtils.copy(ins, out, "utf-8")
            ins.close()
            return out.toString()
        }
        return null
    }

    fun deleteUserScript(script: UserScriptFile) {
        val pathToCodeFile = CODE_DIRECTORY + "/" + script.storageFilename()
        val file = File(pathToCodeFile)
        if (file.exists()) {
            file.delete()
        }
        val userScripts = getUserScriptFilesMap()
        userScripts.remove(script.key.index)
        putUserScriptFilesMap(userScripts)
    }
}

