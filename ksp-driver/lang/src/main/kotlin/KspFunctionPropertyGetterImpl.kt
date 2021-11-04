package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import kotlin.LazyThreadSafetyMode.NONE

internal class KspFunctionPropertyGetterImpl(
    private val impl: KSPropertyDeclaration,
    override val owner: TypeDeclarationLangModel,
    override val isFromCompanionObject: Boolean,
) : FunctionLangModel {
    override val annotations: Sequence<AnnotationLangModel> = impl.getter?.let(::annotationsFrom) ?: emptySequence()
    override val isAbstract: Boolean
        get() = impl.isAbstract()
    override val isStatic: Boolean
        get() = impl.isStatic
    override val returnType: TypeLangModel by lazy(NONE) {
        KspTypeImpl(impl.type.resolve())
    }
    override val name: String by lazy(NONE) {
        val propName = impl.simpleName.asString()
        @Suppress("DEPRECATION")  // capitalize
        if (PropNameIsRegex.matches(propName)) propName
        else "get${impl.simpleName.asString().capitalize()}"
    }
    override val parameters: Sequence<ParameterLangModel> = emptySequence()
    override val isConstructor: Boolean
        get() = false

    override fun equals(other: Any?): Boolean {
        return this === other || (other is KspFunctionPropertyGetterImpl && impl == other.impl)
    }

    override fun hashCode() = impl.hashCode()

    companion object {
        private val PropNameIsRegex = "^is[^a-z].*$".toRegex()
    }
}