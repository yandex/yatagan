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

import com.yandex.yatagan.YataganDelicateApi

/**
 * Contains utility methods to control Yatagan reflection backend.
 */
public object YataganReflection {
    /**
     * Clients may optionally call this method after all the work with Yatagan graphs is done.
     * This call clears all internal global states and caches, freeing memory.
     * **Any further usage of already created graphs may result in Undefined Behavior**,
     * so make sure no methods on any created components are called after `shutdown()`.
     *
     * NOTE: In most of the cases **it's best not to call this method at all**, as DI is often being used up until
     * the very application termination, and it doesn't make much sense to call `reset()` right before that.
     */
    @JvmStatic
    @YataganDelicateApi
    public fun resetGlobalState() {
        ReflectionLoader.reset()
    }
}