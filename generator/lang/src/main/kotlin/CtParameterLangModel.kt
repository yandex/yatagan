package com.yandex.daggerlite.generator.lang

import com.yandex.daggerlite.Assisted
import com.yandex.daggerlite.core.lang.AssistedAnnotationLangModel
import com.yandex.daggerlite.core.lang.hasType
import com.yandex.daggerlite.lang.common.ParameterLangModelBase

abstract class CtParameterLangModel : ParameterLangModelBase() {
    abstract override val annotations: Sequence<CtAnnotationLangModel>

    override val assistedAnnotationIfPresent: AssistedAnnotationLangModel?
        get() = annotations.find { it.hasType<Assisted>() }?.let { CtAssistedAnnotationImpl(it) }
}