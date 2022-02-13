package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.isPrivate
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
import com.yandex.daggerlite.core.lang.KotlinObjectKind
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.CtAnnotationLangModel
import com.yandex.daggerlite.generator.lang.CtTypeDeclarationLangModel

internal class KspTypeDeclarationImpl private constructor(
    val type: KspTypeImpl,
) : CtTypeDeclarationLangModel() {
    private val impl: KSClassDeclaration = type.impl.declaration as KSClassDeclaration

    override val annotations: Sequence<CtAnnotationLangModel> = annotationsFrom(impl)

    override val isInterface: Boolean
        get() = impl.classKind == ClassKind.INTERFACE

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

    override val enclosingType: TypeDeclarationLangModel?
        get() = (impl.parentDeclaration as? KSClassDeclaration)?.let { Factory(KspTypeImpl(it.asType(emptyList()))) }

    override val implementedInterfaces: Sequence<TypeLangModel> = sequence {
        val queue = ArrayDeque<Sequence<KSTypeReference>>()
        queue += impl.superTypes
        while (queue.isNotEmpty()) {
            for (typeRef in queue.removeFirst()) {
                val declaration = typeRef.resolve().declaration as KSClassDeclaration
                queue += declaration.superTypes
                if (declaration.classKind == ClassKind.INTERFACE) {
                    yield(KspTypeImpl(typeRef))
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
            val companionDeclaration = KspTypeDeclarationImpl(KspTypeImpl(companion.asType(emptyList())))
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
        impl.getDeclaredProperties()
            .filter { it.isField && !it.isPrivate() }
            .map { KspFieldImpl(it, this) }.memoize()

    override val nestedClasses: Sequence<TypeDeclarationLangModel> = impl.declarations
        .filterIsInstance<KSClassDeclaration>()
        .map { Factory(KspTypeImpl(it.asType(emptyList()))) }
        .memoize()

    override fun asType(): TypeLangModel {
        return type
    }

    override val platformModel: KSClassDeclaration
        get() = impl

    companion object Factory : ObjectCache<KspTypeImpl, KspTypeDeclarationImpl>() {
        operator fun invoke(impl: KspTypeImpl) =
            createCached(impl, ::KspTypeDeclarationImpl)
    }

    private inner class ConstructorImpl(
        private val impl: KSFunctionDeclaration,
    ) : ConstructorLangModel {
        private val jvmSignature = JvmMethodSignature(impl)

        override val annotations: Sequence<AnnotationLangModel> = annotationsFrom(impl)
        override val constructee: TypeDeclarationLangModel get() = this@KspTypeDeclarationImpl
        override val parameters: Sequence<ParameterLangModel> = parametersSequenceFor(
            declaration = impl,
            containing = type.impl,
            jvmMethodSignature = jvmSignature,
        )
        override val platformModel: KSFunctionDeclaration get() = impl
    }
}