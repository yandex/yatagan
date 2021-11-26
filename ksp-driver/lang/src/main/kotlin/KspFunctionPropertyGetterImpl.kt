package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSPropertyGetter
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import kotlin.LazyThreadSafetyMode.NONE

internal class KspFunctionPropertyGetterImpl private constructor(
    getter: KSPropertyGetter,
    override val owner: KspTypeDeclarationImpl,
) : KspFunctionPropertyAccessorBase<KSPropertyGetter>(getter) {
    override val returnType: TypeLangModel by lazy(NONE) {
        KspTypeImpl(property.asMemberOf(owner.type))
    }
    override val name: String by lazy(NONE) {
        val propName = property.simpleName.asString()
        @Suppress("DEPRECATION")  // capitalize
        if (PropNameIsRegex.matches(propName)) propName
        else "get${propName.capitalize()}"
    }
    override val parameters: Sequence<ParameterLangModel> = emptySequence()

    companion object Factory : ObjectCache<KSPropertyGetter, KspFunctionPropertyGetterImpl>() {
        private val PropNameIsRegex = "^is[^a-z].*$".toRegex()

        operator fun invoke(
            getter: KSPropertyGetter,
            owner: KspTypeDeclarationImpl,
        ) = createCached(getter) {
            KspFunctionPropertyGetterImpl(
                getter = getter,
                owner = owner,
            )
        }
    }
}