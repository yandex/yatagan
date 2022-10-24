package com.yandex.daggerlite.codegen.impl

import com.yandex.daggerlite.core.model.ClassBackedModel
import com.yandex.daggerlite.lang.Method
import com.yandex.daggerlite.lang.Type
import com.yandex.daggerlite.lang.compiled.CtTypeBase
import com.yandex.daggerlite.lang.compiled.CtTypeNameModel

internal val Type.name: CtTypeNameModel
    get() = (this as CtTypeBase).nameModel

internal val ClassBackedModel.name: CtTypeNameModel
    get() = type.name

internal val Method.ownerName: CtTypeNameModel
    get() = owner.asType().name
