package com.tsiemens.androidscripter.activity

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.appcompat.app.AppCompatActivity
import com.tsiemens.androidscripter.R
import com.tsiemens.androidscripter.storage.ScriptType
import com.tsiemens.androidscripter.thread.UncaughtException
import com.tsiemens.androidscripter.thread.UncaughtExceptionHandler

class DebugExceptionViewerActivity : AppCompatActivity() {
    var exceptionsTv : TextView? = null
    var exceptionCnt = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_exception_viewer)
        setSupportActionBar(findViewById(R.id.toolbar))

        exceptionsTv = findViewById(R.id.exceptions_tv)

        updateExceptionsView()

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { view ->
            Thread(
                Runnable {
                    throw Exception("Dummy crash thread")
                }).start()

            Handler(Looper.getMainLooper()).postDelayed({
                updateExceptionsView()
            }, 500)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.exception_viewer_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_clear -> { clearExceptions(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateExceptionsView() {
        exceptionsTv!!.setText("")
        exceptionCnt = 0
        val globExHandler =
            UncaughtExceptionHandler.getGlobalUncaughtExceptionHandler(this)
        for (exception in globExHandler.getExceptionHistory()) {
            addException(exception)
        }
    }

    private fun addException(ue: UncaughtException) {
        addExceptionText(ue.dateStr + "\n" + ue.desc)
    }

    private fun addExceptionText(str: String) {
        val separator = "\n*****************************************************\n"
        if (exceptionCnt > 0) {
            exceptionsTv!!.append(separator)
        }
        exceptionsTv!!.append(str)
        exceptionCnt += 1
    }

    private fun clearExceptions() {
        UncaughtExceptionHandler.getGlobalUncaughtExceptionHandler(this).clearExceptionHistory()
        updateExceptionsView()
    }
}