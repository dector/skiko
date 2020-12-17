package org.jetbrains.skiko.redrawer

import org.jetbrains.skiko.HardwareLayer
import javax.swing.SwingUtilities.convertPoint
import javax.swing.SwingUtilities.getRootPane

@Suppress("unused")
internal class MacOsRedrawer(
    private val layer: HardwareLayer
) : HardwareLayer.Redrawer {
    override fun dispose() {

    }

    override fun needRedraw() {

    }

    private val absoluteX: Int
        get() = convertPoint(layer, layer.x, layer.y, getRootPane(layer)).x

    private val absoluteY: Int
        get() = convertPoint(layer, layer.x, layer.y, getRootPane(layer)).y
}