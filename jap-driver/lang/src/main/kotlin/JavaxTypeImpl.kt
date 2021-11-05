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
    private val impl: PrimitiveType,
) : CtTypeLangModel() {
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
    private val impl: DeclaredType,
    override val declaration: TypeDeclarationLangModel,
    override val typeArguments: List<TypeLangModel>,
) : CtTypeLangModel() {
    override val name: CtTypeNameModel by lazy(NONE) { CtTypeNameModel(impl) }

    companion object Factory : BiObjectCache<TypeDeclarationLangModel, List<TypeLangModel>, JavaxDeclaredTypeImpl>() {
        operator fun invoke(impl: DeclaredType): JavaxDeclaredTypeImpl {
            return createCached(
                key1 = JavaxTypeDeclarationImpl(impl.asTypeElement()),
                key2 = impl.typeArguments.map(::CtTypeLangModel),
            ) { k1, k2 ->
                JavaxDeclaredTypeImpl(impl = impl, declaration = k1, typeArguments = k2)
            }
        }
    }
}

internal fun CtTypeLangModel(impl: TypeMirror): CtTypeLangModel = when {
    impl.kind == TypeKind.DECLARED -> JavaxDeclaredTypeImpl(impl.asDeclaredType())
    impl.kind.isPrimitive -> JavaxPrimitiveTypeImpl(impl.asPrimitiveType())
    else -> throw RuntimeException("Unexpected type: $impl")
}

