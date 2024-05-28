package com.airbnb.lottie

import android.os.Handler
import android.os.Looper
import androidx.annotation.RestrictTo
import com.airbnb.lottie.utils.Logger
import com.airbnb.lottie.utils.LottieThreadFactory
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import kotlin.concurrent.Volatile

/**
 * Helper to run asynchronous tasks with a result.
 * Results can be obtained with [.addListener].
 * Failures can be obtained with [.addFailureListener].
 *
 *
 * A task will produce a single result or a single failure.
 */
class LottieTask<T> {
    /* Preserve add order. */
    private val successListeners: MutableSet<LottieListener<T>> = LinkedHashSet(1)
    private val failureListeners: MutableSet<LottieListener<Throwable?>> = LinkedHashSet(1)
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var result: LottieResult<T>? = null

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    constructor(runnable: Callable<LottieResult<T>>) : this(runnable, false)

    constructor(result: T) {
        setResult(LottieResult(result))
    }

    /**
     * runNow is only used for testing.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    internal constructor(runnable: Callable<LottieResult<T>>, runNow: Boolean) {
        if (runNow) {
            try {
                setResult(runnable.call())
            } catch (e: Throwable) {
                setResult(LottieResult(e))
            }
        } else {
            EXECUTOR.execute(LottieFutureTask(this, runnable))
        }
    }

    private fun setResult(result: LottieResult<T>?) {
        check(this.result == null) { "A task may only be set once." }
        this.result = result
        notifyListeners()
    }

    /**
     * Add a task listener. If the task has completed, the listener will be called synchronously.
     *
     * @return the task for call chaining.
     */
    @Synchronized
    fun addListener(listener: LottieListener<T>): LottieTask<T> {
        val result: LottieResult<T>? = this.result
        if (result != null && result.value != null) {
            listener.onResult(result.value)
        }

        successListeners.add(listener)
        return this
    }

    /**
     * Remove a given task listener. The task will continue to execute so you can re-add
     * a listener if necessary.
     *
     * @return the task for call chaining.
     */
    @Synchronized
    fun removeListener(listener: LottieListener<T>): LottieTask<T> {
        successListeners.remove(listener)
        return this
    }

    /**
     * Add a task failure listener. This will only be called in the even that an exception
     * occurs. If an exception has already occurred, the listener will be called immediately.
     *
     * @return the task for call chaining.
     */
    @Synchronized
    fun addFailureListener(listener: LottieListener<Throwable?>): LottieTask<T> {
        val result = this.result
        if (result?.exception != null) {
            listener.onResult(result.exception)
        }

        failureListeners.add(listener)
        return this
    }

    /**
     * Remove a given task failure listener. The task will continue to execute so you can re-add
     * a listener if necessary.
     *
     * @return the task for call chaining.
     */
    @Synchronized
    fun removeFailureListener(listener: LottieListener<Throwable?>): LottieTask<T> {
        failureListeners.remove(listener)
        return this
    }

    fun getResult(): LottieResult<T>? {
        return result
    }

    private fun notifyListeners() {
        // Listeners should be called on the main thread.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            notifyListenersInternal()
        } else {
            handler.post { this.notifyListenersInternal() }
        }
    }

    private fun notifyListenersInternal() {
        // Local reference in case it gets set on a background thread.
        val result = this@LottieTask.result ?: return
        if (result.value != null) {
            notifySuccessListeners(result.value)
        } else {
            notifyFailureListeners(result.exception)
        }
    }

    @Synchronized
    private fun notifySuccessListeners(value: T) {
        // Allows listeners to remove themselves in onResult.
        // Otherwise we risk ConcurrentModificationException.
        val listenersCopy: List<LottieListener<T>> = ArrayList(successListeners)
        for (l in listenersCopy) {
            l.onResult(value)
        }
    }

    @Synchronized
    private fun notifyFailureListeners(e: Throwable?) {
        // Allows listeners to remove themselves in onResult.
        // Otherwise we risk ConcurrentModificationException.
        val listenersCopy: List<LottieListener<Throwable?>> = ArrayList(failureListeners)
        if (listenersCopy.isEmpty()) {
            Logger.warning("Lottie encountered an error but no failure listener was added:", e)
            return
        }

        for (l in listenersCopy) {
            l.onResult(e)
        }
    }

    private class LottieFutureTask<T>(private var lottieTask: LottieTask<T>?, callable: Callable<LottieResult<T>>) :
        FutureTask<LottieResult<T>>(callable) {
        override fun done() {
            try {
                if (isCancelled) {
                    // We don't need to notify and listeners if the task is cancelled.
                    return
                }

                try {
                    lottieTask!!.setResult(get())
                } catch (e: InterruptedException) {
                    lottieTask!!.setResult(LottieResult(e))
                } catch (e: ExecutionException) {
                    lottieTask!!.setResult(LottieResult(e))
                }
            } finally {
                // LottieFutureTask can be held in memory for up to 60 seconds after the task is done, which would
                // result in holding on to the associated LottieTask instance and leaking its listeners. To avoid
                // that, we clear our the reference to the LottieTask instance.
                //
                // How is LottieFutureTask held for up to 60 seconds? It's a bug in how the VM cleans up stack
                // local variables. When you have a loop that polls a blocking queue and assigns the result
                // to a local variable, after looping the local variable will still reference the previous value
                // until the queue returns the next result.
                //
                // Executors.newCachedThreadPool() relies on a SynchronousQueue and creates a cached thread pool
                // with a default keep alice of 60 seconds. After a given worker thread runs a task, that thread
                // will wait for up to 60 seconds for a new task to come, and while waiting it's also accidentally
                // keeping a reference to the previous task.
                //
                // See commit d577e728e9bccbafc707af3060ea914caa73c14f in AOSP for how that was fixed for Looper.
                lottieTask = null
            }
        }
    }

    companion object {
        /**
         * Set this to change the executor that LottieTasks are run on. This will be the executor that composition parsing and url
         * fetching happens on.
         *
         *
         * You may change this to run deserialization synchronously for testing.
         */
        @JvmField
        var EXECUTOR: Executor = Executors.newCachedThreadPool(LottieThreadFactory())
    }
}
