package com.yandex.yatagan.lang.common.scope

import com.yandex.yatagan.base.ExtensibleImpl
import com.yandex.yatagan.lang.scope.FactoryKey
import com.yandex.yatagan.lang.scope.LexicalScope

abstract class LexicalScopeBase : LexicalScope {
    override val ext: LexicalScope.Extensions = ExtensionsImpl()

    override operator fun <T, R> FactoryKey<T, R>.invoke(input: T): R {
        return ext.getOrPut(this) { factory() }(input)
    }

    private class ExtensionsImpl : LexicalScope.Extensions, ExtensibleImpl<LexicalScope.Extensions>()
}
