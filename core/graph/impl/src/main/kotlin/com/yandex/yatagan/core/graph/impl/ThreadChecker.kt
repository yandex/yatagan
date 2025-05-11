package com.yandex.yatagan.core.graph.impl

import com.yandex.yatagan.core.graph.ThreadChecker
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.isKotlinObject
import com.yandex.yatagan.lang.langFactory
import com.yandex.yatagan.lang.scope.LexicalScope
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.TextColor
import com.yandex.yatagan.validation.format.appendRichString
import com.yandex.yatagan.validation.format.buildRichString
import com.yandex.yatagan.validation.format.reportError

object ThreadChecker {
    operator fun invoke(
        lexicalScope: LexicalScope,
        threadCheckerClassName: String?
    ): ThreadChecker = ThreadCheckerImpl(lexicalScope, threadCheckerClassName)

    const val OPTION_NAME = "yatagan.threadCheckerClassName"
    const val METHOD_NAME = "assertThreadAccess"

    private sealed interface Parsed {
        class Ok(val method: Method?) : Parsed
        class Error(
            val errors: List<String>,
        ) : Parsed
    }

    private class ThreadCheckerImpl(
        private val lexicalScope: LexicalScope,
        private val threadCheckerClassName: String?,
    ) : ThreadChecker {
        private val parsed: Parsed by lazy(::parse)

        override val assertThreadAccessMethod: Method?
            get() = when(val value = parsed) {
                is Parsed.Error -> null
                is Parsed.Ok -> value.method
            }

        override fun validate(validator: Validator) {
            when(val value = parsed) {
                is Parsed.Error -> value.errors.forEach { error ->
                    validator.reportError(Strings.Errors.invalidOptionValue(error))
                }
                is Parsed.Ok -> {}
            }
        }

        override fun toString(childContext: MayBeInvalid?) = buildRichString {
            append("Option ")
            append(OPTION_NAME)
            append(" = ")
            appendRichString {
                color = TextColor.Cyan
                append(threadCheckerClassName)
            }
        }

        private fun parse(): Parsed {
            threadCheckerClassName ?: return Parsed.Ok(null)

            val match = CanonicalClassNameBestEffort.matchEntire(threadCheckerClassName)
            if (match == null) {
                return Parsed.Error(listOf("$threadCheckerClassName is not a valid canonical class name"))
            }
            val simpleNames = match.groups[SIMPLE_NAMES_GROUP]!!.value.split('.')
            val declaration = lexicalScope.ext.langFactory.getTypeDeclaration(
                packageName = match.groups[PACKAGE_NAME_GROUP]!!.value,
                simpleName = simpleNames.first(),
                simpleNames = simpleNames.drop(1).toTypedArray(),
            )
            if (declaration == null) {
                return Parsed.Error(listOf("Unable to find class $threadCheckerClassName"))
            }

            val method = declaration.methods.find { it.name == METHOD_NAME }
            if (method == null) {
                return Parsed.Error(listOf("Unable to find method `$METHOD_NAME` in class $threadCheckerClassName"))
            }
            val errors = buildList {
                if (!method.owner.isKotlinObject && !method.isStatic) {
                    add("Method `$METHOD_NAME` in class `$threadCheckerClassName` must be static or be in a Kotlin object")
                }
                if (method.parameters.any()) {
                    add("Method `$METHOD_NAME` in class `$threadCheckerClassName` must have no parameters")
                }
                if (!method.isEffectivelyPublic) {
                    add("Method `$METHOD_NAME` in class `$threadCheckerClassName` must be public/internal")
                }
            }
            if (errors.isNotEmpty()) {
                return Parsed.Error(errors)
            }

            return Parsed.Ok(method)
        }
    }

    private const val PACKAGE_NAME_GROUP = "pn"
    private const val SIMPLE_NAMES_GROUP = "sn"
    private val CanonicalClassNameBestEffort =
        """(?<$PACKAGE_NAME_GROUP>.*?)\.?(?<$SIMPLE_NAMES_GROUP>(?<=\.|^)[A-Z].*)""".toRegex()
}