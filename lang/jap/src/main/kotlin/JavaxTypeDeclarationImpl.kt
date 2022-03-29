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
import kotlinx.metadata.KmProperty
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

    override val implementedInterfaces: Sequence<TypeLangModel> = impl.allImplementedInterfaces()
        .map { JavaxTypeImpl(it) }
        .memoize()

    override val constructors: Sequence<ConstructorLangModel> = impl.enclosedElements
        .asSequence()
        .filter { it.kind == ElementKind.CONSTRUCTOR }
        .map {
            ConstructorImpl(impl = it.asExecutableElement())
        }.memoize()

    override val functions: Sequence<FunctionLangModel> = sequence {
        val owner = this@JavaxTypeDeclarationImpl
        yieldAll(impl.allMethods(Utils.types, Utils.elements).map {
            JavaxFunctionImpl(
                owner = owner,
                impl = it,
            )
        })
        kotlinClass?.companionObject?.let { companionName: String ->
            val companionClass = checkNotNull(impl.enclosedElements.find {
                it.kind == ElementKind.CLASS && it.simpleName.contentEquals(companionName)
            }) { "Not reached: inconsistent metadata interpreting: No companion $companionName detected in $impl" }
            val companionType = JavaxTypeDeclarationImpl(companionClass.asType().asDeclaredType())
            companionClass.asTypeElement().allMethods(Utils.types, Utils.elements)
                .filter {
                    // Such methods already have a truly static counterpart so skip them.
                    !it.isAnnotatedWith<JvmStatic>()
                }.forEach {
                    yield(JavaxFunctionImpl(
                        owner = companionType,
                        impl = it,
                    ))
                }
        }
    }.memoize()

    override val fields: Sequence<FieldLangModel> = impl.enclosedElements.asSequence()
        .filter { it.kind == ElementKind.FIELD && it.isPublic }
        .map { JavaxFieldImpl(owner = this, impl = it.asVariableElement()) }
        .memoize()

    override val nestedClasses: Sequence<TypeDeclarationLangModel> = impl.enclosedElements
        .asSequence()
        .filter {
            when (it.kind) {
                ElementKind.ENUM, ElementKind.CLASS, ElementKind.ANNOTATION_TYPE, ElementKind.INTERFACE -> true
                else -> false
            }
        }
        .map { Factory(it.asType().asDeclaredType()) }
        .memoize()

    override fun asType(): TypeLangModel {
        return JavaxTypeImpl(type)
    }

    private val allKotlinProperties: Sequence<KmProperty> = sequence {
        val kotlinClass = kotlinClass ?: return@sequence
        for (property in kotlinClass.properties) if (!property.isOverride) {
            yield(property)
        }
        for (implemented in impl.interfaces) {
            yieldAll(Factory(implemented.asDeclaredType()).allKotlinProperties)
        }
        if (impl.superclass.kind != TypeKind.NONE) {
            yieldAll(Factory(impl.superclass.asDeclaredType()).allKotlinProperties)
        }
    }.memoize()

    internal fun findKotlinPropertyAccessorFor(element: ExecutableElement): JavaxPropertyAccessorImpl? {
        if (allKotlinProperties.none()) {
            return null
        }
        val signature = jvmSignatureOf(element = element, type = Utils.types.asMemberOf(type, element))
        allKotlinProperties.forEach { kmProperty ->
            when(signature) {
                kmProperty.setterSignature -> return JavaxPropertyAccessorImpl(property = kmProperty, kind = Setter)
                kmProperty.getterSignature -> return JavaxPropertyAccessorImpl(property = kmProperty, kind = Getter)
            }
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
        override val constructee: TypeDeclarationLangModel get() = this@JavaxTypeDeclarationImpl
        override val parameters: Sequence<ParameterLangModel> = parametersSequenceFor(impl, type)
        override val platformModel: ExecutableElement get() = impl
    }
}