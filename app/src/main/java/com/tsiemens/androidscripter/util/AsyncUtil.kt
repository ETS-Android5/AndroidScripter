package com.tsiemens.androidscripter.util

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit


class SimpleFuture<T>: Future<T> {
    val doneLatch = CountDownLatch(1)
    var result: T? = null

    override fun cancel(p0: Boolean): Boolean {
        throw NotImplementedError()
    }

    override fun isCancelled(): Boolean {
        return false
    }

    override fun isDone(): Boolean {
        return doneLatch.count == 0L
    }

    override fun get(): T? {
        doneLatch.await()
        return result
    }

    override fun get(timeout: Long, unit: TimeUnit?): T? {
        doneLatch.await(timeout, unit)
        return result
    }

    fun set(v: T?) {
        if (isDone) {
            throw IllegalStateException("Cannot set twice")
        }
        result = v
        doneLatch.countDown()
    }
}