package SkijaInjectSample

import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.JFrame
import javax.swing.WindowConstants
import org.jetbrains.skiko.Library
import org.jetbrains.skiko.MetalLayer
import org.jetbrains.skiko.SkiaRenderer
import org.jetbrains.skija.BackendRenderTarget
import org.jetbrains.skija.Canvas
import org.jetbrains.skija.ColorSpace
import org.jetbrains.skija.Context
import org.jetbrains.skija.FramebufferFormat
import org.jetbrains.skija.Surface
import org.jetbrains.skija.SurfaceColorFormat
import org.jetbrains.skija.SurfaceOrigin

// gn gen out/Release-x64 --args="is_debug=false is_official_build=true skia_use_metal=true skia_use_system_expat=false skia_use_system_icu=false skia_use_system_libjpeg_turbo=false skia_use_system_libpng=false skia_use_system_libwebp=false skia_use_system_zlib=false skia_use_sfntly=false skia_use_freetype=true skia_use_harfbuzz=true skia_pdf_subset_harfbuzz=true skia_use_system_freetype2=false skia_use_system_harfbuzz=false target_cpu=\"x64\" extra_cflags=[\"-stdlib=libc++\", \"-mmacosx-version-min=10.13\"] extra_cflags_cc=[\"-frtti\"]"

fun metalLayer() {
    val window = MetalWindow()
    window.defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE

    val state = State()
    state.text = "AWTMetalClocks"

    var mouseX = 0
    var mouseY = 0
    window.layer.renderer = Renderer {
        renderer, w, h -> displayScene(renderer, w, h, mouseX, mouseY, state)
    }

    window.layer.addMouseMotionListener(object : MouseMotionAdapter() {
        override fun mouseMoved(event: MouseEvent) {
            mouseX = event.x
            mouseY = event.y
            window.display()
        }
    })

    window.setVisible(true)
    window.setSize(800, 600)
}

open class SkiaMtlLayer : MetalLayer() {
    var renderer: SkiaRenderer? = null

    private val skijaState = SkijaState()
    protected var inited: Boolean = false

    fun reinit() {
        inited = false
    }

    override fun disposeLayer() {
        super.disposeLayer()
    }

    public override fun draw() {
        if (!inited) {
            if (skijaState.context == null) {
                skijaState.context = Context.makeMetal(nativeSurface)
                initSkija()
            }
            inited = true
        }
        skijaState.apply {
            canvas!!.clear(-1)
            renderer?.onRender(canvas!!, width, height)
            context!!.flush()
        }
    }

    private fun initSkija() {
        val dpi = contentScale
        initRenderTarget(dpi)
        initSurface()
        scaleCanvas(dpi)
    }

    private fun initRenderTarget(dpi: Float) {
        skijaState.apply {
            clear()
            // renderTarget = BackendRenderTarget.makeGL(
            //     (width * dpi).toInt(),
            //     (height * dpi).toInt(),
            //     0,
            //     8,
            //     fbId,
            //     FramebufferFormat.GR_GL_RGBA8
            // )
        }
    }

    private fun initSurface() {
        skijaState.apply {
            surface = Surface.makeFromCAMetalLayer(
                context,
                nativeSurface,
                SurfaceOrigin.BOTTOM_LEFT,
                8,
                SurfaceColorFormat.RGBA_8888,
                ColorSpace.getSRGB()
            )
            canvas = surface!!.canvas
        }
    }

    protected open fun scaleCanvas(dpi: Float) {
        skijaState.apply {
            canvas!!.scale(dpi, dpi)
        }
    }
}

open class MetalWindow : JFrame() {
    companion object {
        init {
            Library.load("/", "skiko")
        }
    }

    val layer: SkiaMtlLayer = SkiaMtlLayer()

    init {
        contentPane.add(layer)

        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                layer.reinit()
            }
        })
    }

    fun display() {
        layer.display()
    }
}

private class SkijaState {
    var context: Context? = null
    var renderTarget: BackendRenderTarget? = null
    var surface: Surface? = null
    var canvas: Canvas? = null

    fun clear() {
        surface?.close()
        renderTarget?.close()
    }
}