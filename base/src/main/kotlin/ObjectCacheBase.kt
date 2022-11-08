package com.yandex.yatagan.base

/**
 * A base class for [ObjectCache] and its composite-key variants.
 *
 * @see ObjectCache
 * @see BiObjectCache
 * @see ObjectCacheRegistry
 */
abstract class ObjectCacheBase {
    internal abstract fun clear()
}