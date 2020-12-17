package org.jetbrains.skiko

import org.jetbrains.skija.*
import java.awt.Graphics
import java.awt.event.HierarchyEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities.isEventDispatchThread

// Should be accessed only from the draw thread ([SkiaLayer.draw])
private class SkijaState {
    val bleachConstant = if (hostOs == OS.MacOS) 0 else -1
    var context: DirectContext? = null
    var renderTarget: BackendRenderTarget? = null
    var surface: Surface? = null
    var canvas: Canvas? = null

    fun clear() {
        surface?.close()
        renderTarget?.close()
    }
}

private class PictureHolder(val instance: Picture, val width: Int, val height: Int)

open class SkiaLayer : HardwareLayer() {
    open val api: GraphicsApi = GraphicsApi.OPENGL

    var renderer: SkiaRenderer? = null
    val clipComponents = mutableListOf<ClipRectangle>()

    private val skijaState = SkijaState()
    private var inited = AtomicBoolean(false)
    @Volatile
    private var isDisposed = false
    private var redrawer: Redrawer? = null
    @Volatile
    private var picture: PictureHolder? = null
    private val pictureRecorder = PictureRecorder()

    init {
        @Suppress("LeakingThis")
        addHierarchyListener {
            if (it.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) {
                checkVisibility()
            }
        }
    }

    private fun checkVisibility() {
        if (redrawer == null && isShowing) {
            redrawer = createRedrawer(this)
            needRedraw()
        }
    }

    fun needRedraw() {
        check(!isDisposed)
        check(isEventDispatchThread())
        redrawer?.needRedraw()
    }

    open fun dispose() {
        check(!isDisposed)
        check(isEventDispatchThread())
        redrawer?.dispose()
        picture?.instance?.close()
        pictureRecorder.close()
        isDisposed = true
    }

    override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
        super.setBounds(x, y, width, height)
        needRedraw()
    }

    override fun paint(g: Graphics?) {
        needRedraw()
    }

    override suspend fun update(nanoTime: Long) {
        check(!isDisposed)
        check(isEventDispatchThread())
        check(picture == null)

        val pictureWidth = (width * contentScale).toInt()
        val pictureHeight = (height * contentScale).toInt()

        val bounds = Rect.makeWH(pictureWidth.toFloat(), pictureHeight.toFloat())!!
        val canvas = pictureRecorder.beginRecording(bounds)!!

        // clipping
        for (component in clipComponents) {
            canvas.clipRectBy(component)
        }

        renderer?.onRender(canvas, pictureWidth, pictureHeight, nanoTime)

        val picture = pictureRecorder.finishRecordingAsPicture()
        this.picture = PictureHolder(picture, pictureWidth, pictureHeight)
    }

    override fun draw() {
        check(!isDisposed)
        val picture = checkNotNull(picture)

        if (!inited.getAndSet(true)) {
            if (skijaState.context == null) {
                skijaState.context = when (api) {
                    GraphicsApi.OPENGL -> makeGLContext()
                    GraphicsApi.METAL -> makeMetalContext()
                    else -> TODO("Unsupported yet")
                }
            }
        }
        initSkija(picture.width, picture.height)
        skijaState.apply {
            canvas!!.clear(bleachConstant)
            canvas!!.drawPicture(picture.instance)
            context!!.flush()
        }
        picture.instance.close()
        this.picture = null
    }

    private fun Canvas.clipRectBy(rectangle: ClipRectangle) {
        clipRect(
            Rect.makeLTRB(
                rectangle.x,
                rectangle.y,
                rectangle.x + rectangle.width,
                rectangle.y + rectangle.height
            ),
            ClipMode.DIFFERENCE,
            true
        )
    }

    private fun initSkija(width: Int, height: Int) {
        initRenderTarget(width, height)
        initSurface()
    }

    private fun initRenderTarget(width: Int, height: Int) {
        skijaState.apply {
            clear()
            renderTarget = when (api) {
                GraphicsApi.OPENGL -> {
                    val gl = OpenGLApi.instance
                    val fbId = gl.glGetIntegerv(gl.GL_DRAW_FRAMEBUFFER_BINDING)
                    makeGLRenderTarget(
                        width,
                        height,
                        0,
                        8,
                        fbId,
                        FramebufferFormat.GR_GL_RGBA8
                    )
                }
                GraphicsApi.METAL -> makeMetalRenderTarget(
                    width,
                    height,
                    0
                )
                else -> TODO("Unsupported yet")
            }
        }
    }

    private fun initSurface() {
        skijaState.apply {
            surface = Surface.makeFromBackendRenderTarget(
                context,
                renderTarget,
                SurfaceOrigin.BOTTOM_LEFT,
                SurfaceColorFormat.RGBA_8888,
                ColorSpace.getSRGB()
            )
            canvas = surface!!.canvas
        }
    }
}
