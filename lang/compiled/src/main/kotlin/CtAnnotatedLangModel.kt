package com.yandex.daggerlite.lang.compiled

import com.yandex.daggerlite.lang.AnnotatedLangModel

interface CtAnnotatedLangModel : AnnotatedLangModel {
    override val annotations: Sequence<CtAnnotationLangModel>
}