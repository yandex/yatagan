package com.yandex.yatagan.lang

/**
 * A sealed marker interface for an entity that can be called.
 */
public interface Callable : HasPlatformModel {

    /**
     * Parameters required to call this callable.
     */
    public val parameters: Sequence<Parameter>

    public fun <T> accept(visitor: Visitor<T>): T

    public interface Visitor<T> {
        public fun visitMethod(method: Method): T
        public fun visitConstructor(constructor: Constructor): T
    }
}