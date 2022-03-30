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
import kotlin.LazyThreadSafetyMode.NONE

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
                val declaration = typeRef.resolve().getNonAliasDeclaration() ?: continue
                queue += declaration.superTypes
                if (declaration.classKind == ClassKind.INTERFACE) {
                    yield(KspTypeImpl(typeRef))
                }
            }
        }
    }.memoize()

    override val constructors: Sequence<ConstructorLangModel> by lazy(NONE) {
        impl.getConstructors()
            .filter { !it.isPrivate() }
            .map { ConstructorImpl(impl = it) }
            .memoize()
    }

    override val functions: Sequence<FunctionLangModel> by lazy(NONE) {
        sequenceOf(
            this.impl.allNonPrivateFunctions().map { KspFunctionImpl(owner = this, impl = it) },
            this.impl.allNonPrivateProperties().flatMap {
                explodeProperty(
                    property = it,
                    owner = this,
                )
            },
        ).flatten().memoize()
    }

    private fun explodeProperty(
        property: KSPropertyDeclaration,
        owner: KspTypeDeclarationImpl,
    ): Sequence<FunctionLangModel> = sequenceOf(
        property.getter?.let { getter ->
            KspFunctionPropertyGetterImpl(owner = owner, getter = getter)
        },
        property.setter?.let { setter ->
            if (Modifier.PRIVATE !in setter.modifiers && Modifier.PROTECTED !in setter.modifiers) {
                KspFunctionPropertySetterImpl(owner = owner, setter = setter)
            } else null
        },
    ).filterNotNull()

    override val fields: Sequence<FieldLangModel> by lazy(NONE) {
        impl.getDeclaredProperties()
            .filter { !it.isPrivate() && it.isField }
            .map { KspFieldImpl(it, this) }.memoize()
    }

    override val nestedClasses: Sequence<TypeDeclarationLangModel> by lazy(NONE) {
        impl.declarations
        .filterIsInstance<KSClassDeclaration>()
        .filter { !it.isPrivate() }
        .map { Factory(KspTypeImpl(it.asType(emptyList()))) }
        .memoize()
    }

    override val companionObjectDeclaration: TypeDeclarationLangModel? by lazy(NONE) {
        impl.getCompanionObject()?.let { companion ->
            KspTypeDeclarationImpl(KspTypeImpl(companion.asType(emptyList())))
        }
    }

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