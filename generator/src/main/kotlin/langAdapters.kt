package com.yandex.daggerlite.generator

import com.yandex.daggerlite.core.ClassBackedModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.ClassNameModel
import com.yandex.daggerlite.generator.lang.NamedTypeLangModel

internal val TypeLangModel.name: ClassNameModel
    get() = (this as NamedTypeLangModel).name

internal val ClassBackedModel.name: ClassNameModel
    get() = type.name

internal val FunctionLangModel.ownerName: ClassNameModel
    get() = owner.asType().name
