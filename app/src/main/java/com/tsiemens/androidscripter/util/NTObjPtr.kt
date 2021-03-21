package com.tsiemens.androidscripter.util

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write


class NTObjPtrMetrics(val displayName: String) {
    var instanceCount: Long = 0

    companion object {
        val classNameIndexMap = hashMapOf<String, Int>()
        var classMetrics = arrayListOf<NTObjPtrMetrics>()

        fun getMetricsIndex(key: String, displayName: String): Int {
            var index = classNameIndexMap.get(key)
            if (index == null) {
                index = classMetrics.size
                classNameIndexMap[key] = index
                classMetrics.add(NTObjPtrMetrics(displayName))
            }
            return index
        }
    }
}

// NOTE there should be one Wrapper per object (or per object life cycle)
class NTObjWrapper<T>(
    val obj: T,
    val destroyer: (T) -> Unit,
    val metrics: NTObjPtrMetrics
) {
    private val lock = ReentrantReadWriteLock()
    var refs: Int = 0
    var destroyed = false

    init {
        metrics.instanceCount++
    }

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
            metrics.instanceCount--
        }
    }
}

class NTObjPtrSpecialisedCompanion<T> {
    var metricId = 0
}

/** Non-trivial object pointer.
 * Acts as a handle for objects which require some release action (like being closed)
 * before giving the object to the garbage collector.
 * */
class NTObjPtr<T> protected constructor(val metrics: NTObjPtrMetrics) {
    private var wrapper: NTObjWrapper<T>? = null

    companion object {
        inline fun <reified T> new(): NTObjPtr<T> {
            val index = NTObjPtrMetrics.getMetricsIndex(
                T::class.java.canonicalName!!, T::class.java.simpleName)
            val metrics = NTObjPtrMetrics.classMetrics[index]
            val ptr = NTObjPtr<T>(metrics)
            return ptr
        }
    }

    fun track(t: T, destroyer: (T) -> Unit) {
        val newWrapper = NTObjWrapper(t, destroyer, metrics)
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
    fun trackFor(action: (t: T?) -> Unit) {
        val tmpPtr = NTObjPtr<T>(metrics)
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