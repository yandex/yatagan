package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSPropertyGetter
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.lang.FunctionLangModel.PropertyAccessorKind
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

    @Suppress("DEPRECATION")  // capitalize
    override val name: String by lazy(NONE) {
        getter.explicitJvmName ?: run {
            val propName = property.simpleName.asString()
            if (PropNameIsRegex.matches(propName)) propName
            else "get${propName.capitalize()}"
        }
    }

    override val parameters: Sequence<ParameterLangModel> = emptySequence()

    override val kind: PropertyAccessorKind
        get() = PropertyAccessorKind.Getter

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