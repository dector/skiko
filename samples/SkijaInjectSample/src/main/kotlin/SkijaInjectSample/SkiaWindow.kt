package SkijaInjectSample

import org.jetbrains.skiko.SkiaLayer
import javax.swing.JFrame

open class SkiaWindow : JFrame() {
    val layer = SkiaLayer()

    init {
        contentPane.add(layer)
    }

    fun needRedraw() {
        layer.needRedraw()
    }

    override fun dispose() {
        super.dispose()
        layer.dispose()
    }
}
