package com.yandex.daggerlite.testing.dokka

import org.jetbrains.dokka.CoreExtensions
import org.jetbrains.dokka.model.doc.*
import org.jetbrains.dokka.plugability.ConfigurableBlock
import org.jetbrains.dokka.plugability.DokkaPlugin


@Suppress("MemberVisibilityCanBePrivate")
class DLDokkaPlugin : DokkaPlugin() {
    val tagWrapperTransformer by extensionPoint<TagWrapperTransformer>()

    val documentableTransformer by extending {
        CoreExtensions.documentableTransformer providing ::DocumentableTransformerImpl
    }

    val codeBlockTransformer by extending {
        tagWrapperTransformer providing ::CodeBlockTransformer
    }
}

data class DLDokkaConfiguration(
    val codeBlockTestsOutputDirectory: String? = null,
) : ConfigurableBlock

interface TagWrapperTransformer {
    fun transform(tagWrapper: TagWrapper, context: TransformContext): TagWrapper
}
