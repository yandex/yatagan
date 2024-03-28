package com.yandex.yatagan.lang.scope

import com.yandex.yatagan.base.api.Extensible
import com.yandex.yatagan.base.api.Internal

@Internal
public interface FactoryKey<T, R> : Extensible.Key<LexicalScope.(T) -> R, LexicalScope.Extensions> {
    public fun LexicalScope.factory(): LexicalScope.(T) -> R
}