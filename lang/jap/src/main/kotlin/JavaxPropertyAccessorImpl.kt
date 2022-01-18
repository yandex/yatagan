package com.yandex.daggerlite.jap.lang

import com.yandex.daggerlite.core.lang.FunctionLangModel
import kotlinx.metadata.KmProperty

internal class JavaxPropertyAccessorImpl(
    override val kind: FunctionLangModel.PropertyAccessorKind,
    private val property: KmProperty,
) : FunctionLangModel.PropertyAccessorInfo {
    override val propertyName: String
        get() = property.name
}