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

package com.yandex.yatagan.lang.ksp

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getJavaClassByName
import com.google.devtools.ksp.processing.Resolver
import com.yandex.yatagan.base.api.Extensible
import com.yandex.yatagan.lang.scope.LexicalScope

internal class ProcessingUtils(
    val resolver: Resolver,
    val isKsp2: Boolean,
) {
    val classType by lazy {
        resolver.getClassDeclarationByName("java.lang.Class")!!
    }

    val anyType by lazy {
        resolver.getClassDeclarationByName("kotlin.Any")!!
    }

    val objectType by lazy {
        resolver.getClassDeclarationByName("java.lang.Object")!!
    }

    val kotlinRetentionClass by lazy {
        resolver.getClassDeclarationByName("kotlin.annotation.Retention")!!
    }

    val javaRetentionClass by lazy {
        resolver.getJavaClassByName("java.lang.annotation.Retention")!!
    }

    companion object : Extensible.Key<ProcessingUtils, LexicalScope.Extensions>
}
