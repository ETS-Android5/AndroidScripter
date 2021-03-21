package com.tsiemens.androidscripter.activity

import android.os.Bundle
import android.widget.TextView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.appcompat.app.AppCompatActivity
import com.tsiemens.androidscripter.R
import com.tsiemens.androidscripter.util.NTObjPtr
import com.tsiemens.androidscripter.util.NTObjPtrMetrics

class DebugNTObjPtrViewerActivity : AppCompatActivity() {
    var ptrInfoTv : TextView? = null

    val ntIntPtr = NTObjPtr.new<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_exception_viewer)
        setSupportActionBar(findViewById(R.id.toolbar))

        ptrInfoTv = findViewById(R.id.exceptions_tv)

        updatePtrsView()

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { view ->
            if (ntIntPtr.obj() == null) {
                ntIntPtr.track(1) { _ -> }
            } else {
                ntIntPtr.untrack()
            }
            updatePtrsView()
        }
    }

    private fun updatePtrsView() {
        ptrInfoTv!!.setText("Instance counts:\n")

        for ((key, index) in NTObjPtrMetrics.classNameIndexMap) {
            val metrics = NTObjPtrMetrics.classMetrics[index]
            ptrInfoTv!!.append("${metrics.displayName}: ${metrics.instanceCount}\n")
        }
    }
}