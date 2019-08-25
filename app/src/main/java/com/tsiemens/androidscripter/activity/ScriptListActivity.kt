package com.tsiemens.androidscripter.activity

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import android.widget.TextView
import com.tsiemens.androidscripter.R
import com.tsiemens.androidscripter.dialog.ScriptEditDialog
import com.tsiemens.androidscripter.storage.*
import com.tsiemens.androidscripter.widget.RecyclerViewClickListener

import kotlinx.android.synthetic.main.activity_script_list.*

class ScriptListActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: RecyclerView.Adapter<*>
    private lateinit var viewManager: RecyclerView.LayoutManager
    private lateinit var recyclerClickListener: RecyclerViewClickListener

    private val scriptStorage = ScriptFileStorage(this)

    private val scriptFiles = arrayListOf<ScriptFile>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_script_list)
        setSupportActionBar(toolbar)

        scriptFiles.addAll(scriptStorage.getScriptFiles())

        fab.setOnClickListener { view ->
//            scriptFiles.add("Entry " + nextNum.toString())
//            nextNum++
//            viewAdapter.notifyDataSetChanged()
            createNewScript()
        }

        viewManager = LinearLayoutManager(this)
        viewAdapter = ScriptFileAdapter(scriptFiles)

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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            R.id.action_launch_prototype_activity -> {
                startActivity(Intent(this, MainActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun startScriptActivity(key: ScriptKey) {
        val intent = Intent(this@ScriptListActivity,
                ScriptRunnerActivity::class.java)
        intent.putExtra(ScriptRunnerActivity.INTENT_EXTRA_SCRIPT_KEY, key.toString())
        startActivity(intent)
    }

    private fun createNewScript() {
        val createDialog = ScriptEditDialog()
        createDialog.onOkListener = object : ScriptEditDialog.OnOkListener {
            override fun onOk(name: String, url: String) {
                val nextIndex = scriptStorage.nextAvailableIndex(ScriptType.user, scriptFiles)
                val script = UserScriptFile(nextIndex, name, url)
                scriptFiles.add(script)
                viewAdapter.notifyDataSetChanged()

                scriptStorage.addScriptFile(script)
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
    class MyViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup,
                                    viewType: Int): ScriptFileAdapter.MyViewHolder {
        // create a new view
        val textView = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false) as TextView
        // set the view's size, margins, paddings and layout parameters

        textView.isClickable = true
        textView.isFocusable = true

        // Apply the "wave" effect on click
        val attrs = intArrayOf(android.R.attr.selectableItemBackground)
        val typedArray = parent.context.obtainStyledAttributes(attrs)
        val backgroundResource = typedArray.getResourceId(0, 0)
        typedArray.recycle()
        textView.foreground = parent.context.getDrawable(backgroundResource)

        return MyViewHolder(textView)
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
