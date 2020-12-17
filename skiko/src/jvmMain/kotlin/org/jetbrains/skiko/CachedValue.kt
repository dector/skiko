package org.jetbrains.skiko

internal class CachedValue<V : Any>(
    private val get: () -> V
) {
    @Volatile
    private var cached: V? = null

    fun reset() {
        cached = null
    }

    val value get(): V {
        var cached = cached
        if (cached == null) {
            cached = get()
            this.cached = cached
        }
        return cached
    }
}