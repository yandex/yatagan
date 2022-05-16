package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSPropertyGetter
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import kotlin.LazyThreadSafetyMode.NONE

internal class KspFunctionPropertyGetterImpl private constructor(
    getter: KSPropertyGetter,
    override val owner: KspTypeDeclarationImpl,
    isStatic: Boolean,
) : KspFunctionPropertyAccessorBase<KSPropertyGetter>(getter, isStatic) {

    override val returnType: TypeLangModel by lazy(NONE) {
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
    override val name: String by lazy(NONE) {
        Utils.resolver.getJvmName(getter) ?: run {
            val propName = property.simpleName.asString()
            if (PropNameIsRegex.matches(propName)) propName
            else "get${propName.capitalize()}"
        }
    }

    override val parameters: Sequence<ParameterLangModel> = emptySequence()

    override val platformModel: Any?
        get() = null

    companion object Factory : ObjectCache<KSPropertyGetter, KspFunctionPropertyGetterImpl>() {
        private val PropNameIsRegex = "^is[^a-z].*$".toRegex()

        operator fun invoke(
            getter: KSPropertyGetter,
            owner: KspTypeDeclarationImpl,
            isStatic: Boolean,
        ) = createCached(getter) {
            KspFunctionPropertyGetterImpl(
                getter = getter,
                owner = owner,
                isStatic = isStatic
            )
        }
    }
}