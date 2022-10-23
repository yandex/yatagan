package com.yandex.daggerlite.lang.compiled

import com.yandex.daggerlite.Assisted
import com.yandex.daggerlite.lang.AssistedAnnotationLangModel
import com.yandex.daggerlite.lang.common.ParameterBase
import com.yandex.daggerlite.lang.hasType

abstract class CtParameter : ParameterBase() {
    abstract override val annotations: Sequence<CtAnnotationLangModel>

    override val assistedAnnotationIfPresent: AssistedAnnotationLangModel?
        get() = annotations.find { it.hasType<Assisted>() }?.let { CtAssistedAnnotationImpl(it) }
}