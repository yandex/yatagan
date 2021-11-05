package com.yandex.daggerlite.generator

import com.yandex.daggerlite.core.ClassBackedModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.CtTypeLangModel
import com.yandex.daggerlite.generator.lang.CtTypeNameModel

internal val TypeLangModel.name: CtTypeNameModel
    get() = (this as CtTypeLangModel).name

internal val ClassBackedModel.name: CtTypeNameModel
    get() = type.name

internal val FunctionLangModel.ownerName: CtTypeNameModel
    get() = owner.asType().name
