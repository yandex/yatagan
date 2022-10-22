package com.yandex.daggerlite.generator.lang

import com.yandex.daggerlite.core.lang.AnnotatedLangModel

interface CtAnnotatedLangModel : AnnotatedLangModel {
    override val annotations: Sequence<CtAnnotationLangModel>
}