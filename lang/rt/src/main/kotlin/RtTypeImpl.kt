package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.lang.common.NoDeclaration
import com.yandex.daggerlite.lang.common.TypeLangModelBase
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

internal class RtTypeImpl private constructor(
    val impl: Type,
) : TypeLangModelBase() {

    override val declaration: TypeDeclarationLangModel by lazy {
        if (impl.tryAsClass() != null) RtTypeDeclarationImpl(this) else NoDeclaration(this)
    }

    override val typeArguments: List<TypeLangModel> by lazy {
        when (impl) {
            is ParameterizedType -> impl.actualTypeArguments.map { type ->
                Factory(when(type) {
                    is WildcardType -> {
                        type.lowerBounds.singleOrNull() ?: type.upperBounds.singleOrNull() ?: Any::class.java
                    }
                    else -> type
                })
            }
            else -> emptyList()
        }
    }

    override val isVoid: Boolean
        get() = impl.tryAsClass() == Void.TYPE

    override fun asBoxed(): TypeLangModel {
        return Factory(when(impl) {
            is Class<*> -> impl.boxed()
            else -> impl
        })
    }

    override fun isAssignableFrom(another: TypeLangModel): Boolean {
        return when (another) {
            is RtTypeImpl -> impl.isAssignableFrom(another.impl)
            else -> false
        }
    }

    override fun toString(): String = impl.formatString()

    companion object Factory : ObjectCache<TypeEquivalenceWrapper, RtTypeImpl>() {
        operator fun invoke(type: Type): RtTypeImpl {
            return createCached(type.equivalence()) { RtTypeImpl(type) }
        }
    }
}
