package com.tsiemens.androidscripter.widget

import android.content.Context
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView
import android.view.GestureDetector
import android.view.View

interface RecyclerViewClickListenerIntf {
    fun onClick(view: View, position: Int)

    fun onLongClick(view: View, position: Int)
}

abstract class RecyclerViewClickListener(
    context: Context,
    recyclerView: RecyclerView
    ) : RecyclerView.OnItemTouchListener, RecyclerViewClickListenerIntf {

    private val gestureDetector: GestureDetector

    init {
        gestureDetector =
            GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    val child = recyclerView.findChildViewUnder(e.x, e.y)
                    if (child != null) {
                        // Interface call
                        onLongClick(child, recyclerView.getChildAdapterPosition(child))
                    }
                }
            })
    }

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {

        val child = rv.findChildViewUnder(e.x, e.y)
        if (child != null && gestureDetector.onTouchEvent(e)) {
            // Interface call
            onClick(child, rv.getChildAdapterPosition(child))
        }
        return false
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
}