package com.yandex.daggerlite.jap.lang

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.lang.NoDeclaration
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.CtTypeLangModel
import com.yandex.daggerlite.generator.lang.CtTypeNameModel
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

internal class JavaxTypeImpl private constructor(
    val impl: TypeMirror,
) : CtTypeLangModel() {
    override val nameModel: CtTypeNameModel by lazy { CtTypeNameModel(impl) }

    override val declaration: TypeDeclarationLangModel by lazy {
        if (impl.kind == TypeKind.DECLARED) {
            JavaxTypeDeclarationImpl(impl.asDeclaredType())
        } else NoDeclaration(this)
    }

    override val isBoolean: Boolean
        get() = when (impl.kind) {
            TypeKind.BOOLEAN -> true
            TypeKind.DECLARED -> impl.asTypeElement() == Utils.booleanType
            else -> false
        }

    override val isVoid: Boolean
        get() = impl.kind == TypeKind.VOID

    override fun decay(): TypeLangModel {
        return Factory(decay(impl))
    }

    override val typeArguments: Collection<TypeLangModel> by lazy {
        when (impl.kind) {
            TypeKind.DECLARED -> impl.asDeclaredType().typeArguments.map { Factory(decay(it)) }
            else -> emptyList()
        }
    }

    override fun isAssignableFrom(another: TypeLangModel): Boolean {
        return when (another) {
            is JavaxTypeImpl -> Utils.types.isAssignable(another.impl, impl)
            else -> false
        }
    }

    companion object Factory : ObjectCache<TypeMirrorEquivalence, JavaxTypeImpl>() {
        operator fun invoke(impl: TypeMirror): JavaxTypeImpl {
            return createCached(TypeMirrorEquivalence(impl)) { JavaxTypeImpl(impl = impl) }
        }

        private fun decay(type: TypeMirror): TypeMirror {
            return when (type.kind) {
                TypeKind.WILDCARD -> type.asWildCardType().let { it.extendsBound ?: it.superBound }
                TypeKind.BOOLEAN, TypeKind.BYTE, TypeKind.SHORT, TypeKind.CHAR,
                TypeKind.INT, TypeKind.LONG, TypeKind.FLOAT, TypeKind.DOUBLE,
                -> Utils.types.boxedClass(type.asPrimitiveType()).asType()
                else -> null
            } ?: type
        }
    }
}