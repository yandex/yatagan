package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.lang.FieldLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.memoize
import com.yandex.daggerlite.generator.lang.CompileTimeAnnotationLangModel
import com.yandex.daggerlite.generator.lang.CompileTimeTypeDeclarationLangModel

internal class KspTypeDeclarationImpl private constructor(
    private val impl: KSClassDeclaration,
) : CompileTimeTypeDeclarationLangModel() {
    override val annotations: Sequence<CompileTimeAnnotationLangModel> = annotationsFrom(impl)

    override val isAbstract: Boolean
        get() = impl.isAbstract()

    override val isKotlinObject: Boolean
        get() = impl.isObject

    override val qualifiedName: String
        get() = impl.qualifiedName!!.asString()

    override val constructors: Sequence<FunctionLangModel> =
        impl.getConstructors().map {
            KspFunctionImpl(owner = this, impl = it)
        }.memoize()

    override val allPublicFunctions: Sequence<FunctionLangModel> = sequence {
        val owner = this@KspTypeDeclarationImpl
        yieldAll(impl.allPublicFunctions().map {
            KspFunctionImpl(owner = owner, impl = it, isFromCompanionObject = false)
        })
        yieldAll(impl.allPublicProperties().map {
            KspFunctionPropertyGetterImpl(owner = owner, impl = it, isFromCompanionObject = false)
        })
        impl.getCompanionObject()?.let { companion ->
            yieldAll(companion.allPublicFunctions().map {
                KspFunctionImpl(owner = owner, impl = it, isFromCompanionObject = true)
            })
            yieldAll(companion.allPublicProperties().map {
                KspFunctionPropertyGetterImpl(owner = owner, impl = it, isFromCompanionObject = true)
            })
        }
    }.memoize()

    override val allPublicFields: Sequence<FieldLangModel> =
        impl.getDeclaredProperties().filter(KSPropertyDeclaration::isField).map { KspFieldImpl(it) }.memoize()

    override val nestedInterfaces: Sequence<TypeDeclarationLangModel> = impl.declarations
        .filterIsInstance<KSClassDeclaration>()
        .filter { it.classKind == ClassKind.INTERFACE }
        .map(Factory::invoke)
        .memoize()

    override fun asType(): TypeLangModel {
        require(impl.typeParameters.isEmpty())
        return KspTypeImpl(impl.asType(emptyList()))
    }

    companion object Factory : ObjectCache<KSClassDeclaration, KspTypeDeclarationImpl>() {
        operator fun invoke(impl: KSClassDeclaration) = createCached(impl, ::KspTypeDeclarationImpl)
    }
}