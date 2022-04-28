package com.yandex.daggerlite.core.lang

/**
 * Kotlin object classification
 */
enum class KotlinObjectKind {
    /**
     * Kotlin `object` singleton. Does not represent companion objects, they have a separate kind - [Companion]
     */
    Object,

    /**
     * Kotlin `companion object`.
     */
    Companion,
}
