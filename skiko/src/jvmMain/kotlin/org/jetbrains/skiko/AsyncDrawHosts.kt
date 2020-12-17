package org.jetbrains.skiko

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class AsyncDrawHosts<Display, Layer>(
    private val redraw: (layers: Collection<Layer>) -> Unit,
    private val getDisplay: Layer.() -> Display
) {
    private val displayToThread = mutableMapOf<Display, AsyncDrawThread<Layer>>()
    private val threadToDisplay = mutableMapOf<AsyncDrawThread<Layer>, Display>()

    private fun acquireThread(device: Display): AsyncDrawThread<Layer> {
        var thread = displayToThread[device]
        if (thread == null) {
            thread = AsyncDrawThread(redraw)
            displayToThread[device] = thread
            threadToDisplay[thread] = device
        }
        thread.acquire()
        return thread
    }

    private fun releaseThread(thread: AsyncDrawThread<Layer>) {
        thread.release()
        if (!thread.hasRefs) {
            val device = threadToDisplay[thread]!!
            threadToDisplay.remove(thread)
            displayToThread.remove(device)
        }
    }

    operator fun get(layer: Layer) = object : DrawHost {
        private var display: Display? = null
        private var thread: AsyncDrawThread<Layer>? = null

        override fun dispose() {
            thread?.release()
        }

        override fun needRedraw() {
            val display = layer.getDisplay()
            if (display != this.display) {
                thread?.also(::releaseThread)
                thread = acquireThread(display)
                this.display = display
            }
            thread?.needRedraw(layer)
        }
    }

    interface DrawHost {
        fun dispose()
        fun needRedraw()
    }
}

internal class AsyncDrawThread<Layer>(
    private val redraw: (layers: Collection<Layer>) -> Unit
) : Thread() {
    private val needRedraw = NeedRedraw()
    private val toRedraw = mutableSetOf<Layer>()
    private val toRedrawCopy = mutableSetOf<Layer>()
    private var refCount = 0

    override fun run() {
        try {
            while (!interrupted()) {
                draw()
            }
        } catch (e: InterruptedException) {
            // ignore
        }
    }

    private fun draw() {
        needRedraw.await()

        synchronized(toRedraw) {
            toRedrawCopy.clear()
            toRedrawCopy.addAll(toRedraw)
            toRedraw.clear()
        }

        redraw(toRedrawCopy)
    }

    val hasRefs get() = refCount > 0

    fun acquire() {
        check(currentThread() != this)
        check(refCount >= 0)

        if (refCount == 0) {
            start()
        }

        refCount++
    }

    fun release() {
        check(currentThread() != this)
        check(refCount > 0)

        refCount--

        if (refCount == 0) {
            interrupt()
            join()
        }
    }

    fun needRedraw(layer: Layer) {
        check(currentThread() != this)
        synchronized(toRedraw) {
            toRedraw.add(layer)
        }
        needRedraw.signal()
    }
}

internal class NeedRedraw {
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()
    private var needRedraw = false

    fun await() = lock.withLock {
        while (!needRedraw) {
            condition.await()
        }
        needRedraw = false
    }

    fun signal() = lock.withLock {
        needRedraw = true
        condition.signalAll()
    }
}