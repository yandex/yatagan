package com.yandex.daggerlite.jap.lang

import com.yandex.daggerlite.base.BiObjectCache
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.CtTypeLangModel
import com.yandex.daggerlite.generator.lang.CtTypeNameModel
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.PrimitiveType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import kotlin.LazyThreadSafetyMode.NONE

// TODO: Нужно решить, насколько мы собираемся поддерживать примитивы и исходя из этого доделать.
//  Кажется легче всего примитивы передавать с `name` вида ClassNAmeModel("", int, emptyList()), а в генераторе
//  разбираться, стоит ли нам брать boxed тип.
private class JavaxPrimitiveTypeImpl(
    override val impl: PrimitiveType,
) : CtTypeLangModel(), JavaxTypeImpl {
    override val declaration: TypeDeclarationLangModel by lazy(NONE) {
        JavaxTypeDeclarationImpl(Utils.types.boxedClass(impl))
    }
    override val name: CtTypeNameModel by lazy(NONE) {
        CtTypeNameModel(Utils.types.boxedClass(impl))
    }
    override val typeArguments: Collection<Nothing> = emptyList()
    override val isBoolean: Boolean = impl.kind == TypeKind.BOOLEAN

    override fun equals(other: Any?): Boolean {
        return this === other || (other is JavaxPrimitiveTypeImpl && impl.kind == other.impl.kind)
    }

    override fun hashCode() = impl.kind.hashCode()
}

private class JavaxDeclaredTypeImpl private constructor(
    override val impl: DeclaredType,
    override val declaration: TypeDeclarationLangModel,
    override val typeArguments: List<JavaxTypeImpl>,
) : CtTypeLangModel(), JavaxTypeImpl {
    override val name: CtTypeNameModel by lazy(NONE) { CtTypeNameModel(impl) }

    companion object Factory : BiObjectCache<TypeDeclarationLangModel, List<JavaxTypeImpl>, JavaxDeclaredTypeImpl>() {
        operator fun invoke(impl: DeclaredType): JavaxDeclaredTypeImpl {
            // MAYBE: If some bugs are encountered with equivalence here, use TypeMirrors.equivalence() from google.auto
            return createCached(
                key1 = JavaxTypeDeclarationImpl(impl.asTypeElement()),
                key2 = impl.typeArguments.map(::JavaxTypeImpl),
            ) { k1, k2 ->
                JavaxDeclaredTypeImpl(impl = impl, declaration = k1, typeArguments = k2)
            }
        }
    }
}

internal interface JavaxTypeImpl : TypeLangModel {
    val impl: TypeMirror
}

internal fun JavaxTypeImpl(impl: TypeMirror): JavaxTypeImpl = when {
    impl.kind == TypeKind.DECLARED -> JavaxDeclaredTypeImpl(impl.asDeclaredType())
    impl.kind == TypeKind.WILDCARD -> impl.asWildCardType().extendsBound?.let(::JavaxTypeImpl)
        ?: throw RuntimeException("Wildcard type with no `extends` bound: $impl")
    impl.kind.isPrimitive -> JavaxPrimitiveTypeImpl(impl.asPrimitiveType())
    else -> throw RuntimeException("Unexpected type: $impl")
}

