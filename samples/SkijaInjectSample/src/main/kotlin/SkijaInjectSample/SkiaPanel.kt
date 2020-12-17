package SkijaInjectSample

import org.jetbrains.skiko.ClipComponent
import org.jetbrains.skiko.SkiaLayer
import java.awt.Color
import java.awt.Component
import javax.swing.JLayeredPane

internal class SkiaPanel : JLayeredPane() {
    val layer = SkiaLayer()

    init {
        layout = null
        background = Color.white
    }

    override fun add(component: Component): Component {
        layer.clipComponents.add(ClipComponent(component))
        return super.add(component, Integer.valueOf(0))
    }

    override fun addNotify() {
        super.addNotify()
        super.add(layer, Integer.valueOf(10))
    }

    override fun removeNotify() {
        layer.dispose()
        super.removeNotify()
    }
}
