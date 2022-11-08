package com.yandex.yatagan.testing.doc_testing

import org.jetbrains.dokka.base.parsers.MarkdownParser
import org.jetbrains.dokka.model.doc.*
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.configuration
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

internal class CodeBlockTransformer(
    private val context: DokkaContext,
) : TagWrapperTransformer {
    private val outputPath = configuration<DLDokkaPlugin, DLDokkaConfiguration>(context)
        ?.codeBlockTestsOutputDirectory?.let { Path(it) }

    override fun transform(tagWrapper: TagWrapper, context: TransformContext): TagWrapper {
        return when (tagWrapper) {
            is Description -> tagWrapper.copy(root = TransformAdapter(context).transform(tagWrapper.root))
            else -> tagWrapper
        }
    }

    private inner class TransformAdapter(
        val transformContext: TransformContext,
    ) : DocTagTransformAdapter() {
        override fun transformCodeBlock(docTag: CodeBlock): DocTag {
            if (docTag.params["lang"] != "kotlin") {
                return docTag
            }

            val parsedLines = docTag.children.filterIsInstance<Text>().asSequence()
                .map { line ->
                    val trimmed = line.body.trim()
                    when {
                        trimmed.startsWith("/*@*/") -> CodeLine.ImplicitCode(trimmed.substring(5))
                        trimmed.startsWith("//") -> CodeLine.Comment(trimmed.substring(2))
                        else -> CodeLine.Code(line.body)
                    }
                }.toList()

            outputPath?.let { outputPath ->
                val testName = makeTestName(source = transformContext)
                context.logger.info("Writing code block test: $testName")
                val sourceDir = outputPath.resolve(testName).also { it.createDirectories() }
                val output = sourceDir.resolve("TestCase.kt")
                output.writeText(text = buildString {
                    for (parsedLine in parsedLines) {
                        when (parsedLine) {
                            is CodeLine.ImplicitCode, is CodeLine.Code -> {
                                appendLine(parsedLine.contents)
                            }
                            is CodeLine.Comment -> Unit  // skip
                        }
                    }
                })
            }

            return Div(children = buildList {
                var state: CodeDocWriterState = CodeDocWriterState.Code()
                for (parsedLine in parsedLines) {
                    state = when (parsedLine) {
                        is CodeLine.Code -> state.accept(code = parsedLine, output = this)
                        is CodeLine.Comment -> state.accept(comment = parsedLine, output = this)
                        is CodeLine.ImplicitCode -> state  // hide in docs
                    }
                }
                state.flush(into = this)
            })
        }
    }

    private sealed interface CodeDocWriterState {
        fun flush(into: MutableList<DocTag>)
        fun accept(code: CodeLine.Code, output: MutableList<DocTag>): CodeDocWriterState
        fun accept(comment: CodeLine.Comment, output: MutableList<DocTag>): CodeDocWriterState

        class Code(initial: CodeLine.Code? = null) : CodeDocWriterState {
            private val lines = arrayListOf<CodeLine.Code>().apply { initial?.let(::add) }

            override fun accept(code: CodeLine.Code, output: MutableList<DocTag>): CodeDocWriterState {
                lines += code
                return this
            }

            override fun accept(comment: CodeLine.Comment, output: MutableList<DocTag>): CodeDocWriterState {
                flush(output)
                return Comment(initial = comment)
            }

            override fun flush(into: MutableList<DocTag>) {
                if (lines.isNotEmpty()) {
                    into.add(CodeBlock(children = buildList {
                        for (i in lines.indices) {
                            add(Text(body = lines[i].contents))
                            if (i != lines.lastIndex) add(Br)
                        }
                    }))
                }
            }
        }

        class Comment(initial: CodeLine.Comment) : CodeDocWriterState {
            private val lines = arrayListOf(initial)
            private val mdParser = MarkdownParser(
                externalDri = { null },
                kdocLocation = null,
            )

            override fun accept(comment: CodeLine.Comment, output: MutableList<DocTag>): CodeDocWriterState {
                lines += comment
                return this
            }

            override fun accept(code: CodeLine.Code, output: MutableList<DocTag>): CodeDocWriterState {
                flush(output)
                return Code(initial = code)
            }

            override fun flush(into: MutableList<DocTag>) {
                val fullText = lines.joinToString(separator = " ", transform = CodeLine.Comment::contents)
                into.add(
                    Div(
                        children = listOf(
                            Br,
                            mdParser.parseStringToDocNode(fullText),
                        )
                    )
                )
            }
        }
    }

    private sealed interface CodeLine {
        val contents: String

        data class Code(
            override val contents: String,
        ) : CodeLine

        data class Comment(
            override val contents: String,
        ) : CodeLine

        data class ImplicitCode(
            override val contents: String,
        ) : CodeLine
    }

    private fun makeTestName(source: TransformContext): String {
        return source.stack.joinToString(separator = "_") {
            it.name ?: ""
        }
    }
}