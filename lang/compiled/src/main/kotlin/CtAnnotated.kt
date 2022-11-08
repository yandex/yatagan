package com.yandex.yatagan.lang.compiled

import com.yandex.yatagan.lang.Annotated

interface CtAnnotated : Annotated {
    override val annotations: Sequence<CtAnnotationBase>
}