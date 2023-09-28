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

package com.yandex.yatagan.testing.tests

import javax.inject.Provider

internal fun compileTestDrivers(
    includeKsp: Boolean = true,
    includeJap: Boolean = true,
    includeRt: Boolean = true,
): Collection<Provider<CompileTestDriverBase>> {
    class NamedProvider(
        private val initializer: () -> CompileTestDriverBase,
        private val name: String,
    ) : Provider<CompileTestDriverBase> {
        override fun get() = initializer()
        override fun toString() = name
    }

    val providers = buildList {
        if (includeJap) {
            add(NamedProvider(::JapCompileTestDriver, name = "JAP"))
        }
        if (includeKsp) {
            add(NamedProvider(::KspCompileTestDriver, name = "KSP"))
        }
        if (includeRt) {
            add(NamedProvider(::DynamicCompileTestDriver, name = "RT"))
        }
    }
    return if (CompileTestDriverBase.isInUpdateGoldenMode) {
        // No need to use all backends, use only the first included to be chosen as "golden".
        providers.take(1)
    } else providers
}
