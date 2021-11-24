package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSPropertySetter
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import kotlin.LazyThreadSafetyMode.NONE

internal class KspFunctionPropertySetterImpl private constructor(
    setter: KSPropertySetter,
    override val owner: TypeDeclarationLangModel,
    override val isFromCompanionObject: Boolean,
) : KspFunctionPropertyAccessorBase<KSPropertySetter>(setter) {

    override val returnType: TypeLangModel = KspTypeImpl(Utils.resolver.builtIns.unitType)
    @Suppress("DEPRECATION")  // capitalize
    override val name: String by lazy(NONE) {
        // TODO: Support @JvmName here?
        "set${property.simpleName.asString().capitalize()}"
    }
    override val parameters: Sequence<ParameterLangModel> = sequence {
        yield(KspParameterImpl(accessor.parameter))
    }

    companion object Factory : ObjectCache<KSPropertySetter, KspFunctionPropertySetterImpl>() {
        operator fun invoke(
            setter: KSPropertySetter,
            owner: TypeDeclarationLangModel,
            isFromCompanionObject: Boolean = false,
        ) = createCached(setter) {
            KspFunctionPropertySetterImpl(
                setter = setter,
                owner = owner,
                isFromCompanionObject = isFromCompanionObject,
            )
        }
    }
}