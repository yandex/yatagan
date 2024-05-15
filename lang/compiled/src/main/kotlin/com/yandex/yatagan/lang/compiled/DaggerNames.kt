/*
 * Copyright 2024 Yandex LLC
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

internal object DaggerNames {
    const val REUSABLE = "dagger.Reusable"

    const val MODULE = "dagger.Module"
    const val COMPONENT = "dagger.Component"
    const val SUBCOMPONENT = "dagger.Subcomponent"

    val COMPONENT_BUILDERS = setOf(
        "dagger.Component.Builder",
        "dagger.Component.Factory",
        "dagger.Subcomponent.Builder",
        "dagger.Subcomponent.Factory",
    )

    const val BINDS = "dagger.Binds"
    const val BINDS_INSTANCE = "dagger.BindsInstance"
    const val PROVIDES = "dagger.Provides"
    const val MAP_KEY = "dagger.MapKey"

    const val ASSISTED = "dagger.assisted.Assisted"
    const val ASSISTED_INJECT = "dagger.assisted.AssistedInject"
    const val ASSISTED_FACTORY = "dagger.assisted.AssistedFactory"

    const val INTO_MAP = "dagger.multibindings.IntoMap"
    const val INTO_SET = "dagger.multibindings.IntoSet"
    const val MULTIBINDS = "dagger.multibindings.Multibinds"
    const val ELEMENTS_INTO_SET = "dagger.multibindings.ElementsIntoSet"
}
