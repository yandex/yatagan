package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.lang.NoDeclaration
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
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

    override val typeArguments: Collection<TypeLangModel> by lazy {
        when (impl) {
            is ParameterizedType -> impl.actualTypeArguments.map { Factory(decay(it)) }
            else -> emptyList()
        }
    }

    override val isBoolean: Boolean
        get() = when (impl.tryAsClass()) {
            java.lang.Boolean.TYPE, java.lang.Boolean::class.java -> true
            else -> false
        }

    override val isVoid: Boolean
        get() = impl.tryAsClass() == Void.TYPE

    override fun decay(): TypeLangModel {
        return Factory(decay(impl))
    }

    override fun isAssignableFrom(another: TypeLangModel): Boolean {
        return when (another) {
            is RtTypeImpl -> {
                val thisClass = this.impl.tryAsClass() ?: return false
                val thatClass = another.impl.tryAsClass() ?: return false
                thisClass.boxed().isAssignableFrom(thatClass.boxed())
            }
            else -> false
        }
    }

    override fun toString(): String = impl.formatString()

    companion object Factory : ObjectCache<TypeEquivalenceWrapper, RtTypeImpl>() {
        operator fun invoke(type: Type): RtTypeImpl {
            return createCached(type.equivalence()) { RtTypeImpl(type) }
        }

        private fun decay(type: Type): Type {
            return when (type) {
                is WildcardType -> type.lowerBounds.singleOrNull() ?: type.upperBounds.singleOrNull()
                is Class<*> -> type.boxed()
                else -> null
            } ?: type
        }
    }
}
