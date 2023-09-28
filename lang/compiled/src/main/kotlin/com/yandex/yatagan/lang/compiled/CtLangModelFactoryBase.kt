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

package com.yandex.yatagan.lang.compiled

import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.common.LangModelFactoryFallback

abstract class CtLangModelFactoryBase : LangModelFactoryFallback() {
    final override fun createNoType(name: String): Type {
        return CtErrorType(
            nameModel = InvalidNameModel.Error(error = name),
            // This is a synthetic "no"-type, it's assumed to be resolved: no need to report it in validation.
            isUnresolved = false,
        )
    }
}