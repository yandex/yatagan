package com.yandex.daggerlite.lang.jap

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.lang.Type
import com.yandex.daggerlite.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.lang.common.NoDeclaration
import com.yandex.daggerlite.lang.compiled.CtType
import com.yandex.daggerlite.lang.compiled.CtTypeNameModel
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

internal class JavaxTypeImpl private constructor(
    val impl: TypeMirror,
) : CtType() {
    override val nameModel: CtTypeNameModel by lazy { CtTypeNameModel(impl) }

    override val declaration: TypeDeclarationLangModel by lazy {
        if (impl.kind == TypeKind.DECLARED) {
            JavaxTypeDeclarationImpl(impl.asDeclaredType())
        } else NoDeclaration(this)
    }

    override val isVoid: Boolean
        get() = impl.kind == TypeKind.VOID

    override fun asBoxed(): Type {
        return Factory(if (impl.kind.isPrimitive) {
            Utils.types.boxedClass(impl.asPrimitiveType()).asType()
        } else impl)
    }

    override val typeArguments: List<Type> by lazy {
        when (impl.kind) {
            TypeKind.DECLARED -> impl.asDeclaredType().typeArguments.map { type ->
                Factory(when(type.kind) {
                    TypeKind.WILDCARD -> type.asWildCardType().let {
                        it.extendsBound ?: it.superBound ?: Utils.objectType.asType()
                    }
                    else -> type
                })
            }
            else -> emptyList()
        }
    }

    override fun isAssignableFrom(another: Type): Boolean {
        return when (another) {
            is JavaxTypeImpl -> Utils.types.isAssignable(another.impl, impl)
            else -> false
        }
    }

    companion object Factory : ObjectCache<TypeMirrorEquivalence, JavaxTypeImpl>() {
        operator fun invoke(impl: TypeMirror): JavaxTypeImpl {
            return createCached(TypeMirrorEquivalence(impl)) { JavaxTypeImpl(impl = impl) }
        }
    }
}