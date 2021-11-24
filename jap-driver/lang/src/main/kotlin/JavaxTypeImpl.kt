package com.yandex.daggerlite.jap.lang

import com.google.auto.common.MoreTypes
import com.google.common.base.Equivalence
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.CtTypeLangModel
import com.yandex.daggerlite.generator.lang.CtTypeNameModel
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import kotlin.LazyThreadSafetyMode.NONE

internal class JavaxTypeImpl private constructor(
    val impl: DeclaredType,
) : CtTypeLangModel() {
    override val name: CtTypeNameModel by lazy(NONE) { CtTypeNameModel(impl) }

    override val declaration: TypeDeclarationLangModel by lazy(NONE) {
        JavaxTypeDeclarationImpl(impl.asTypeElement())
    }

    override val isBoolean: Boolean
        get() = impl.asTypeElement() == Utils.booleanType

    override val typeArguments: Collection<TypeLangModel> by lazy(NONE) {
        impl.asDeclaredType().typeArguments.map(Factory::invoke)
    }

    companion object Factory : ObjectCache<Equivalence.Wrapper<TypeMirror>, JavaxTypeImpl>() {
        operator fun invoke(impl: TypeMirror): JavaxTypeImpl {
            val declared = mapToDeclared(impl)
            return createCached(MoreTypes.equivalence().wrap(declared)) { JavaxTypeImpl(impl = declared) }
        }

        private fun mapToDeclared(impl: TypeMirror): DeclaredType {
            return when(impl.kind) {
                TypeKind.DECLARED -> impl
                TypeKind.WILDCARD -> {
                    // best effort
                    val declaredType = impl.asWildCardType().extendsBound
                        ?: throw IllegalStateException("Wildcard type with no `extends` bound: $impl")
                    declaredType
                }
                TypeKind.BOOLEAN, TypeKind.BYTE, TypeKind.SHORT, TypeKind.CHAR,
                TypeKind.INT, TypeKind.LONG, TypeKind.FLOAT, TypeKind.DOUBLE,
                -> {
                    Utils.types.boxedClass(impl.asPrimitiveType()).asType()
                }
                TypeKind.VOID -> Utils.voidType.asType()
                else -> throw IllegalArgumentException("Unexpected type $impl")
            }.asDeclaredType()
        }
    }
}