package com.yandex.yatagan.lang.scope

import com.yandex.yatagan.base.api.Extensible
import com.yandex.yatagan.base.api.Internal

/**
 * A scope that encapsulates the lang modeling session and everything around it.
 */
public interface LexicalScope {
    /**
     * Extension container related to the scope
     */
    public val ext: Extensions

    /**
     * A "constructor" operator for entities that have [FactoryKey]-implementing companion object.
     * Creates and instance of [R] using the receiver's cached [FactoryKey.factory].
     *
     * @receiver a factory key that is used to create and cache [FactoryKey.factory].
     * @param input a factory input
     * @return created instance
     *
     * @see FactoryKey
     */
    @Internal
    public operator fun <T, R> FactoryKey<T, R>.invoke(input: T): R

    /**
     * Extension container interface.
     * Can be used to obtain [langFactory][com.yandex.yatagan.lang.langFactory].
     *
     * @see com.yandex.yatagan.lang.LangModelFactory
     */
    public interface Extensions : Extensible<Extensions>
}
