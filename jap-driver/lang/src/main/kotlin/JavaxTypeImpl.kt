package com.yandex.daggerlite.jap.lang

import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.memoize
import com.yandex.daggerlite.generator.lang.ClassNameModel
import com.yandex.daggerlite.generator.lang.NamedTypeLangModel
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.PrimitiveType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

// TODO: Нужно решить, насколько мы собираемся поддерживать примитивы и исходя из этого доделать.
//  Кажется легче всего примитивы передавать с `name` вида ClassNAmeModel("", int, emptyList()), а в генераторе
//  разбираться, стоит ли нам брать boxed тип.
private class JavaxPrimitiveTypeImpl(
    impl: PrimitiveType,
) : NamedTypeLangModel() {
    override val declaration: TypeDeclarationLangModel
    override val name: ClassNameModel
    init {
        val boxedType = Utils.types.boxedClass(impl)
        declaration = JavaxTypeDeclarationImpl(boxedType)
        name = ClassNameModel(boxedType)
    }
    override val typeArguments: Sequence<TypeLangModel> = emptySequence()
}

private class JavaxDeclaredTypeImpl(
    impl: DeclaredType,
    ) : NamedTypeLangModel() {
    override val name: ClassNameModel = ClassNameModel(impl)
    override val declaration: TypeDeclarationLangModel = JavaxTypeDeclarationImpl(impl.asTypeElement())
    override val typeArguments: Sequence<TypeLangModel> = impl.typeArguments
        .asSequence().map(::NamedTypeLangModel).memoize()
}

internal fun NamedTypeLangModel(impl: TypeMirror): NamedTypeLangModel = when {
    impl.kind == TypeKind.DECLARED -> JavaxDeclaredTypeImpl(impl.asDeclaredType())
    impl.kind.isPrimitive -> JavaxPrimitiveTypeImpl(impl.asPrimitiveType())
    else -> throw RuntimeException("Unexpected type: $impl")
}

