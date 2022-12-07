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

package com.yandex.yatagan.testing.procedural

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName

internal object ClassNames {
    val BindsInstance = ClassName("com.yandex.yatagan", "BindsInstance")
    val Component = ClassName("com.yandex.yatagan", "Component")
    val Module = ClassName("com.yandex.yatagan", "Module")
    val Lazy = ClassName("com.yandex.yatagan", "Lazy")
    val Provides = ClassName("com.yandex.yatagan", "Provides")
    val Binds = ClassName("com.yandex.yatagan", "Binds")
    val ComponentBuilder = ClassName("com.yandex.yatagan", "Component", "Builder")
    val Yatagan = ClassName("com.yandex.yatagan", "Yatagan")

    val Retention = ClassName("kotlin.annotation", "Retention")
    val AnnotationRetention = ClassName("kotlin.annotation", "AnnotationRetention")

    val Scope = ClassName("javax.inject", "Scope")
    val Inject = ClassName("javax.inject", "Inject")
    val Provider = ClassName("javax.inject", "Provider")

    val mock = MemberName("org.mockito.kotlin", "mock")
    val Answers = ClassName("org.mockito", "Answers")

    val Any = ClassName("kotlin", "Any")
    val Suppress = ClassName("kotlin", "Suppress")

}