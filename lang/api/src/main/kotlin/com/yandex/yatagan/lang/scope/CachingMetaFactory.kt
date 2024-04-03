package com.yandex.yatagan.lang.scope

import com.yandex.yatagan.base.api.Extensible
import com.yandex.yatagan.base.api.Internal

@Internal
public interface CachingMetaFactory {
    public fun <T, R> createFactory(delegate: LexicalScope.(T) -> R): LexicalScope.(T) -> R

    @Internal
    public companion object : Extensible.Key<CachingMetaFactory, LexicalScope.Extensions>
}