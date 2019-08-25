package com.tsiemens.androidscripter.activity

import android.graphics.Bitmap
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.tsiemens.androidscripter.*
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.tsiemens.androidscripter.dialog.ScriptEditDialog
import com.tsiemens.androidscripter.storage.*
import android.text.method.ScrollingMovementMethod
import android.support.v4.app.SupportActivity
import android.support.v4.app.SupportActivity.ExtraData
import android.support.v4.content.ContextCompat.getSystemService
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import android.widget.ScrollView


class ScriptRunnerActivity : ScreenCaptureActivityBase(),
    ScriptApi.LogChangeListener {
    // Set here for testing only
    val overlayManager = OverlayManager(this)

    lateinit var tessHelper: TesseractHelper

    lateinit var screenCapClient: ScreenCaptureClient

    val dataHelper = DataUtilHelper(this)

    private val scriptStorage = ScriptFileStorage(this)
    private lateinit var scriptFile: ScriptFile

    var scriptThread: Thread? = null
    var script: Script? = null
    var scriptCode: String? = null
    var scriptApi = ScriptApi(this, this)

    lateinit var scriptNameTv: TextView
    lateinit var logScrollView: ScrollView
    lateinit var logTv: TextView
    lateinit var startButton: Button

    companion object {
        val INTENT_EXTRA_SCRIPT_KEY = "script_key"

        private val TAG = ScriptRunnerActivity::class.java.simpleName
        private val PREPARE_TESS_PERMISSION_REQUEST_CODE =
            MIN_REQUEST_CODE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scriptrunner)

        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        val keyStr = intent.getStringExtra(INTENT_EXTRA_SCRIPT_KEY)
        require(keyStr != null) { "INTENT_EXTRA_SCRIPT_KEY must be set" }
        val scriptKey = ScriptKey.fromString(keyStr)
        scriptFile = scriptStorage.getScript(scriptKey)!!

        updateScriptDetailsViews()

        logScrollView = findViewById(R.id.log_scrollview)
        logScrollView.addOnLayoutChangeListener {
                view: View, i: Int, i1: Int, i2: Int, i3: Int, i4: Int, i5: Int, i6: Int, i7: Int ->
            scrollLogToBottom()
        }
        logTv = findViewById(R.id.log_tv)

        startButton = findViewById<Button>(R.id.start_button)
        startButton.setOnClickListener { onStartButton() }

        val stopButton = findViewById<Button>(R.id.stop_button)
        stopButton.setOnClickListener { onStopButton() }

        if (scriptKey.type == ScriptType.user) {
            startButton.isEnabled = false
            loadUserScript()
        }

        screenCapClient = object : ScreenCaptureClient() {
                override fun onScreenCap(bm: Bitmap) {
//                    val imgText = tessHelper.extractText(bm)
//                    lastImgText = imgText
//                    Log.i(TAG, "Image text: \"$imgText\"")
//
//                    val action = {
//                        if (imgText != null) {
//                            overlayManager.updateOcrText(imgText)
//                        }
//                        mImgView!!.setImageBitmap(bm)
//                    }
//
//                    if (Looper.myLooper() == Looper.getMainLooper()) {
//                       action()
//                    } else {
//                        runOnUiThread(action)
//                    }
                }
            }
        setScreenCaptureClient(screenCapClient)

        tessHelper = TesseractHelper(
            this,
            PREPARE_TESS_PERMISSION_REQUEST_CODE
        )
        tessHelper.prepareTesseract(true)
    }

    private fun updateScriptDetailsViews() {
        scriptNameTv = findViewById(R.id.script_name_tv)
        scriptNameTv.text = scriptFile.name
    }

    private fun loadUserScript() {
        val reqTask = RequestTask()
        reqTask.listener = object : RequestTask.OnResponseListener {
            override fun onResponse(resp: String?) {
                if (resp != null) {
                    scriptCode = resp
                    script = null
                    startButton.isEnabled = true
                    Toast.makeText(this@ScriptRunnerActivity,
                        "Script loaded", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ScriptRunnerActivity,
                        "Error getting script", Toast.LENGTH_SHORT).show()
                }
            }
        }
        reqTask.execute((scriptFile as UserScriptFile).url)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "onActivityResult $requestCode")
        if (requestCode == PREPARE_TESS_PERMISSION_REQUEST_CODE) {
            tessHelper.prepareTesseract(false)
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    fun tryStartOverlay() {
        if (overlayManager.permittedToShow() && !overlayManager.started()) {
            overlayManager.showOverlay()

            overlayManager.setOnCaptureTextButtonClick(View.OnClickListener {
                Log.i(TAG, "Capture in overlay pressed")
                // The next image cap will return a response in the client
                screenCapClient.requestScreenCap()
            })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayManager.destroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.script_runner_menu, menu)
        if (scriptFile.key.type == ScriptType.sample) {
            menu.findItem(R.id.action_edit).isEnabled = false
            menu.findItem(R.id.action_refresh).isEnabled = false
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_edit -> { doEditScriptDetails(); true }
            R.id.action_refresh -> {
                if (scriptFile.key.type == ScriptType.user) {
                    loadUserScript()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun doEditScriptDetails() {
        if (scriptFile.key.type != ScriptType.user) {
            return
        }
        val userScript = scriptFile as UserScriptFile

        val dialog = ScriptEditDialog()
        dialog.setInitialVals(userScript.name, userScript.url)

        dialog.onOkListener = object : ScriptEditDialog.OnOkListener {
            override fun onOk(name: String, url: String) {
                userScript.name = name
                userScript.url = url
                scriptStorage.putUserScriptFile(userScript)
                updateScriptDetailsViews()
            }
        }
        dialog.show(supportFragmentManager, "Edit script dialog")
    }

    fun onStartButton() {
        // startProjection()
        tryStartOverlay()

        if (scriptCode == null && scriptFile.key.type == ScriptType.sample) {
            scriptCode = dataHelper.getAssetUtf8Data((scriptFile as SampleScriptFile).filename)
        }

        if (script == null && scriptCode != null) {
            script = Script(this, scriptFile.key.toString(), scriptCode!!)

            scriptThread = Thread(
                Runnable {
                    script?.run(scriptApi)
                })
            scriptThread!!.start()
        }
    }

    fun onStopButton() {
        // stopProjection()

        scriptThread?.interrupt()
        scriptThread = null
        script = null
    }

    private fun scrollLogToBottom() {
        logScrollView.apply {
            val lastChild = getChildAt(childCount - 1)
            val bottom = lastChild.bottom + paddingBottom
            val delta = bottom - (scrollY+ height)
            smoothScrollBy(0, delta)
        }
    }

    // From LogChangeListener
    override fun onLogChanged(newLog: ScriptApi.LogEntry) {
        Handler(mainLooper).post {
            logTv.append(newLog.toString() + "\n")
        }
    }
}