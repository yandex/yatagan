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

package com.yandex.yatagan.lang.jap

import java.io.Closeable
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

private var utils: ProcessingUtils? = null

internal val Utils: ProcessingUtils get() = checkNotNull(utils) {
    "Not reached: utils are used before set/after cleared"
}

class ProcessingUtils(
    val types: Types,
    val elements: Elements,
) : Closeable {
    val booleanType: TypeElement by lazy {
        elements.getTypeElement("java.lang.Boolean")
    }
    val objectType : TypeElement by lazy {
        elements.getTypeElement("java.lang.Object")
    }
    val stringType : TypeElement by lazy {
        elements.getTypeElement("java.lang.String")
    }

    init {
        utils = this
    }

    override fun close() {
        utils = null
    }
}