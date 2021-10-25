package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.memoize
import com.yandex.daggerlite.generator.lang.ClassNameModel
import com.yandex.daggerlite.generator.lang.CompileTimeTypeDeclarationLangModel
import com.yandex.daggerlite.generator.lang.NamedTypeLangModel

internal class KspTypeDeclarationImpl(
    override val impl: KSClassDeclaration,
) : KspAnnotatedImpl(), CompileTimeTypeDeclarationLangModel {
    override val isAbstract: Boolean
        get() = impl.isAbstract()
    override val isKotlinObject: Boolean
        get() = impl.isObject

    override val qualifiedName: String
        get() = impl.qualifiedName!!.asString()

    override val constructors: Sequence<FunctionLangModel> =
        impl.getConstructors().map {
            KspFunctionImpl(owner = this, impl = it, isConstructor = true)
        }.memoize()

    override val allPublicFunctions: Sequence<FunctionLangModel> = sequenceOf(
        impl.getDeclaredProperties().map { KspFunctionPropertyGetterImpl(owner = this, impl = it) },
        impl.getDeclaredFunctions().map { KspFunctionImpl(owner = this, impl = it) },
    ).flatten().memoize()

    override val nestedInterfaces: Sequence<TypeDeclarationLangModel> = impl.declarations
        .filterIsInstance<KSClassDeclaration>()
        .filter { it.classKind == ClassKind.INTERFACE }
        .map(::KspTypeDeclarationImpl)
        .memoize()

    override fun asType(): TypeLangModel {
        require(impl.typeParameters.isEmpty())
        return NoArgsTypeImpl()
    }

    private inner class NoArgsTypeImpl : NamedTypeLangModel() {
        override val declaration: TypeDeclarationLangModel
            get() = this@KspTypeDeclarationImpl
        override val typeArguments: Sequence<Nothing>
            get() = emptySequence()
        override val name: ClassNameModel
            get() = ClassNameModel(impl)
    }

    override fun equals(other: Any?): Boolean {
        // MAYBE: remove this if cache is implemented
        return this === other || (other is KspTypeDeclarationImpl && impl == other.impl)
    }

    override fun hashCode() = impl.hashCode()
}