package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Modifier
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.ConstructorLangModel
import com.yandex.daggerlite.core.lang.FieldLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.CtAnnotationLangModel
import com.yandex.daggerlite.generator.lang.CtTypeDeclarationLangModel

internal class KspTypeDeclarationImpl private constructor(
    private val impl: KSClassDeclaration,
) : CtTypeDeclarationLangModel() {
    override val annotations: Sequence<CtAnnotationLangModel> = annotationsFrom(impl)

    override val isAbstract: Boolean
        get() = impl.isAbstract()

    override val isKotlinObject: Boolean
        get() = impl.isObject

    override val qualifiedName: String
        get() = impl.qualifiedName!!.asString()

    override val implementedInterfaces: Sequence<TypeLangModel> = sequence {
        val queue = ArrayDeque<Sequence<KSTypeReference>>()
        queue += impl.superTypes
        while (queue.isNotEmpty()) {
            for (typeRef in queue.removeFirst()) {
                val type = typeRef.resolve()
                val declaration = type.declaration as KSClassDeclaration
                queue += declaration.superTypes
                if (declaration.classKind == ClassKind.INTERFACE) {
                    yield(KspTypeImpl(type))
                }
            }
        }
    }.memoize()

    override val constructors: Sequence<ConstructorLangModel> =
        impl.getConstructors().map {
            ConstructorImpl(impl = it)
        }.memoize()

    override val allPublicFunctions: Sequence<FunctionLangModel> = sequence {
        val owner = this@KspTypeDeclarationImpl
        yieldAll(impl.allPublicFunctions().map {
            KspFunctionImpl(owner = owner, impl = it, isFromCompanionObject = false)
        })
        impl.allPublicProperties().forEach {
            explodeProperty(it)
        }
        impl.getCompanionObject()?.let { companion ->
            yieldAll(companion.allPublicFunctions().map {
                KspFunctionImpl(owner = owner, impl = it, isFromCompanionObject = true)
            })
            companion.allPublicProperties().forEach {
                explodeProperty(it, isFromCompanionObject = true)
            }
        }
    }.memoize()

    private suspend fun SequenceScope<FunctionLangModel>.explodeProperty(
        property: KSPropertyDeclaration,
        isFromCompanionObject: Boolean = true,
    ) {
        val owner = this@KspTypeDeclarationImpl
        property.getter?.let { getter ->
            yield(KspFunctionPropertyGetterImpl(owner = owner, getter = getter,
                isFromCompanionObject = isFromCompanionObject))
        }
        property.setter?.let { setter ->
            if (Modifier.PRIVATE !in setter.modifiers && Modifier.PROTECTED !in setter.modifiers) {
                yield(KspFunctionPropertySetterImpl(owner = owner, setter = setter,
                    isFromCompanionObject = isFromCompanionObject))
            }
        }
    }

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
        operator fun invoke(impl: KSClassDeclaration) =
            createCached(mapToJavaPlatformIfNeeded(impl), ::KspTypeDeclarationImpl)
    }

    private inner class ConstructorImpl(
        impl: KSFunctionDeclaration,
    ) : ConstructorLangModel {
        override val annotations: Sequence<AnnotationLangModel> = annotationsFrom(impl)
        override val constructee: TypeDeclarationLangModel get() = this@KspTypeDeclarationImpl
        override val parameters: Sequence<ParameterLangModel> = impl.parameters.asSequence()
            .map { KspParameterImpl(it) }.memoize()
    }
}