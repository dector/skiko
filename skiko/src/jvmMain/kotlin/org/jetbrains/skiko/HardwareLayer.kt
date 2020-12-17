package org.jetbrains.skiko

import java.awt.Canvas

abstract class HardwareLayer : Canvas() {

    private val _contentScale = CachedValue { platformOperations.getDpiScale(this) }

    companion object {
        init {
            Library.load()
        }
    }

    override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
        super.setBounds(x, y, width, height)
        _contentScale.reset()
        contentScaleChanged()
    }

    protected open fun contentScaleChanged() = Unit

    // Can be create only after component will be shown to user (isShowing == true)
    protected open external fun createRedrawer(layer: HardwareLayer): Redrawer

    // Should be called in Swing thread
    internal abstract suspend fun update(nanoTime: Long)

    // Should be called in the OpenGL thread, and only once after update
    internal abstract fun draw()

    val windowHandle: Long
        external get

    val contentScale: Float
        get() = _contentScale.value

    var fullscreen: Boolean
        get() = platformOperations.isFullscreen(this)
        set(value) = platformOperations.setFullscreen(this, value)

    // TODO add ability to redraw immediately (after resize or window start)
    //  To do so maybe we need to make update synchronous (without suspend).
    //  or just call draw without update
    interface Redrawer {
        fun dispose()
        fun needRedraw()
    }
}