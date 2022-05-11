package com.yandex.daggerlite.gradle

import com.yandex.daggerlite.gradle.PatchModuleDocTask.SyntaxError
import java.io.File
import kotlin.reflect.KProperty

internal class CheckModuleDocHasValidHeader(
    private val projectName: String,
    private val isRootProject: Boolean,
) : PatchModuleDocTask.DocSyntaxExtension {
    override val syntaxRegex = "^.*".toRegex()  // match the first line

    override fun substitute(match: MatchResult): String {
        val header = match.value

        val expectedHeader = "# Module $projectName"
        if (!isRootProject && header.trim() != expectedHeader) {
            throw SyntaxError("Module documentation must start with `$expectedHeader` for dokka to recognise it")
        }

        return header
    }
}

/**
 * Simple one-pass macro substitutor.
 * Replaces %%<macro-name>%% according with the given macros map.
 */
internal class MacroSyntaxExtension(
    private val macros: Map<String, String>,
) : PatchModuleDocTask.DocSyntaxExtension {
    override val syntaxRegex = """%%(.+?)%%""".toRegex()

    override fun substitute(match: MatchResult): String {
        val (macro) = match.destructured
        return macros[macro] ?: throw SyntaxError("No macro definition available for `$macro`")
    }
}

/**
 * Supports module doc reference by its full path:
 * `[:<module-path:child>]`
 */
internal class ModuleReferenceSyntaxExtension(
    currentProjectPath: String,
) : PatchModuleDocTask.DocSyntaxExtension {
    private val currentPath = File(currentProjectPath.replace(':', '/'))

    override val syntaxRegex = """\[(?<module>:.*?)\]""".toRegex()

    override fun substitute(match: MatchResult): String {
        val module by match
        val relativeModulePath = File(module.replace(':', '/'))
            .resolve("index.html")
            .toRelativeString(currentPath).escapeCapitals()
        return """<b><a href="./$relativeModulePath">$module</a></b>"""
    }
}

/**
 * Supports referencing classes/members in different modules.
 * `[<display-name>][<package>/<class>#<member>@<module-path>]`
 */
internal class ForeignClassReferenceSyntaxExtension(
    private val currentProjectPath: String,
) : PatchModuleDocTask.DocSyntaxExtension {
    private val currentPath = File(currentProjectPath.replace(':', '/'))

    override val syntaxRegex =
        """(?:\[(?<display>.+?)\])?\[(?<packaje>.*?)/(?<clazz>.+?)(?:#(?<member>.+?))?@(?<module>:.*?)\]"""
            .toRegex()

    override fun substitute(match: MatchResult): String {
        val display by match
        val packaje by match
        val clazz by match
        val member by match
        val module by match

        var path =  File(module.replace(':', '/')).resolve(packaje)
        for (simpleName in clazz.splitToSequence('.')) {
            path = path.resolve(simpleName)
        }
        path = path.resolve(if (member.isNotEmpty()) member + ".html"  else "index.html")
        var relativePath = path.toRelativeString(currentPath).escapeCapitals()
        val displayName = display.ifEmpty { "$packaje.$clazz" }
        return """<a href="./$relativePath">`$displayName`</a>"""
    }
}

/**
 * Appends links with generated anchors to allow linking to headers.
 *
 * Generates header tree - [document].
 */
internal class HeaderReferenceSyntaxExtension(
): PatchModuleDocTask.DocSyntaxExtension {
    override val syntaxRegex = """^\s*?(?<level>#{1,6})\s?(?<text>.*)${'$'}""".toRegex(RegexOption.MULTILINE)

    class Header(
        val id: String,
        val level: Int,
        val text: String,
        val parent: Header?,
    ) {
        val children = arrayListOf<Header>()
    }

    val document = Header(id = "", level = 0, text = "", parent = null)

    private var previousTitle = document
    private var firstHeader = true

    override fun substitute(match: MatchResult): String {
        val level by match
        val text by match
        val levelNumber = level.length

        val id = text.makeTitleId()
        previousTitle = when {
            levelNumber > previousTitle.level -> {
                Header(id, levelNumber, text, parent = previousTitle).also {
                    previousTitle.children += it
                }
            }
            levelNumber == previousTitle.level -> {
                Header(id, levelNumber, text, parent = previousTitle.parent).also {
                    previousTitle.parent!!.children += it
                }
            }
            else -> {
                var title = previousTitle.parent
                while(title != null && title.level < levelNumber) {
                    title = title.parent
                }
                Header(id, levelNumber, text, parent = title).also {
                    title!!.children += it
                }
            }
        }

        if (firstHeader) {
            // Do not replace first title, as it is module directive
            firstHeader = false
            return match.value
        }

        return "\n<h$levelNumber id=\"$id\">$text</h$levelNumber>\n"
    }
}

/**
 * Generates table of contents on `{{TOC}}` marker.
 * Depends on [HeaderReferenceSyntaxExtension] being run before.
 */
internal class TableOfContentsSyntaxExtension(
    private val headers: HeaderReferenceSyntaxExtension,
): PatchModuleDocTask.DocSyntaxExtension {
    override val syntaxRegex = """\{\{TOC\}\}""".toRegex()

    override fun substitute(match: MatchResult): String {
        return buildString {
            appendLine()
            for (child in headers.document.children) {
                addTitle(child)
            }
            appendLine()
        }
    }

    private fun StringBuilder.addTitle(header: HeaderReferenceSyntaxExtension.Header) {
        val count = (header.level - 1) * 2
        repeat(count) {
            append(' ')
        }
        append("- ")
        append("<a href=\"#")
        append(header.id)
        append("\">")
        append(header.text)
        appendLine("</a>")
        for (child in header.children) {
            addTitle(child)
        }
    }
}

private operator fun MatchResult.getValue(thisRef: Any?, property: KProperty<*>): String {
    return groups[property.name]?.value ?: ""
}

private fun String.escapeCapitals(): String = buildString(length + 4) {
    for (c in this@escapeCapitals) {
        if (c.isUpperCase()) {
            append('-').append(c.toLowerCase())
        } else {
            append(c)
        }
    }
}

private fun String.makeTitleId(): String {
    return buildString(length) {
        for (c in this@makeTitleId) {
            if (c.isLetterOrDigit()) {
                append(c)
            }
        }
    }
}