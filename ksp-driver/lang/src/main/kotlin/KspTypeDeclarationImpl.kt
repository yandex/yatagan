package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Modifier
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.ConstructorLangModel
import com.yandex.daggerlite.core.lang.FieldLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.KotlinObjectKind
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.CtAnnotationLangModel
import com.yandex.daggerlite.generator.lang.CtTypeDeclarationLangModel

internal class KspTypeDeclarationImpl private constructor(
    val type: KSType,
) : CtTypeDeclarationLangModel() {
    private val impl: KSClassDeclaration = type.declaration as KSClassDeclaration

    override val annotations: Sequence<CtAnnotationLangModel> = annotationsFrom(impl)

    override val isAbstract: Boolean
        get() = impl.isAbstract()

    override val kotlinObjectKind: KotlinObjectKind?
        get() = when {
            impl.isCompanionObject -> KotlinObjectKind.Companion
            impl.isObject -> KotlinObjectKind.Object
            else -> null
        }

    override val qualifiedName: String
        get() = impl.qualifiedName?.asString() ?: ""

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
            KspFunctionImpl(owner = owner, impl = it)
        })
        impl.allPublicProperties().forEach {
            explodeProperty(it, owner = this@KspTypeDeclarationImpl)
        }
        impl.getCompanionObject()?.let { companion ->
            val companionDeclaration = KspTypeDeclarationImpl(companion.asType(emptyList()))
            yieldAll(companion.allPublicFunctions().map {
                KspFunctionImpl(owner = companionDeclaration, impl = it)
            })
            companion.allPublicProperties().forEach {
                explodeProperty(it, owner = companionDeclaration)
            }
        }
    }.memoize()

    private suspend fun SequenceScope<FunctionLangModel>.explodeProperty(
        property: KSPropertyDeclaration,
        owner: KspTypeDeclarationImpl,
    ) {
        property.getter?.let { getter ->
            yield(KspFunctionPropertyGetterImpl(owner = owner, getter = getter))
        }
        property.setter?.let { setter ->
            if (Modifier.PRIVATE !in setter.modifiers && Modifier.PROTECTED !in setter.modifiers) {
                yield(KspFunctionPropertySetterImpl(owner = owner, setter = setter))
            }
        }
    }

    override val allPublicFields: Sequence<FieldLangModel> =
        impl.getDeclaredProperties().filter(KSPropertyDeclaration::isField).map { KspFieldImpl(it, this) }.memoize()

    override val nestedInterfaces: Sequence<TypeDeclarationLangModel> = impl.declarations
        .filterIsInstance<KSClassDeclaration>()
        .filter { it.classKind == ClassKind.INTERFACE }
        .map { Factory(it.asType(emptyList())) }
        .memoize()

    override fun asType(): TypeLangModel {
        return KspTypeImpl(type)
    }

    companion object Factory : ObjectCache<KSType, KspTypeDeclarationImpl>() {
        operator fun invoke(impl: KSType) =
            createCached(mapToJavaPlatformIfNeeded(impl), ::KspTypeDeclarationImpl)
    }

    private inner class ConstructorImpl(
        impl: KSFunctionDeclaration,
    ) : ConstructorLangModel {
        private val jvmSignature = JvmMethodSignature(impl)

        override val annotations: Sequence<AnnotationLangModel> = annotationsFrom(impl)
        override val constructee: TypeDeclarationLangModel get() = this@KspTypeDeclarationImpl
        override val parameters: Sequence<ParameterLangModel> = parametersSequenceFor(
            declaration = impl,
            containing = type,
            jvmMethodSignature = jvmSignature,
        )
    }
}