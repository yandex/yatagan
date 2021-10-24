package com.yandex.daggerlite.jap.lang

import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.memoize
import com.yandex.daggerlite.generator.lang.ClassNameModel
import com.yandex.daggerlite.generator.lang.NamedTypeLangModel
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror
import kotlin.LazyThreadSafetyMode.NONE

internal class JavaxTypeImpl(
    private val impl: TypeMirror,
) : NamedTypeLangModel() {
    override val name: ClassNameModel by lazy(NONE) {
        ClassNameModel(impl)
    }
    override val declaration: TypeDeclarationLangModel by lazy(NONE) {
        // FIXME: it may not be a class, so provide another implementations for primitive types, arrays, etc.
        JavaxTypeDeclarationImpl(impl.asTypeElement())
    }
    override val typeArguments: Sequence<TypeLangModel> = (impl as DeclaredType).typeArguments
        .asSequence().map(::JavaxTypeImpl).memoize()
}