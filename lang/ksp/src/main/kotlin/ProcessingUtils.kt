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
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import java.io.Closeable

private var utils: ProcessingUtils? = null

internal val Utils: ProcessingUtils get() = checkNotNull(utils) {
    "Not reached: utils are used before set/after cleared"
}

class ProcessingUtils(
    val resolver: Resolver,
    val environment: SymbolProcessorEnvironment,
) : Closeable {

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
        Utils.resolver.getClassDeclarationByName("kotlin.annotation.Retention")!!
    }

    val javaRetentionClass by lazy {
        Utils.resolver.getJavaClassByName("java.lang.annotation.Retention")!!
    }

    val isKsp2 by lazy {
        environment.kspVersion >= KotlinVersion(2, 0)
    }

    init {
        utils = this
    }

    override fun close() {
        utils = null
    }
}