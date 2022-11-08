package com.yandex.daggerlite.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.mapProperty
import javax.inject.Inject

/**
 * Extends Dokka Markdown Module docs. Supports the following extensions:
 * - [MacroSyntaxExtension]
 * - [ModuleReferenceSyntaxExtension]
 * - [ForeignClassReferenceSyntaxExtension]
 * - [HeaderReferenceSyntaxExtension]
 * - [TableOfContentsSyntaxExtension]
 */
abstract class PatchModuleDocTask @Inject constructor(
    objects: ObjectFactory,
) : DefaultTask() {

    @get:InputFile
    val inputFile: RegularFileProperty = objects.fileProperty()

    @get:OutputFile
    val outputFile: RegularFileProperty = objects.fileProperty().apply {
        convention(project.layout.buildDirectory.file("$name.md"))
    }

    @get:Input
    val macros: MapProperty<String, String> = objects.mapProperty()

    private val projectName: String
    private val isRoot = project == project.rootProject
    private val projectPath = project.path

    init {
        projectName = project.extensions.findByType<PublishingExtension>()?.let {
            val mainArtifactPublication = it.publications.getByName("main") as MavenPublication
            mainArtifactPublication.artifactId
        } ?: project.name
    }

    internal interface DocSyntaxExtension {
        val syntaxRegex: Regex

        @Throws(SyntaxError::class)
        fun substitute(match: MatchResult): String
    }

    internal class SyntaxError(override val message: String) : Exception()

    @TaskAction
    fun action() {
        val headerReferenceSyntaxExtension = HeaderReferenceSyntaxExtension()
        val syntaxExtensions = listOf<DocSyntaxExtension>(
            CheckModuleDocHasValidHeader(
                projectName = projectName,
                isRootProject = isRoot,
            ),
            MacroSyntaxExtension(
                macros = macros.get(),
            ),
            ModuleReferenceSyntaxExtension(
                currentProjectPath = projectPath,
            ),
            ForeignClassReferenceSyntaxExtension(
                currentProjectPath = projectPath,
            ),
            headerReferenceSyntaxExtension,
            TableOfContentsSyntaxExtension(
                headerReferenceSyntaxExtension,
            )
        )

        var documentText = inputFile.get().asFile.readText()

        var hasError = false
        for (syntaxExtension in syntaxExtensions) {
            documentText = syntaxExtension.syntaxRegex.replace(documentText) { match ->
                try {
                    syntaxExtension.substitute(match)
                } catch (e: SyntaxError) {
                    val lineNumber = documentText
                        .substring(0..match.range.start)
                        .count { it == '\n' } + 1

                    logger.error("Error: file://${inputFile.get().asFile}:$lineNumber: ${e.message}")
                    hasError = true
                    "<error>"
                }
            }
        }

        if (hasError) {
            throw RuntimeException("Errors occured while processing doc syntax extension")
        }

        outputFile.get().asFile.writeText(documentText)
    }
}