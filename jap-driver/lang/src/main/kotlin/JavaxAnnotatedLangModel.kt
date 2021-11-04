package com.yandex.daggerlite.jap.lang

import com.yandex.daggerlite.core.lang.AnnotatedLangModel
import com.yandex.daggerlite.generator.lang.CompileTimeAnnotationLangModel

interface JavaxAnnotatedLangModel : AnnotatedLangModel {
    override val annotations: Sequence<CompileTimeAnnotationLangModel>
}