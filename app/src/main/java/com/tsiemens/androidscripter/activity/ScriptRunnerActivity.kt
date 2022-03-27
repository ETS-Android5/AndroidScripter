package com.tsiemens.androidscripter.activity

import android.graphics.Bitmap
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.Toolbar
import android.util.Log
import android.view.View
import com.tsiemens.androidscripter.*
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import com.chaquo.python.PyException
import com.tsiemens.androidscripter.dialog.ScriptEditDialog
import com.tsiemens.androidscripter.inspect.ScreenProvider
import com.tsiemens.androidscripter.notify.ScreenInspectionListener
import com.tsiemens.androidscripter.overlay.DebugOverlayManager
import com.tsiemens.androidscripter.overlay.OverlayManager
import com.tsiemens.androidscripter.script.*
import com.tsiemens.androidscripter.storage.*
import com.tsiemens.androidscripter.util.BitmapUtil
import com.tsiemens.androidscripter.util.ColorCompat
import com.tsiemens.androidscripter.util.UiUtil
import com.tsiemens.androidscripter.widget.ScriptController
import com.tsiemens.androidscripter.widget.ScriptControllerUIHelper
import com.tsiemens.androidscripter.widget.ScriptControllerUIHelperColl
import com.tsiemens.androidscripter.widget.ScriptState
import java.lang.Exception
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

class ScriptRunnerActivity : ScreenCaptureActivityBase(),
    Api.LogChangeListener, ScreenProvider, ScreenInspectionListener {

    var scriptLogManager = ScriptLogManager()
    val overlayManager = OverlayManager(
        this,
        scriptLogManager,
        this,
        this
    )
    val debugOverlayManager = DebugOverlayManager(this)

    lateinit var screenCapClient: ScreenCaptureClient

    val dataHelper = DataUtilHelper(this)

    private val scriptStorage = ScriptFileStorage(this)
    private lateinit var scriptFile: ScriptFile

    var scriptThread: Thread? = null
    var script: Script? = null
    var scriptCode: String? = null
    var scriptApi = Api(this, this, this, this,
        overlayManager, debugOverlayManager)

    lateinit var scriptNameTv: TextView
    lateinit var logTv: TextView
    lateinit var showOverlayCheck: CheckBox
    lateinit var logLevelSpinner: Spinner

    var lastScreencap: Bitmap? = null
    var lastScreencapId: Long = 0
    var lastRetrievedScreencapId: Long = 0
    val screenCapLatchLock = ReentrantLock()
    var screenCapLatch: CountDownLatch? = null

    var overlayScriptControllerUIHelper: ScriptControllerUIHelper? = null
    val scriptUIControllers = ScriptControllerUIHelperColl()
    lateinit var scriptController: ScriptController

    var restartingScript = false

    companion object {
        val INTENT_EXTRA_SCRIPT_KEY = "script_key"

        private val TAG = ScriptRunnerActivity::class.java.simpleName

        val globalThreadListLock = ReentrantReadWriteLock()
        val globalThreadList = arrayListOf<Thread>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
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

        validateGlobalThreadListEmpty()

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

        logLevelSpinner = findViewById(R.id.log_level_spinner)

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

            override fun scriptIsRunnable(): Boolean {
                return scriptCode != null
            }
        }

        val logScrollView = findViewById<ScrollView>(R.id.log_scrollview)

        scriptUIControllers.helpers.add(
            ScriptControllerUIHelper(this,
                startPauseButton, stopButton, restartButton,
                logTv, logScrollView, logLevelSpinner, scriptLogManager, scriptController) )

        screenCapClient = object : ScreenCaptureClient() {
                override fun onScreenCap(bm: Bitmap, imgId: Long) {
                    Log.d(TAG, "onScreenCap: $bm")
                    lastScreencap = bm
                    lastScreencapId = imgId

                    screenCapLatchLock.lock()
                    screenCapLatch?.countDown()
                    screenCapLatchLock.unlock()
                }
            }
        setScreenCaptureClient(screenCapClient)
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
            override fun onResponse(resp: String) {
                scriptCode = resp
                scriptStorage.putUserScriptCode(scriptFile as UserScriptFile,
                                                scriptCode!!)
                script = null
                scriptUIControllers.notifyScriptStateChanged()
                Toast.makeText(this@ScriptRunnerActivity,
                    "Script loaded", Toast.LENGTH_SHORT).show()
            }
            override fun onError(e: Exception) {
                Toast.makeText(this@ScriptRunnerActivity,
                    "Error getting script: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        reqTask.execute((scriptFile as UserScriptFile).url)
    }

    private fun deleteUserScript() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage("Delete script?")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                scriptStorage.deleteUserScript(scriptFile as UserScriptFile)
                finish()
            }
        builder.create().show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
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
            debugOverlayManager.bringToFront()

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

    private fun stopDebugCanvasOverlay() {
        debugOverlayManager.destroy()
    }

    private fun stopAllOverlays() {
        stopOverlay()
        stopDebugCanvasOverlay()
    }

    override fun onResume() {
        super.onResume()
        if (!debugOverlayManager.permittedToShow()) {
            Log.w(TAG, "Not permitted to show overlay")
            debugOverlayManager.launchOverlayPermissionsActivity()
            return
        }
        if (debugOverlayManager.permittedToShow() && !debugOverlayManager.started()) {
            debugOverlayManager.showOverlay()
        }
    }

    override fun onDestroy() {
        stopAllOverlays()
        stopThread()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.script_runner_menu, menu)
        if (scriptFile.key.type == ScriptType.sample) {
            menu.findItem(R.id.action_edit).isEnabled = false
            menu.findItem(R.id.action_refresh).isEnabled = false
            menu.findItem(R.id.action_delete).isEnabled = false
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
            R.id.action_delete -> {
                if (scriptFile.key.type == ScriptType.user) {
                    deleteUserScript()
                }
                true
            }
            R.id.action_launch_pointer_debug_activity -> {
                startActivity(Intent(this, DebugNTObjPtrViewerActivity::class.java))
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

    private fun validateGlobalThreadListEmpty() {
        var extraThreadsFound = 0
        globalThreadListLock.write {
            if (globalThreadList.isNotEmpty()) {
                extraThreadsFound = globalThreadList.size
                for (t in globalThreadList) {
                    t.interrupt()
                }
                globalThreadList.clear()
            }
        }
        if (extraThreadsFound > 0) {
            Log.e(TAG, "startScript: Thread list is not empty! Found $extraThreadsFound")
            UiUtil.forceToMainThread {
                Toast.makeText(this, "Found unexpected threads", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setPaused(pause: Boolean) {
        val oldVal = scriptApi.paused.getAndSet(pause)
        if (oldVal != pause) {
            scriptUIControllers.notifyScriptStateChanged()
        }

    }

    private fun startScript() {
        if (scriptThread != null) {
            setPaused(false)
        } else if (script == null && scriptCode != null) {
            if (!tryGetApiPermissions(this)) {
                // We don't have permissions for all the API endpoints yet. The above will have
                // launched an activity to acquire them (start the accessibility service). The
                // start button will need to be pressed a second time when the user returns.
                return
            }
            try {
                scriptApi.paused.set(false)
                script = Script(
                    this,
                    scriptFile.key.toString(),
                    scriptCode!!
                )
                if (!projecting) {
                    startProjection()
                }

                scriptThread = Thread(
                    Runnable {
                        script?.run(scriptApi)
                        onScriptEnded()
                    })

                validateGlobalThreadListEmpty()
                globalThreadList.add(scriptThread!!)
                scriptUIControllers.notifyScriptStateChanged()
                scriptThread!!.start()
            } catch (e: PyException) {
                onLogChanged(
                    Api.LogEntry("ERROR: ${e.message}", Api.LogLevel.ERROR))
            }
        }
    }

    fun onStartButton() {
        startScript()
    }

    fun onPauseButton() {
       setPaused(true)
    }

    fun stopThreadAndRemoveFromList(tr: Thread) {
        tr.interrupt()
        globalThreadListLock.write {
            globalThreadList.remove(tr)
        }
    }

    fun stopThread() {
        scriptThread?.apply { stopThreadAndRemoveFromList(this) }
        scriptThread = null
    }

    fun onStopButton() {
        stopThread()
    }

    fun onRestartButton() {
        restartingScript = true
        scriptThread?.interrupt()
    }

    /** Callback from script runner, when script completes */
    fun onScriptEnded() {
        scriptThread?.apply {
            globalThreadList.remove(this)
        }
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
    override fun onLogChanged(newLog: Api.LogEntry) {
        scriptLogManager.addLog(newLog)
        scriptUIControllers.onLog(newLog)
    }

    // NOTE this method will block the thread.
    override fun getScreenCap(cropPadding: Boolean): Bitmap? {
        screenCapLatchLock.lock()
        screenCapLatch = CountDownLatch(1)
        screenCapLatchLock.unlock()

        screenCapClient.requestScreenCap()
        // Ok is false if we time out
        val ok = screenCapLatch!!.await(1, TimeUnit.SECONDS)

        screenCapLatchLock.lock()
        if (screenCapLatch?.count != 0L) {
            screenCapLatch!!.countDown()
        }
        screenCapLatch = null
        screenCapLatchLock.unlock()

        var bm : Bitmap? = null
        if (ok) {
            if (lastScreencapId == lastRetrievedScreencapId) {
                val msg = "WARN: Last screencap has not changed since last request"
                onLogChanged(Api.LogEntry(msg, Api.LogLevel.WARNING))
                Log.w(TAG, msg)
            }
            lastRetrievedScreencapId = lastScreencapId

            if (lastScreencap != null) {
                runOnUiThread {
                    overlayManager.updateScreenCaptureViewer(lastScreencap!!)
                }
            }

            bm = lastScreencap

            if (cropPadding && bm != null) {
                bm = BitmapUtil.cropScreenshotPadding(bm)
            }
        } else {
            val msg = "WARN: Timed out getting screencap"
            onLogChanged(Api.LogEntry(msg, Api.LogLevel.WARNING))
            Log.w(TAG, msg)
        }
        return bm
    }

    override fun onPointInspected(x: Float, y: Float, color: ColorCompat, isPercent: Boolean) {
        debugOverlayManager.onPointInspected(x, y, isPercent)
        overlayManager.onPointInspected(x, y, color, isPercent)
    }
}