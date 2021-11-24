package com.yandex.daggerlite.jap.lang

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.lang.FieldLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.CtTypeDeclarationLangModel
import kotlinx.metadata.KmClass
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import kotlin.LazyThreadSafetyMode.NONE

internal class JavaxTypeDeclarationImpl private constructor(
    val impl: TypeElement,
) : JavaxAnnotatedLangModel by JavaxAnnotatedImpl(impl), CtTypeDeclarationLangModel() {
    private val kotlinClass: KmClass? by lazy(NONE) {
        impl.obtainKotlinClassIfApplicable()
    }

    override val isAbstract: Boolean
        get() = impl.isAbstract

    override val isKotlinObject: Boolean
        get() = kotlinClass?.isObject == true

    override val qualifiedName: String
        get() = impl.qualifiedName.toString()

    override val implementedInterfaces: Sequence<TypeLangModel> = impl.allImplementedInterfaces()
        .map { JavaxTypeImpl(it) }
        .memoize()

    override val constructors: Sequence<FunctionLangModel> = impl.enclosedElements
        .asSequence()
        .filter { it.kind == ElementKind.CONSTRUCTOR }
        .map {
            JavaxFunctionImpl(owner = this, impl = it.asExecutableElement())
        }.memoize()

    override val allPublicFunctions: Sequence<FunctionLangModel> = sequence {
        val owner = this@JavaxTypeDeclarationImpl
        yieldAll(impl.allMethods(Utils.types, Utils.elements).map {
            JavaxFunctionImpl(
                owner = owner,
                impl = it,
                isFromCompanionObject = false,
            )
        })
        kotlinClass?.companionObject?.let { companionName: String ->
            val companionClass = checkNotNull(impl.enclosedElements.find {
                it.kind == ElementKind.CLASS && it.simpleName.contentEquals(companionName)
            }) { "Inconsistent metadata interpreting: No companion $companionName detected in $impl" }
            companionClass.asTypeElement().allMethods(Utils.types, Utils.elements)
                .filter {
                    // Such methods already have a truly static counterpart so skip them.
                    !it.isAnnotatedWith<JvmStatic>()
                }.forEach {
                    yield(JavaxFunctionImpl(
                        owner = owner,
                        impl = it,
                        isFromCompanionObject = true,
                    ))
                }
        }
    }.memoize()

    override val allPublicFields: Sequence<FieldLangModel> = impl.enclosedElements.asSequence()
        .filter { it.kind == ElementKind.FIELD && it.isPublic }
        .map { JavaxFieldImpl(owner = this, impl = it.asVariableElement()) }
        .memoize()

    override val nestedInterfaces: Sequence<TypeDeclarationLangModel> = impl.enclosedElements
        .asSequence()
        .filter { it.kind == ElementKind.INTERFACE }
        .map { Factory(it.asTypeElement()) }
        .memoize()

    override fun asType(): TypeLangModel {
        require(impl.typeParameters.isEmpty())
        return JavaxTypeImpl(impl.asType())
    }

    companion object Factory : ObjectCache<TypeElement, JavaxTypeDeclarationImpl>() {
        operator fun invoke(impl: TypeElement) = createCached(impl, ::JavaxTypeDeclarationImpl)
    }
}