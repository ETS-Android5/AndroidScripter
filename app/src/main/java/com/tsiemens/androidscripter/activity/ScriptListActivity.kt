package com.tsiemens.androidscripter.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.*
import android.widget.TextView
import com.tsiemens.androidscripter.service.AccessibilitySettingDialogFragment
import com.tsiemens.androidscripter.R
import com.tsiemens.androidscripter.dialog.LicenseDialog
import com.tsiemens.androidscripter.service.ScriptAccessService
import com.tsiemens.androidscripter.dialog.ScriptEditDialog
import com.tsiemens.androidscripter.service.isMyServiceRunning
import com.tsiemens.androidscripter.storage.*
import com.tsiemens.androidscripter.tryGuaranteeUsageStatsAccess
import com.tsiemens.androidscripter.widget.RecyclerViewClickListener

import kotlinx.android.synthetic.main.activity_script_list.*

class ScriptListActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var viewManager: RecyclerView.LayoutManager
    private lateinit var recyclerClickListener: RecyclerViewClickListener

    private val scriptStorage = ScriptFileStorage(this)

    private val scriptFiles = arrayListOf<ScriptFile>()
    private val viewAdapter = ScriptFileAdapter(scriptFiles)

    companion object {
        val TAG = ScriptListActivity::class.java.simpleName
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_script_list)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
            createNewScript()
        }

        updateScriptAdapter()

        viewManager = LinearLayoutManager(this)

        recyclerView = findViewById<RecyclerView>(R.id.recycler_view).apply {
            // use this setting to improve performance if you know that changes
            // in content do not change the layout size of the RecyclerView
            setHasFixedSize(true)

            // use a linear layout manager
            layoutManager = viewManager

            // specify an viewAdapter (see also next example)
            adapter = viewAdapter
        }
        recyclerClickListener = object: RecyclerViewClickListener(this, recyclerView) {
            override fun onClick(view: View, position: Int) {
                val scriptFile = scriptFiles[position]
                startScriptActivity(scriptFile.key)
            }

            override fun onLongClick(view: View, position: Int) {}
        }

        recyclerView.addOnItemTouchListener(recyclerClickListener)
    }

    override fun onDestroy() {
        recyclerView.removeOnItemTouchListener(recyclerClickListener)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        updateScriptAdapter()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_launch_screen_cap_debug_activity -> {
                startActivity(Intent(this, ScreenCaptureImageActivity::class.java))
                true
            }
            R.id.action_launch_prototype_activity -> {
                startActivity(Intent(this, PrototypeActivity::class.java))
                true
            }
            R.id.action_launch_exceptions_activity -> {
                startActivity(Intent(this, DebugExceptionViewerActivity::class.java))
                true
            }
            R.id.action_launch_pointer_debug_activity -> {
                startActivity(Intent(this, DebugNTObjPtrViewerActivity::class.java))
                true
            }
            R.id.action_licenses -> {
                LicenseDialog().show(supportFragmentManager, "License dialog")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateScriptAdapter() {
        scriptFiles.clear()
        scriptFiles.addAll(scriptStorage.getScriptFiles())
        viewAdapter.notifyDataSetChanged()
    }

    /**
     * @return: true if permissions were already granted.
     *          false if the user is going to need to take some action which is not guaranteed
     *              complete on return
     */
    private fun tryGetPermissions(): Boolean {
        var alreadyGranted = tryGuaranteeUsageStatsAccess(this)

        if (!isMyServiceRunning(this, ScriptAccessService::class.java)) {
            AccessibilitySettingDialogFragment()
                .show(supportFragmentManager, "")
            alreadyGranted = false
        }
        return alreadyGranted
    }

    private fun startScriptActivity(key: ScriptKey) {
        if (!tryGetPermissions()) {
            return
        }
        // Run this on a very slight delay, to allow the ripple effect to take effect before
        // interrupting the UI thread to spawn the activity.
        Handler(mainLooper).postDelayed( {
            val intent = Intent(this@ScriptListActivity,
                ScriptRunnerActivity::class.java)
            intent.putExtra(ScriptRunnerActivity.INTENT_EXTRA_SCRIPT_KEY, key.toString())
            startActivity(intent)
        }, 100)
    }

    private fun createNewScript() {
        val createDialog = ScriptEditDialog()
        createDialog.onOkListener = object : ScriptEditDialog.OnOkListener {
            override fun onOk(name: String, url: String) {
                val nextIndex = scriptStorage.nextAvailableIndex(ScriptType.user, scriptFiles)
                val script = UserScriptFile(nextIndex, name, url)
                scriptFiles.add(script)
                viewAdapter.notifyDataSetChanged()

                scriptStorage.putUserScriptFile(script)
                startScriptActivity(script.key)
            }
        }
        createDialog.show(supportFragmentManager, "Create script dialog")
    }
}

class ScriptFileAdapter(private val myDataset: List<ScriptFile>) :
    RecyclerView.Adapter<ScriptFileAdapter.MyViewHolder>() {

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder.
    // Each data item is just a string in this case that is shown in a TextView.
    class MyViewHolder(listItem: View, val textView: TextView) : RecyclerView.ViewHolder(listItem)

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        // create a new view
        val listItem = LayoutInflater.from(parent.context)
            .inflate(R.layout.simple_selectable_list_item, parent, false)
        // set the view's size, margins, paddings and layout parameters

        val textView = listItem.findViewById<TextView>(R.id.primary_text)
        return MyViewHolder(listItem, textView)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        holder.textView.text = myDataset[position].name
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = myDataset.size
}
