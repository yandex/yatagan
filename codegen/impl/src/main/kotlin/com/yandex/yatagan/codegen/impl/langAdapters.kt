/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yandex.yatagan.codegen.impl

import com.yandex.yatagan.core.model.ClassBackedModel
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.compiled.CtNamedType
import com.yandex.yatagan.lang.compiled.CtTypeNameModel

internal val Type.name: CtTypeNameModel
    get() = (this as CtNamedType).nameModel

internal val ClassBackedModel.name: CtTypeNameModel
    get() = type.name

internal val Method.ownerName: CtTypeNameModel
    get() = owner.asType().name
