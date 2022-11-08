package com.yandex.yatagan.codegen.impl

import com.yandex.yatagan.core.model.ClassBackedModel
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.compiled.CtTypeBase
import com.yandex.yatagan.lang.compiled.CtTypeNameModel

internal val Type.name: CtTypeNameModel
    get() = (this as CtTypeBase).nameModel

internal val ClassBackedModel.name: CtTypeNameModel
    get() = type.name

internal val Method.ownerName: CtTypeNameModel
    get() = owner.asType().name
