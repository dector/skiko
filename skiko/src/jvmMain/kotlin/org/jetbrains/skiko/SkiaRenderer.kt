package org.jetbrains.skiko

import org.jetbrains.skija.Canvas

interface SkiaRenderer {
    suspend fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long)
}