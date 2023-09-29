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

package com.yandex.yatagan.testing.source_set

import org.intellij.lang.annotations.Language

/**
 * Source file for testing
 */
typealias SourceFile = androidx.room.compiler.processing.util.Source

interface SourceSet {
    /**
     * All sources, currently included into the source set.
     */
    val sourceFiles: List<SourceFile>

    /**
     * Adds a Java source to the source set.
     *
     * @param name a dot-separated qualified name of a source file. package-name is derived from this.
     * @param source source code in Java. No package directive is expected.
     * @param addPackageDirective if `true` then a package directive will be prepended.
     *   Package name is computed based on the [name].
     */
    fun givenJavaSource(
        name: String,
        @Language("java") source: String,
        addPackageDirective: Boolean = true,
    )

    /**
     * Adds a Kotlin source to the source set.
     *
     * @param name a dot-separated qualified name of a source file. package-name is derived from this.
     * @param source source code in Kotlin. No package directive is expected.
     * @param addPackageDirective if `true` then a package directive will be prepended.
     *   Package name is computed based on the [name].
     */
    fun givenKotlinSource(
        name: String,
        @Language("kotlin") source: String,
        addPackageDirective: Boolean = true,
    )

    /**
     * Includes all currently added sources from the [sourceSet]. No relations are established between source sets.
     * If any changes to be done later to the given [sourceSet], they will not be reflected here.
     */
    fun includeFromSourceSet(sourceSet: SourceSet)
}

/**
 * Constructor function for [SourceSet]s.
 */
fun SourceSet(block: SourceSet.() -> Unit = {}): SourceSet = SourceSetImpl().apply(block)