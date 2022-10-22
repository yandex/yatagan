package com.yandex.daggerlite.codegen.impl

import com.yandex.daggerlite.core.model.ClassBackedModel
import com.yandex.daggerlite.lang.FunctionLangModel
import com.yandex.daggerlite.lang.TypeLangModel
import com.yandex.daggerlite.lang.compiled.CtTypeLangModel
import com.yandex.daggerlite.lang.compiled.CtTypeNameModel

internal val TypeLangModel.name: CtTypeNameModel
    get() = (this as CtTypeLangModel).nameModel

internal val ClassBackedModel.name: CtTypeNameModel
    get() = type.name

internal val FunctionLangModel.ownerName: CtTypeNameModel
    get() = owner.asType().name
