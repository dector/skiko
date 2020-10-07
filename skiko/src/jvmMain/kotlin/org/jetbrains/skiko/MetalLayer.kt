package org.jetbrains.skiko

import org.jetbrains.skija.Context
import org.jetbrains.skija.impl.RefCnt;
import org.jetbrains.skija.impl.Stats;
import java.awt.Graphics
import java.awt.Canvas

open class MetalLayer : Canvas() {
    override fun paint(g: Graphics) {
        display()
    }
    open fun display() {
        this.updateLayer()
        this.redrawLayer()
    }
    open fun draw() {}
    external open fun redrawLayer()
    external open fun updateLayer()
    external open fun disposeLayer()
    val windowHandle: Long
        external get
    val contentScale: Float
        external get
    val nativeSurface: Long
        external get
}
