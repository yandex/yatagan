package com.yandex.daggerlite.jap.lang

import com.yandex.daggerlite.core.lang.FieldLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.memoize
import com.yandex.daggerlite.generator.lang.CompileTimeTypeDeclarationLangModel
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement

internal class JavaxTypeDeclarationImpl(
    val impl: TypeElement,
) : JavaxAnnotatedLangModel by JavaxAnnotatedImpl(impl), CompileTimeTypeDeclarationLangModel() {
    override val isAbstract: Boolean
        get() = impl.isAbstract
    override val isKotlinObject: Boolean
        get() = impl.isKotlinObject
    override val qualifiedName: String
        get() = impl.qualifiedName.toString()
    override val constructors: Sequence<FunctionLangModel> = impl.enclosedElements
        .asSequence()
        .filter { it.kind == ElementKind.CONSTRUCTOR }
        .map {
            JavaxFunctionImpl(owner = this, impl = it.asExecutableElement(), isConstructor = true)
        }
    override val allPublicFunctions: Sequence<FunctionLangModel> = sequence {
        yieldAll(impl.allMethods(Utils.types, Utils.elements).map {
            JavaxFunctionImpl(
                owner = this@JavaxTypeDeclarationImpl,
                impl = it,
                isConstructor = false,
                isFromCompanionObject = false
            )
        })
        impl.getCompanionObject()?.allMethods(Utils.types, Utils.elements)?.map {
            JavaxFunctionImpl(
                owner = this@JavaxTypeDeclarationImpl,
                impl = it,
                isConstructor = false,
                isFromCompanionObject = true
            )
        }?.let { yieldAll(it) }
    }

    override val allPublicFields: Sequence<FieldLangModel> = impl.enclosedElements.asSequence()
        .filter { it.kind == ElementKind.FIELD && it.isPublic }
        .map { JavaxFieldImpl(owner = this, impl = it.asVariableElement()) }
        .memoize()

    override val nestedInterfaces: Sequence<TypeDeclarationLangModel> = impl.enclosedElements
        .asSequence()
        .filter { it.kind == ElementKind.INTERFACE }
        .map { JavaxTypeDeclarationImpl(it.asTypeElement()) }

    override fun asType(): TypeLangModel {
        require(impl.typeParameters.isEmpty())
        return NamedTypeLangModel(impl.asType())
    }

    override fun equals(other: Any?): Boolean {
        return this === other || (other is JavaxTypeDeclarationImpl && impl == other.impl)
    }

    override fun hashCode() = impl.hashCode()
}