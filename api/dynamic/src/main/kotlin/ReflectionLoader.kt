/*
 * Copyright 2023 Yandex LLC
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

package com.yandex.yatagan.dynamic

import com.yandex.yatagan.AutoBuilder
import com.yandex.yatagan.base.singleInitWithFallback
import com.yandex.yatagan.internal.ImplementationLoader
import com.yandex.yatagan.internal.YataganInternal
import com.yandex.yatagan.rt.engine.RuntimeEngine
import com.yandex.yatagan.rt.support.DynamicValidationDelegate
import com.yandex.yatagan.rt.support.Logger

/**
 * Instantiated reflectively.
 * WARNING: Keep class name in sync with [com.yandex.yatagan.Yatagan.DYNAMIC_LOADER_DELEGATE_CLASS_NAME].
 */
@PublishedApi
@OptIn(YataganInternal::class)
internal class ReflectionLoader : ImplementationLoader by ReflectionLoader {
    internal companion object : ImplementationLoader {
        private val engineHolder = singleInitWithFallback { RuntimeEngine(Params()) }
        private var engine: RuntimeEngine<Params> by engineHolder

        override fun <T : Any> builder(builderClass: Class<T>): Result<T> {
            return Result.success(engine.builder(builderClass))
        }

        override fun <T : Any> autoBuilder(componentClass: Class<T>): Result<AutoBuilder<T>> {
            return Result.success(engine.autoBuilder(componentClass))
        }

        internal fun complete(params: Params) {
            engine = RuntimeEngine(params.copy())
        }

        internal fun reset() {
            if (engineHolder.isInitialized()) {
                engine.destroy()
                engineHolder.deinitialize()
            }
        }
    }

    internal data class Params (
        override var validationDelegate: DynamicValidationDelegate? = null,
        override var maxIssueEncounterPaths: Int = 5,
        override var isStrictMode: Boolean = true,
        override var logger: Logger? = null,
    ) : RuntimeEngine.Params
}

