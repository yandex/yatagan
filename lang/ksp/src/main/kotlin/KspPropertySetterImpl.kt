package com.yandex.daggerlite.lang.ksp

import com.google.devtools.ksp.symbol.KSPropertySetter
import com.google.devtools.ksp.symbol.Modifier
import com.yandex.daggerlite.lang.Parameter
import com.yandex.daggerlite.lang.Type

internal class KspPropertySetterImpl(
    private val setter: KSPropertySetter,
    override val owner: KspTypeDeclarationImpl,
    isStatic: Boolean,
) : KspPropertyAccessorBase<KSPropertySetter>(setter, isStatic) {

    override val isEffectivelyPublic: Boolean
        get() = super.isEffectivelyPublic && setter.modifiers.let {
            Modifier.PRIVATE !in it && Modifier.PROTECTED !in it
        }

    override val returnType: Type = KspTypeImpl(Utils.resolver.builtIns.unitType)

    @Suppress("DEPRECATION")  // capitalize
    override val name: String by lazy {
        Utils.resolver.getJvmName(setter) ?: "set${property.simpleName.asString().capitalize()}"
    }

    override val parameters: Sequence<Parameter> = sequence {
        yield(KspParameterImpl(
            impl = setter.parameter,
            refinedTypeRef = property.type.replaceType(property.asMemberOf(owner.type.impl)),
            jvmSignatureSupplier = { jvmSignature },
        ))
    }

    override val platformModel: Any?
        get() = null
}