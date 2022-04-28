package com.yandex.daggerlite.core.lang

/**
 * A trait for models that encodes a language construct's accessibility.
 */
interface Accessible {
    /**
     * `true` is the entity is practically accessible from any package or module. `false` otherwise.
     *
     * NOTE: Kotlin's `internal` is presumed accessible, as it compiles into Java's `public` and technically can be
     * accessed from another module by its mangled name. Mangled name can change across compilation configurations, yet
     * it's fine with code generation and/or reflection.
     */
    val isEffectivelyPublic: Boolean
}