package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.core.lang.FunctionLangModel
import kotlinx.metadata.KmProperty

internal class RtPropertyAccessorImpl(
    override val kind: FunctionLangModel.PropertyAccessorKind,
    private val property: KmProperty,
) : FunctionLangModel.PropertyAccessorInfo {
    override val propertyName: String
        get() = property.name
}