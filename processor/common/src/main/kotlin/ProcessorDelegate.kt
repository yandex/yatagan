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

package com.yandex.yatagan.processor.common

import com.yandex.yatagan.lang.TypeDeclaration
import java.io.Writer

/**
 * Platform-specific processor implementation.
 *
 * @param Source platform-specific opaque object, that represent a source of a class declaration.
 *
 * @see openFileForGenerating
 */
interface ProcessorDelegate<Source> {
    val logger: Logger

    /**
     * Get a [type declaration][TypeDeclaration] from a source
     */
    fun createDeclaration(source: Source): TypeDeclaration

    /**
     * Get a Source for the given declaration.
     */
    fun getSourceFor(declaration: TypeDeclaration): Source

    /**
     * Opens file for writing generated Java code. Only a single class is permitted per file.
     *
     * @param sources a sequence of sources that were used for generation of this file in **ISOLATING** mode.
     * An implementation may use *only the first element* of the sequence if it chooses,
     * which *MUST* be a component declaration.
     * Other sources are optional and serve as hints to what elements (accessible from the first one) were used.
     * @param packageName `.`-separated java package name for generated file
     * @param className simple name for class being generated.
     */
    fun openFileForGenerating(
        sources: Sequence<Source>,
        packageName: String,
        className: String,
    ): Writer

    /**
     * Constructed options container.
     */
    val options: Options
}