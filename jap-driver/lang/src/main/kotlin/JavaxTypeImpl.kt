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
import kotlin.LazyThreadSafetyMode.NONE

private class JavaxPrimitiveTypeImpl(
    impl: PrimitiveType,
) : NamedTypeLangModel() {
    override val name: ClassNameModel by lazy(NONE) { ClassNameModel(impl) }
    override val declaration: TypeDeclarationLangModel =
        JavaxTypeDeclarationImpl(Utils.types.boxedClass(impl))
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
    impl.kind == TypeKind.DECLARED -> JavaxDeclaredTypeImpl(impl as DeclaredType)
    impl.kind.isPrimitive -> JavaxPrimitiveTypeImpl(impl as PrimitiveType)
    else -> throw RuntimeException("Unexpected type: $impl")
}

