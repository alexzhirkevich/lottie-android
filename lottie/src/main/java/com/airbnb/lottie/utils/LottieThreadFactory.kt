package com.airbnb.lottie.utils

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

class LottieThreadFactory : ThreadFactory {
    private val group: ThreadGroup
    private val threadNumber = AtomicInteger(1)
    private val namePrefix: String

    init {
        val s = System.getSecurityManager()
        group = if ((s == null)) Thread.currentThread().threadGroup else s.threadGroup
        namePrefix = "lottie-" + poolNumber.getAndIncrement() + "-thread-"
    }

    override fun newThread(r: Runnable): Thread {
        val t = Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0)
        // Don't prevent this thread from letting Android kill the app process if it wants to.
        t.isDaemon = false
        // This will block the main thread if it isn't high enough priority
        // so this thread should be as close to the main thread priority as possible.
        t.priority = Thread.MAX_PRIORITY
        return t
    }

    companion object {
        private val poolNumber = AtomicInteger(1)
    }
}
