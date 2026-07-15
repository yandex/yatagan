/*
 * Copyright 2026 Yandex LLC
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

package com.yandex.yatagan.kcp.consumer

import com.yandex.yatagan.Component
import com.yandex.yatagan.Module
import com.yandex.yatagan.Provides

@Module
object ConsumerModule {
    @Provides
    fun message(): String = "KCP native code generation works"
}

@Component(modules = [ConsumerModule::class])
interface ConsumerComponent {
    fun message(): String
}
