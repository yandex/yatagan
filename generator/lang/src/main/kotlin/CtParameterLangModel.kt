package com.yandex.daggerlite.generator.lang

import com.yandex.daggerlite.Assisted
import com.yandex.daggerlite.core.lang.AssistedAnnotationLangModel
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.hasType

abstract class CtParameterLangModel : ParameterLangModel {
    abstract override val annotations: Sequence<CtAnnotationLangModel>

    override val assistedAnnotationIfPresent: AssistedAnnotationLangModel?
        get() = annotations.find { it.hasType<Assisted>() }?.let { CtAssistedAnnotationImpl(it) }
}