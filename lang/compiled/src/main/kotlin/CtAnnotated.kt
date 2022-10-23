package com.yandex.daggerlite.lang.compiled

import com.yandex.daggerlite.lang.Annotated

interface CtAnnotated : Annotated {
    override val annotations: Sequence<CtAnnotation>
}