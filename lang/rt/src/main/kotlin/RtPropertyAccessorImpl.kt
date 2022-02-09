package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.core.lang.FunctionLangModel
import kotlin.reflect.KProperty

internal class RtPropertyAccessorImpl(
    override val kind: FunctionLangModel.PropertyAccessorKind,
    private val property: KProperty<*>,
) : FunctionLangModel.PropertyAccessorInfo {
    override val propertyName: String
        get() = property.name
}