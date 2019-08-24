package com.tsiemens.androidscripter.activity

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity;
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

import kotlinx.android.synthetic.main.activity_main.*
import android.app.ActivityManager
import android.content.Context
import android.support.design.widget.Snackbar
import android.support.v4.content.LocalBroadcastManager
import android.widget.Button
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import com.tsiemens.androidscripter.*

private class MyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("MyReceiver", "onReceive")
    }
}

class MainActivity : AppCompatActivity() {
    companion object {
        val TAG = MainActivity::class.java.simpleName
    }

    val bcastReceiver : BroadcastReceiver? = MyReceiver()
    var projectionManager : MediaProjectionManager? = null

    val overlayManager = OverlayManager(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        tryGuaranteeUsageStatsAccess(this)

        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Clicked FAB", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }

        val startBtn = findViewById<Button>(R.id.start_button)
        val stopBtn = findViewById<Button>(R.id.stop_button)

        startBtn.setOnClickListener {
            val dataHelper = DataUtilHelper(this)

            val example1Script = dataHelper.getAssetUtf8Data("example1.py")
            Log.d(TAG, "example1Script: $example1Script")
            ScriptDriver(this).runScript(example1Script)

            if (!isMyServiceRunning(ScriptService::class.java)) {
                Intent(this, ScriptService::class.java).also { intent ->
                    startService(intent)
                }
            }
        }

        stopBtn.setOnClickListener {
            if (isMyServiceRunning(ScriptService::class.java)) {
                Intent(this, ScriptService::class.java).also { intent ->
                    stopService(intent)
                }
            }
        }

        val testBcastBtn = findViewById<Button>(R.id.testbcast_button)
        testBcastBtn.setOnClickListener {
            ServiceBcastClient(this).sendRunScript("example1.py")
        }

        val testOcrBtn = findViewById<Button>(R.id.ocr_button)
        testOcrBtn.setOnClickListener {
            //ServiceBcastClient(this).sendDoOcr()
            val PERMISSION_CODE = 1
            // startActivityForResult(projectionManager!!.createScreenCaptureIntent(), PERMISSION_CODE)
            startActivity(Intent(this, ScreenCaptureImageActivity::class.java))
        }

        val scriptActivityBtn = findViewById<Button>(R.id.script_activity_button)
        scriptActivityBtn.setOnClickListener {
            startActivity(Intent(this, ScriptRunnerActivity::class.java))
        }

        if (!isMyServiceRunning(ScriptService2::class.java)) {
            AccessibilitySettingDialogFragment().show(supportFragmentManager, "")
        }

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            bcastReceiver!!,
            IntentFilter(ScriptService2.ACTION_FROM_SERVICE)
        )
        if (overlayManager.permittedToShow()) {
            // overlayManager.showOverlay()
        } else {
            overlayManager.launchOverlayPermissionsActivity()
        }
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(bcastReceiver!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayManager.destroy()
    }

    private fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }
}
