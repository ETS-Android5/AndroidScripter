package com.tsiemens.androidscripter.activity

import android.graphics.Bitmap
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.AppCompatImageButton
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.View
import com.tsiemens.androidscripter.*
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import com.chaquo.python.PyException
import com.tsiemens.androidscripter.dialog.ScriptEditDialog
import com.tsiemens.androidscripter.storage.*
import com.tsiemens.androidscripter.widget.ScriptController
import com.tsiemens.androidscripter.widget.ScriptControllerUIHelper
import com.tsiemens.androidscripter.widget.ScriptControllerUIHelperColl
import com.tsiemens.androidscripter.widget.ScriptState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.ReentrantLock

class ScriptRunnerActivity : ScreenCaptureActivityBase(),
    ScriptApi.LogChangeListener, ScriptApi.ScreenProvider {

    val overlayManager = OverlayManager(this)

    lateinit var tessHelper: TesseractHelper

    lateinit var screenCapClient: ScreenCaptureClient

    val dataHelper = DataUtilHelper(this)

    private val scriptStorage = ScriptFileStorage(this)
    private lateinit var scriptFile: ScriptFile

    var scriptThread: Thread? = null
    var script: Script? = null
    var scriptCode: String? = null
    var scriptApi = ScriptApi(this, this, this)

    lateinit var scriptNameTv: TextView
    lateinit var logTv: TextView
    lateinit var showOverlayCheck: CheckBox

    var lastScreencap: Bitmap? = null
    val screenCapLatchLock = ReentrantLock()
    var screenCapLatch: CountDownLatch? = null

    var overlayScriptControllerUIHelper: ScriptControllerUIHelper? = null
    val scriptUIControllers = ScriptControllerUIHelperColl()
    lateinit var scriptController: ScriptController

    var restartingScript = false

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

        if (scriptFile.key.type == ScriptType.sample) {
            scriptCode = dataHelper.getAssetUtf8Data((scriptFile as SampleScriptFile).filename)
        }

        updateScriptDetailsViews()

        logTv = findViewById(R.id.log_tv)

        val startPauseButton = findViewById<AppCompatImageButton>(R.id.start_pause_button)

        val restartButton = findViewById<AppCompatImageButton>(R.id.restart_button)

        val stopButton = findViewById<AppCompatImageButton>(R.id.stop_button)

        showOverlayCheck = findViewById(R.id.show_overlay_checkbox)
        showOverlayCheck.isChecked = false
        showOverlayCheck.setOnCheckedChangeListener { _, checked ->
            enableOverlay(checked)
        }

        overlayManager.onDestroyListener = {
            showOverlayCheck.isChecked = false
            doPreDestroyOverlayCleanup()
        }

        if (scriptKey.type == ScriptType.user) {
            loadUserScript()
        }

        scriptController = object : ScriptController {
            override fun onPausePressed() {
                this@ScriptRunnerActivity.onPauseButton()
            }

            override fun onRestartPressed() {
                this@ScriptRunnerActivity.onRestartButton()
            }

            override fun getScriptState(): ScriptState {
                if (scriptThread == null) {
                    return ScriptState.stopped
                }
                return if (scriptApi.paused.get()) ScriptState.paused else ScriptState.running
            }

            override fun onStartPressed() {
                this@ScriptRunnerActivity.onStartButton()
            }

            override fun onStopPressed() {
                this@ScriptRunnerActivity.onStopButton()
            }

            override fun scriptIsRunning(): Boolean {
                return scriptThread != null
            }

            override fun scriptIsRunnable(): Boolean {
                return scriptCode != null
            }
        }

        val logScrollView = findViewById<ScrollView>(R.id.log_scrollview)

        scriptUIControllers.helpers.add(
            ScriptControllerUIHelper(this,
                startPauseButton, stopButton, restartButton,
                logTv, logScrollView, scriptController) )

        screenCapClient = object : ScreenCaptureClient() {
                override fun onScreenCap(bm: Bitmap) {
                    Log.d(TAG, "onScreenCap: $bm")
                    lastScreencap = bm

                    screenCapLatchLock.lock()
                    screenCapLatch?.countDown()
                    screenCapLatchLock.unlock()
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

    private fun loadUserScript(tryStorage: Boolean = true) {
        if (tryStorage) {
            scriptCode = scriptStorage.getUserScriptCode(scriptFile as UserScriptFile)
            if (scriptCode != null) {
                // We loaded
                return
            }
        }
        val reqTask = RequestTask()
        reqTask.listener = object : RequestTask.OnResponseListener {
            override fun onResponse(resp: String?) {
                if (resp != null) {
                    scriptCode = resp
                    scriptStorage.putUserScriptCode(scriptFile as UserScriptFile,
                                                    scriptCode!!)
                    script = null
                    scriptUIControllers.notifyScriptStateChanged()
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

    private fun enableOverlay(enable: Boolean) {
        if (enable) {
            tryStartOverlay()
        } else if (overlayManager.started()) {
            stopOverlay()
        }
    }

    private fun tryStartOverlay() {
        if (!overlayManager.permittedToShow()) {
            Log.w(TAG, "Not permitted to show overlay")
            overlayManager.launchOverlayPermissionsActivity()
            showOverlayCheck.isChecked = false
            return
        }
        if (overlayManager.permittedToShow() && !overlayManager.started()) {
            overlayManager.showOverlay()
            if (overlayScriptControllerUIHelper != null) {
                scriptUIControllers.helpers.remove(overlayScriptControllerUIHelper!!)
            }
            overlayScriptControllerUIHelper =
                overlayManager.createScriptControllerUIHelper(scriptController)
            scriptUIControllers.helpers.add(overlayScriptControllerUIHelper!!)

            overlayManager.setOnCaptureTextButtonClick(View.OnClickListener {
                Log.i(TAG, "Capture in overlay pressed")
                // The next image cap will return a response in the client
                screenCapClient.requestScreenCap()
            })
        }
    }

    private fun doPreDestroyOverlayCleanup() {
        if (overlayScriptControllerUIHelper != null) {
            scriptUIControllers.helpers.remove(overlayScriptControllerUIHelper!!)
            overlayScriptControllerUIHelper = null
        }
    }

    private fun stopOverlay() {
        doPreDestroyOverlayCleanup()
        overlayManager.destroy()
    }

    override fun onDestroy() {
        stopOverlay()
        super.onDestroy()
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
                    loadUserScript(tryStorage = false)
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

    private fun setPaused(pause: Boolean) {
        val oldVal = scriptApi.paused.getAndSet(pause)
        if (oldVal != pause) {
            scriptUIControllers.notifyScriptStateChanged()
        }

    }

    fun startScript() {
        if (scriptThread != null) {
            setPaused(false)
        } else if (script == null && scriptCode != null) {
            try {
                scriptApi.paused.set(false)
                script = Script(this, scriptFile.key.toString(), scriptCode!!)
                if (!projecting) {
                    startProjection()
                }

                scriptThread = Thread(
                    Runnable {
                        script?.run(scriptApi)
                        onScriptEnded()
                    })
                scriptUIControllers.notifyScriptStateChanged()
                scriptThread!!.start()
            } catch (e: PyException) {
                onLogChanged(ScriptApi.LogEntry("ERROR: ${e.message}"))
            }
        }
    }

    fun onStartButton() {
        startScript()
    }

    fun onPauseButton() {
       setPaused(true)
    }

    fun onStopButton() {
        scriptThread?.interrupt()
    }

    fun onRestartButton() {
        restartingScript = true
        scriptThread?.interrupt()
    }

    /** Callback from script runner, when script completes */
    fun onScriptEnded() {
        scriptThread = null
        script = null
        scriptApi.paused.set(false)
        if (restartingScript) {
            restartingScript = false
            startScript()
        } else {
            stopProjection()
        }
        scriptUIControllers.notifyScriptStateChanged()
    }

    // From LogChangeListener
    override fun onLogChanged(newLog: ScriptApi.LogEntry) {
        scriptUIControllers.onLog(newLog)
    }

    // NOTE this method will block the thread.
    override fun getScreenCap(): Bitmap? {
        screenCapLatchLock.lock()
        screenCapLatch = CountDownLatch(1)
        screenCapLatchLock.unlock()

        screenCapClient.requestScreenCap()
        screenCapLatch!!.await()

        screenCapLatchLock.lock()
        if (screenCapLatch?.count != 0L) {
            screenCapLatch!!.countDown()
        }
        screenCapLatch = null
        screenCapLatchLock.unlock()

        return lastScreencap
    }
}