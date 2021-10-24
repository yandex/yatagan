package com.yandex.daggerlite.jap.lang

import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.ClassNameModel
import com.yandex.daggerlite.generator.lang.NamedTypeLangModel
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement

internal class JavaxTypeDeclarationImpl(
    override val impl: TypeElement,
) : JavaxAnnotatedImpl(), TypeDeclarationLangModel {
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
            JavaxFunctionImpl(owner = this, impl = it as ExecutableElement, isConstructor = true)
        }
    override val allPublicFunctions: Sequence<FunctionLangModel> = impl.enclosedElements
        .asSequence()
        .filter { it.kind == ElementKind.METHOD }
        .map {
            JavaxFunctionImpl(owner = this, impl = it as ExecutableElement, isConstructor = false)
        }
    override val nestedInterfaces: Sequence<TypeDeclarationLangModel> = impl.enclosedElements
        .asSequence()
        .filter { it.kind == ElementKind.INTERFACE }
        .map { JavaxTypeDeclarationImpl(it as TypeElement) }

    override fun asType(): TypeLangModel {
        require(impl.typeParameters.isEmpty())
        return NoArgsTypeImpl()
    }

    private inner class NoArgsTypeImpl : NamedTypeLangModel() {
        override val declaration: TypeDeclarationLangModel
            get() = this@JavaxTypeDeclarationImpl
        override val typeArguments: Sequence<Nothing>
            get() = emptySequence()
        override val name: ClassNameModel
            get() = ClassNameModel(impl)
    }

    override fun equals(other: Any?): Boolean {
        return this === other || (other is JavaxTypeDeclarationImpl && impl == other.impl)
    }

    override fun hashCode() = impl.hashCode()
}