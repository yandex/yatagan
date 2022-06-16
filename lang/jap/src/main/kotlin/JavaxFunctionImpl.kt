package com.yandex.daggerlite.jap.lang

import com.yandex.daggerlite.base.BiObjectCache
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.CtAnnotatedLangModel
import com.yandex.daggerlite.generator.lang.CtFunctionLangModel
import javax.lang.model.element.ExecutableElement

internal class JavaxFunctionImpl private constructor(
    private val impl: ExecutableElement,
    override val owner: JavaxTypeDeclarationImpl,
) : CtAnnotatedLangModel by JavaxAnnotatedImpl(impl), CtFunctionLangModel() {

    override val isAbstract: Boolean get() = impl.isAbstract

    override val isStatic: Boolean get() = impl.isStatic

    override val isEffectivelyPublic: Boolean
        get() = impl.isPublic

    override val returnType: TypeLangModel by lazy {
        JavaxTypeImpl(impl.asMemberOf(owner.type).asExecutableType().returnType)
    }
    override val name: String get() = impl.simpleName.toString()

    override val parameters: Sequence<ParameterLangModel> = parametersSequenceFor(impl, owner.type)

    override val platformModel: ExecutableElement get() = impl

    companion object Factory : BiObjectCache<JavaxTypeDeclarationImpl, ExecutableElement, JavaxFunctionImpl>() {
        operator fun invoke(
            owner: JavaxTypeDeclarationImpl,
            impl: ExecutableElement,
        ) = createCached(owner, impl) {
            JavaxFunctionImpl(
                impl = impl,
                owner = owner,
            )
        }
    }
}