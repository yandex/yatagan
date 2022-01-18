package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSPropertySetter
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.lang.FunctionLangModel.PropertyAccessorKind
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import kotlin.LazyThreadSafetyMode.NONE

internal class KspFunctionPropertySetterImpl private constructor(
    setter: KSPropertySetter,
    override val owner: KspTypeDeclarationImpl,
) : KspFunctionPropertyAccessorBase<KSPropertySetter>(setter) {

    override val returnType: TypeLangModel = KspTypeImpl(Utils.resolver.builtIns.unitType)

    @Suppress("DEPRECATION")  // capitalize
    override val name: String by lazy(NONE) {
        Utils.resolver.getJvmName(setter) ?: "set${property.simpleName.asString().capitalize()}"
    }

    override val parameters: Sequence<ParameterLangModel> = sequence {
        yield(KspParameterImpl(
            impl = setter.parameter,
            refinedType = property.asMemberOf(owner.type),
            jvmSignatureSupplier = { jvmSignature },
        ))
    }

    override val kind: PropertyAccessorKind
        get() = PropertyAccessorKind.Setter


    companion object Factory : ObjectCache<KSPropertySetter, KspFunctionPropertySetterImpl>() {
        operator fun invoke(
            setter: KSPropertySetter,
            owner: KspTypeDeclarationImpl,
        ) = createCached(setter) {
            KspFunctionPropertySetterImpl(
                setter = setter,
                owner = owner,
            )
        }
    }
}