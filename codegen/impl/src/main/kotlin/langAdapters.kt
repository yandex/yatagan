package com.yandex.daggerlite.codegen.impl

import com.yandex.daggerlite.core.model.ClassBackedModel
import com.yandex.daggerlite.lang.FunctionLangModel
import com.yandex.daggerlite.lang.Type
import com.yandex.daggerlite.lang.compiled.CtType
import com.yandex.daggerlite.lang.compiled.CtTypeNameModel

internal val Type.name: CtTypeNameModel
    get() = (this as CtType).nameModel

internal val ClassBackedModel.name: CtTypeNameModel
    get() = type.name

internal val FunctionLangModel.ownerName: CtTypeNameModel
    get() = owner.asType().name
