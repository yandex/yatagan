package com.yandex.daggerlite.jap.lang

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.lang.ConstructorLangModel
import com.yandex.daggerlite.core.lang.FieldLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel.PropertyAccessorKind.Getter
import com.yandex.daggerlite.core.lang.FunctionLangModel.PropertyAccessorKind.Setter
import com.yandex.daggerlite.core.lang.KotlinObjectKind
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.CtTypeDeclarationLangModel
import kotlinx.metadata.KmClass
import kotlinx.metadata.jvm.getterSignature
import kotlinx.metadata.jvm.setterSignature
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.NestingKind
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import kotlin.LazyThreadSafetyMode.NONE

internal class JavaxTypeDeclarationImpl private constructor(
    val type: DeclaredType,
) : JavaxAnnotatedLangModel by JavaxAnnotatedImpl(type.asTypeElement()), CtTypeDeclarationLangModel() {
    private val impl = type.asTypeElement()

    private val kotlinClass: KmClass? by lazy(NONE) {
        impl.obtainKotlinClassIfApplicable()
    }

    override val isEffectivelyPublic: Boolean
        get() = impl.isPublic

    override val isInterface: Boolean
        get() = impl.kind == ElementKind.INTERFACE

    override val isAbstract: Boolean
        get() = impl.isAbstract

    override val kotlinObjectKind: KotlinObjectKind?
        get() = kotlinClass?.let {
            when {
                it.isCompanionObject -> KotlinObjectKind.Companion
                it.isObject -> KotlinObjectKind.Object
                else -> null
            }
        }

    override val qualifiedName: String
        get() = impl.qualifiedName.toString()

    override val enclosingType: TypeDeclarationLangModel?
        get() = when (impl.nestingKind) {
            NestingKind.MEMBER -> Factory(impl.enclosingElement.asType().asDeclaredType())
            else -> null
        }

    override val implementedInterfaces: Sequence<TypeLangModel> by lazy(NONE) {
        impl.allImplementedInterfaces()
        .map { JavaxTypeImpl(it) }
        .memoize()
    }

    override val constructors: Sequence<ConstructorLangModel> by lazy(NONE) {
        impl.enclosedElements
        .asSequence()
        .filter { it.kind == ElementKind.CONSTRUCTOR && !it.isPrivate }
        .map {
            ConstructorImpl(impl = it.asExecutableElement())
        }.memoize()
    }

    override val functions: Sequence<FunctionLangModel> by lazy(NONE) {
        impl.allNonPrivateMethods()
        .run {
            when (kotlinObjectKind) {
                KotlinObjectKind.Companion -> filterNot {
                    // Such methods already have a truly static counterpart so skip them.
                    it.isAnnotatedWith<JvmStatic>()
                }
                else -> this
            }
        }
        .map {
            JavaxFunctionImpl(
                owner = this@JavaxTypeDeclarationImpl,
                impl = it,
            )
        }
    }

    override val fields: Sequence<FieldLangModel> by lazy(NONE) {
        impl.enclosedElements
        .asSequence()
        .filter { it.kind == ElementKind.FIELD && !it.isPrivate }
        .map { JavaxFieldImpl(owner = this, impl = it.asVariableElement()) }
        .memoize()
    }

    override val nestedClasses: Sequence<TypeDeclarationLangModel> by lazy(NONE) {
        impl.enclosedElements
        .asSequence()
        .filter {
            when (it.kind) {
                ElementKind.ENUM, ElementKind.CLASS,
                ElementKind.ANNOTATION_TYPE, ElementKind.INTERFACE,
                -> !it.isPrivate
                else -> false
            }
        }
        .map { Factory(it.asType().asDeclaredType()) }
        .memoize()
    }

    override val companionObjectDeclaration: TypeDeclarationLangModel? by lazy(NONE) {
        kotlinClass?.companionObject?.let { companionName: String ->
            val companionClass = checkNotNull(impl.enclosedElements.find {
                it.kind == ElementKind.CLASS && it.simpleName.contentEquals(companionName)
            }) { "Not reached: inconsistent metadata interpreting: No companion $companionName detected in $impl" }
            JavaxTypeDeclarationImpl(companionClass.asType().asDeclaredType())
        }
    }

    override fun asType(): TypeLangModel {
        return JavaxTypeImpl(type)
    }

    private val typeHierarchy: Sequence<JavaxTypeDeclarationImpl> by lazy(NONE) {
        sequence {
            val queue = ArrayList<JavaxTypeDeclarationImpl>(4)
            queue += this@JavaxTypeDeclarationImpl
            do {
                val declaration = queue.removeLast()
                yield(declaration)
                declaration.impl.superclass?.let { superClass ->
                    if (superClass.kind != TypeKind.NONE) {
                        queue += Factory(superClass.asDeclaredType())
                    }
                }
                for (superInterface in declaration.impl.interfaces) {
                    queue += Factory(superInterface.asDeclaredType())
                }
            } while (queue.isNotEmpty())
        }
    }

    private val kotlinPropertySetters by lazy(NONE) {
        buildMap {
            kotlinClass?.properties?.forEach { kmProperty ->
                kmProperty.setterSignature?.let { put(it, kmProperty) }
                kmProperty.getterSignature?.let { put(it, kmProperty) }
            }
        }
    }

    internal fun findKotlinPropertyAccessorFor(element: ExecutableElement): JavaxPropertyAccessorImpl? {
        val signature by lazy(NONE) {
            jvmSignatureOf(element = element, type = Utils.types.asMemberOf(type, element))
        }
        for (declaration in typeHierarchy) {
            val property = declaration.kotlinPropertySetters[signature] ?: continue
            return JavaxPropertyAccessorImpl(
                property = property,
                kind = if (property.setterSignature == signature) Setter else Getter,
            )
        }
        return null
    }

    override val platformModel: TypeElement get() = impl

    companion object Factory : ObjectCache<TypeMirrorEquivalence, JavaxTypeDeclarationImpl>() {
        operator fun invoke(
            impl: DeclaredType,
        ): JavaxTypeDeclarationImpl {
            return createCached(TypeMirrorEquivalence(impl)) {
                JavaxTypeDeclarationImpl(type = impl)
            }
        }
    }

    private inner class ConstructorImpl(
        impl: ExecutableElement,
    ) : ConstructorLangModel, JavaxAnnotatedImpl<ExecutableElement>(impl) {
        override val isEffectivelyPublic: Boolean get() = impl.isPublic
        override val constructee: TypeDeclarationLangModel get() = this@JavaxTypeDeclarationImpl
        override val parameters: Sequence<ParameterLangModel> = parametersSequenceFor(impl, type)
        override val platformModel: ExecutableElement get() = impl
    }
}