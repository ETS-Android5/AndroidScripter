package com.tsiemens.androidscripter.util

import java.lang.IllegalStateException
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

// NOTE there should be one Wrapper per object (or per object life cycle)
class NTObjWrapper<T>(val obj: T,
                      val destroyer: (T)->Unit) {
    private val lock = ReentrantReadWriteLock()
    var refs: Int = 0
    var destroyed = false

    fun inc() {
        lock.write {
            if (destroyed) {
                throw IllegalStateException("Cannot inc a destroyed object")
            }
            refs++
        }
    }

    fun tryIncAndGet(): NTObjWrapper<T>? {
        lock.write {
            if (!destroyed) {
                refs++
                return this
            }
        }
        return null
    }

    fun dec() {
        var doDestroy = false
        lock.write {
            refs--
            doDestroy = refs <= 0 && !destroyed
            if (doDestroy) {
                destroyed = true
            }
        }
        if (doDestroy) {
            destroyer(obj)
        }
    }
}

/** Non-trivial object pointer.
 * Acts as a handle for objects which require some release action (like being closed)
 * before giving the object to the garbage collector.
 * */
class NTObjPtr<T> {
    private var wrapper: NTObjWrapper<T>? = null

    fun track(t: T, destroyer: (T)->Unit) {
        val newWrapper = NTObjWrapper(t, destroyer)
        newWrapper.inc()
        wrapper?.dec()
        wrapper = newWrapper
    }

    fun track(p: NTObjPtr<T>) {
        val newWrapper = p.wrapper?.tryIncAndGet()
        wrapper?.dec()
        wrapper = newWrapper
    }

    /** Run @action with pointer to obj, which is guaranteed to not be destroyed during
     * the execution. */
    fun trackFor(action: ( t: T? )->Unit ) {
        val tmpPtr = NTObjPtr<T>()
        tmpPtr.track(this)
        try {
            action(tmpPtr.obj())
        } finally {
            tmpPtr.untrack()
        }
    }

    fun untrack() {
        wrapper?.dec()
        wrapper = null
    }

    fun obj(): T? {
        return wrapper?.obj
    }
}
