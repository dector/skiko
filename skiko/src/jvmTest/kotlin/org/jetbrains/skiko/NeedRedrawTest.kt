package org.jetbrains.skiko

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.concurrent.thread

internal class NeedRedrawTest {
    @Test
    fun `await without signal`() {
        val test = object {
            @Volatile
            var isNeverEnded = true
        }
        val needDraw = NeedRedraw()

        val thread = testThread {
            needDraw.await()
            test.isNeverEnded = false
        }

        Thread.sleep(1000)
        assertTrue(test.isNeverEnded)

        thread.interrupt()
    }

    @Test
    fun `await after previous resumed await`() {
        val test = object {
            @Volatile
            var isNeverEnded = true
        }
        val needDraw = NeedRedraw()

        val thread = testThread {
            needDraw.await()
            needDraw.await()
            test.isNeverEnded = false
        }

        Thread.sleep(1000)
        needDraw.signal() // resume first await (second await shouldn't be resumed)
        Thread.sleep(1000)
        assertTrue(test.isNeverEnded)

        thread.interrupt()
    }

    @Test
    fun `multiple signals should resume only current await`() {
        val test = object {
            @Volatile
            var isNeverEnded = true
        }
        val needDraw = NeedRedraw()

        val thread = testThread {
            needDraw.await()
            Thread.sleep(1000)
            needDraw.await()
            test.isNeverEnded = false
        }

        Thread.sleep(1000)
        needDraw.signal() // resume first await
        needDraw.signal() // do nothing (second await shouldn't be resumed)
        needDraw.signal() // do nothing (second await shouldn't be resumed)
        Thread.sleep(1000)
        assertTrue(test.isNeverEnded)

        thread.interrupt()
    }

    @Test(timeout = 5000)
    fun `signal before await`() {
        val needDraw = NeedRedraw()
        needDraw.signal()

        val thread = testThread {
            needDraw.await()
        }

        thread.join()
    }

    @Test(timeout = 5000)
    fun `signal after await`() {
        val needDraw = NeedRedraw()

        val thread = testThread {
            needDraw.await()
        }

        Thread.sleep(1000)
        needDraw.signal()

        thread.join()
    }

    private fun testThread(body: () -> Unit) = thread {
        try {
            body()
        } catch (e: InterruptedException) {
            // ignore
        }
    }
}