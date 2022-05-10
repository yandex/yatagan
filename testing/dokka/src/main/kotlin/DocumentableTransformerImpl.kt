package com.yandex.daggerlite.testing.dokka

import org.jetbrains.dokka.model.*
import org.jetbrains.dokka.model.doc.*
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.transformers.documentation.DocumentableTransformer

internal class DocumentableTransformerImpl(
    context: DokkaContext,
) : DocumentableTransformer {
    private val plugin = context.plugin(DLDokkaPlugin::class)!!
    private val transformers = context[plugin.tagWrapperTransformer]

    override fun invoke(original: DModule, context: DokkaContext): DModule {
        if (transformers.isEmpty())
            return original

        return transform(original)
    }

    private fun TransformContext.transform(
        documentation: SourceSetDependent<DocumentationNode>,
    ): SourceSetDependent<DocumentationNode> {
        return documentation.mapValues { (_, it) ->
            it.copy(children = it.children.map {
                transformers.fold(it) { tag, transformer -> transformer.transform(tag, this) }
            })
        }
    }

    private fun transform(module: DModule): DModule = with(TransformContext(module)) {
        module.copy(
            packages = module.packages.map { transform(it) },
            documentation = transform(module.documentation),
        )
    }

    private fun TransformContext.transform(`package`: DPackage): DPackage = with(TransformContext(`package`)) {
        `package`.copy(
            functions = `package`.functions.map { transform(it) },
            classlikes = `package`.classlikes.map { transform(it) },
            properties = `package`.properties.map { transform(it) },
            typealiases = `package`.typealiases.map { transform(it) },
            documentation = transform(`package`.documentation),
        )
    }

    private fun TransformContext.transform(function: DFunction): DFunction = with(TransformContext(function)) {
        function.copy(
            parameters = function.parameters.map { transform(it) },
            generics = function.generics.map { transform(it) },
            documentation = transform(function.documentation),
        )
    }

    private fun TransformContext.transform(parameter: DParameter): DParameter = with(TransformContext(parameter)) {
        parameter.copy(
            documentation = transform(parameter.documentation),
        )
    }

    private fun TransformContext.transform(property: DProperty): DProperty = with(TransformContext(property)) {
        property.copy(
            documentation = transform(property.documentation),
            generics = property.generics.map { transform(it) },
        )
    }

    private fun TransformContext.transform(typeAlias: DTypeAlias): DTypeAlias = with(TransformContext(typeAlias)) {
        typeAlias.copy(
            documentation = transform(typeAlias.documentation),
            generics = typeAlias.generics.map { transform(it) },
        )
    }

    private fun TransformContext.transform(param: DTypeParameter): DTypeParameter = with(TransformContext(param)) {
        param.copy(
            documentation = transform(param.documentation),
        )
    }

    private fun TransformContext.transform(enumEntry: DEnumEntry): DEnumEntry = with(TransformContext(enumEntry)) {
        enumEntry.copy(
            functions = enumEntry.functions.map { transform(it) },
            properties = enumEntry.properties.map { transform(it) },
            classlikes = enumEntry.classlikes.map { transform(it) },
            documentation = transform(enumEntry.documentation),
        )
    }

    private fun TransformContext.transform(classlike: DClasslike): DClasslike = with(TransformContext(classlike)) {
        val documentation = transform(classlike.documentation)
        return when (classlike) {
            is DClass -> classlike.copy(
                functions = classlike.functions.map { transform(it) },
                properties = classlike.properties.map { transform(it) },
                classlikes = classlike.classlikes.map { transform(it) },
                constructors = classlike.constructors.map { transform(it) },
                companion = classlike.companion?.let { transform(it) } as DObject?,
                generics = classlike.generics.map { transform(it) },
                documentation = documentation,
            )
            is DEnum -> classlike.copy(
                entries = classlike.entries.map { transform(it) },
                functions = classlike.functions.map { transform(it) },
                properties = classlike.properties.map { transform(it) },
                classlikes = classlike.classlikes.map { transform(it) },
                constructors = classlike.constructors.map { transform(it) },
                companion = classlike.companion?.let { transform(it) } as DObject?,
                documentation = documentation,
            )
            is DInterface -> classlike.copy(
                functions = classlike.functions.map { transform(it) },
                properties = classlike.properties.map { transform(it) },
                classlikes = classlike.classlikes.map { transform(it) },
                generics = classlike.generics.map { transform(it) },
                documentation = documentation,
            )
            is DObject -> classlike.copy(
                functions = classlike.functions.map { transform(it) },
                properties = classlike.properties.map { transform(it) },
                classlikes = classlike.classlikes.map { transform(it) },
                documentation = documentation,
            )
            is DAnnotation -> classlike.copy(
                functions = classlike.functions.map { transform(it) },
                properties = classlike.properties.map { transform(it) },
                classlikes = classlike.classlikes.map { transform(it) },
                constructors = classlike.constructors.map { transform(it) },
                generics = classlike.generics.map { transform(it) },
                companion = classlike.companion?.let { transform(it) } as DObject?,
                documentation = documentation,
            )
        }
    }
}