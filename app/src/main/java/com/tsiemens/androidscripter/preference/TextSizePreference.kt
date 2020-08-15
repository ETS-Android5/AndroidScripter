package com.tsiemens.androidscripter.preference

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.preference.PreferenceViewHolder
import androidx.preference.SeekBarPreference


class TextSizePreference(context: Context?, a: AttributeSet?, da: Int, dr: Int) : SeekBarPreference(context, a, da, dr) {
    constructor(c: Context?): this(c, null, androidx.preference.R.attr.seekBarPreferenceStyle, 0)
    constructor(c: Context?, a: AttributeSet?): this(c, a, androidx.preference.R.attr.seekBarPreferenceStyle, 0)
    constructor(c: Context?, a: AttributeSet?, da: Int): this(c, a, da, 0)

    companion object {
        val TAG = TextSizePreference::class.java.simpleName
    }

    var summaryView : AppCompatTextView? = null
    var onPreferenceChangeListenerSet = false

    override fun onAttached() {
        super.onAttached()
        if (!onPreferenceChangeListenerSet) {
            // We need our wrapper on the change listener set, so set up a dummy one if there
            // isn't one.
            setOnPreferenceChangeListener { preference, newValue -> true }
        }
    }

    override fun onBindViewHolder(view: PreferenceViewHolder?) {
        super.onBindViewHolder(view)
        if (view?.itemView != null && view.itemView is ViewGroup) {
            // printViewHierarchy(view.itemView as ViewGroup, "")

            summaryView = findSummaryView(view.itemView as ViewGroup)
            if (summaryView != null) {
                Log.i(TAG, "Found summary view: id ${summaryView!!.id}")
            }
            updateTextSizePreview(value)
        } else {
            Log.e(TAG, "view (" + view?.javaClass?.simpleName + ") is not ViewGroup")
        }
    }

    private fun findSummaryView(vg: ViewGroup): AppCompatTextView? {
        val summary = this.summary
        for (i in 0 until vg.getChildCount()) {
            val v = vg.getChildAt(i)
            if (v is AppCompatTextView) {
                Log.d(TAG, "Found AppCompatTextView. text: ${v.text}")
                if (v.text.contains(summary)) {
                    return v
                }
            }
            if (v is ViewGroup) {
                val sv = findSummaryView(v as ViewGroup)
                if (sv != null) {
                    return sv
                }
            }
        }
        return null
    }

    private fun printViewHierarchy(vg: ViewGroup, prefix: String) {
        for (i in 0 until vg.getChildCount()) {
            val v = vg.getChildAt(i)
            val desc: String =
                prefix + " | " + "[" + i + "/" + (vg.getChildCount() - 1) + "] " +
                        v.javaClass.simpleName + " " + v.getId()
            Log.v(TAG, desc)
            if (v is ViewGroup) {
                printViewHierarchy(v as ViewGroup, desc)
            }
        }
    }

    override fun setOnPreferenceChangeListener(onPreferenceChangeListener: OnPreferenceChangeListener?) {
        onPreferenceChangeListenerSet = true
        val myListener =
            OnPreferenceChangeListener { preference, newValue ->
                var res = true
                if (onPreferenceChangeListener != null){
                    res = onPreferenceChangeListener.onPreferenceChange(preference, newValue)
                }
                if (newValue != null) {
                    updateTextSizePreview(newValue as Int)
                }
                res
            }
        super.setOnPreferenceChangeListener(myListener)
    }

    private fun updateTextSizePreview(textSize: Int) {
        val newSize: Float = textSize.toFloat()
        Log.d(TAG, "New size is $newSize")
        if (summaryView != null) {
            summaryView?.textSize = newSize
            summaryView?.invalidate()
        } else {
            Log.w(TAG, "summaryView is null")
        }
    }
}