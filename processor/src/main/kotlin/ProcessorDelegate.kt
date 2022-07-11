package com.yandex.daggerlite.process

import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
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
     * Get a [type declaration][TypeDeclarationLangModel] from a source
     */
    fun createDeclaration(source: Source): TypeDeclarationLangModel

    /**
     * Get a Source for the given declaration.
     */
    fun getSourceFor(declaration: TypeDeclarationLangModel): Source

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