package org.jetbrains.skiko.redrawer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.jetbrains.skiko.FrameDispatcher
import org.jetbrains.skiko.HardwareLayer

@Suppress("unused")
internal class WindowsRedrawer(
    private val layer: HardwareLayer
) : HardwareLayer.Redrawer {
    private val device = getDevice(layer)
    private val context = createContext(device)
    private var isDisposed = false

    init {
        makeCurrent()
        // For vsync we will use dwmFlush instead of swapInterval,
        // because it isn't reliable with DWM (Desktop Windows Manager): interval between frames isn't stable (14-19ms).
        // With dwmFlush it is stable (16.6-16.8 ms)
        // GLFW also uses dwmFlush (https://www.glfw.org/docs/3.0/window.html#window_swap)
        setSwapInterval(0)
    }

    override fun dispose() {
        check(!isDisposed)
        deleteContext(context)
    }

    override fun needRedraw() {
        toRedraw.add(this)
        frameDispatcher.scheduleFrame()
    }

    private fun makeCurrent() = makeCurrent(device, context)
    private fun swapBuffers() = swapBuffers(device)

    companion object {
        private val toRedraw = mutableSetOf<WindowsRedrawer>()
        private val toRedrawCopy = mutableSetOf<WindowsRedrawer>()
        private val toRedrawAlive = toRedrawCopy.asSequence().filterNot(WindowsRedrawer::isDisposed)

        private val frameDispatcher = FrameDispatcher(Dispatchers.Swing) { nanoTime ->
            toRedrawCopy.clear()
            toRedrawCopy.addAll(toRedraw)
            toRedraw.clear()

            toRedrawAlive.forEach {
                it.layer.update(nanoTime)
            }

            toRedrawAlive.forEach {
                it.makeCurrent()
                it.layer.draw()
            }

            withContext(Dispatchers.IO) {
                toRedrawAlive.forEach {
                    it.swapBuffers()
                }
                dwmFlush() // wait for vsync
            }
        }
    }
}

private external fun makeCurrent(device: Long, context: Long)
private external fun getDevice(layer: HardwareLayer): Long
private external fun createContext(device: Long): Long
private external fun deleteContext(context: Long)
private external fun setSwapInterval(interval: Int)
private external fun swapBuffers(device: Long)

// TODO according to https://bugs.chromium.org/p/chromium/issues/detail?id=467617 dwmFlush has lag 3 ms after vsync.
//  Maybe we should use D3DKMTWaitForVerticalBlankEvent? See also https://www.vsynctester.com/chromeisbroken.html
// TODO should we support Windows 7? DWM can be disabled on Windows 7.
//  it that case there will be a crash or just no frame limit (I don't know exactly).
private external fun dwmFlush()