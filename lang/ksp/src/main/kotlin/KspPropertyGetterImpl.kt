package com.yandex.yatagan.lang.ksp

import com.google.devtools.ksp.symbol.KSPropertyGetter
import com.yandex.yatagan.lang.Parameter
import com.yandex.yatagan.lang.Type

internal class KspPropertyGetterImpl(
    getter: KSPropertyGetter,
    override val owner: KspTypeDeclarationImpl,
    isStatic: Boolean,
) : KspPropertyAccessorBase<KSPropertyGetter>(getter, isStatic) {

    override val returnType: Type by lazy {
        var typeReference = property.type
        if (!isStatic) {
            typeReference = typeReference.replaceType(property.asMemberOf(owner.type.impl))
        }
        KspTypeImpl(
            reference = typeReference,
            jvmSignatureHint = jvmSignature,
        )
    }

    @Suppress("DEPRECATION")  // capitalize
    override val name: String by lazy {
        Utils.resolver.getJvmName(getter) ?: run {
            val propName = property.simpleName.asString()
            if (PropNameIsRegex.matches(propName)) propName
            else "get${propName.capitalize()}"
        }
    }

    override val parameters: Sequence<Parameter> = emptySequence()

    override val platformModel: Any?
        get() = null

    companion object {
        private val PropNameIsRegex = "^is[^a-z].*$".toRegex()
    }
}