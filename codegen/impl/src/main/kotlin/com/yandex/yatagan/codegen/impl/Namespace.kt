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

package com.yandex.yatagan.codegen.impl

import com.yandex.yatagan.lang.Annotation
import com.yandex.yatagan.lang.compiled.ArrayNameModel
import com.yandex.yatagan.lang.compiled.ClassNameModel
import com.yandex.yatagan.lang.compiled.CtTypeNameModel
import com.yandex.yatagan.lang.compiled.InvalidNameModel
import com.yandex.yatagan.lang.compiled.KeywordTypeNameModel
import com.yandex.yatagan.lang.compiled.ParameterizedNameModel
import com.yandex.yatagan.lang.compiled.WildcardNameModel
import java.util.Locale

private fun String.capitalize() = replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
private fun String.decapitalize() = replaceFirstChar { it.lowercase(Locale.US) }

private fun Iterable<String>.joinWithCamelCase(firstCapital: Boolean? = null): String {
    val joined = joinToString(separator = "", transform = String::capitalize)
    return when (firstCapital) {
        null -> joined
        true -> joined.capitalize()
        false -> joined.decapitalize()
    }
}

private val NonIdentifierCharacters = "[^a-zA-Z$0-9]".toRegex()

private fun <T> singleValueIterator(value: T) = object : Iterator<T> {
    var expired = false

    override fun hasNext() = !expired

    override fun next(): T {
        if (expired) throw NoSuchElementException()
        expired = true
        return value
    }
}

internal class Namespace(
    private val prefix: String = "",
) {
    private val names = hashMapOf<String, Int>().withDefault { 0 }

    private fun obtainNameImpl(
        nameModel: CtTypeNameModel,
    ): Iterator<String> {
        return when (nameModel) {
            is ClassNameModel -> iterator {
                yield(nameModel.simpleNames.joinWithCamelCase())
                val fullyQualified = nameModel.packageName.split(".") + nameModel.simpleNames
                yield(fullyQualified.joinWithCamelCase())
            }
            is ParameterizedNameModel -> iterator {
                val nameGenerators = mutableListOf(obtainNameImpl(nameModel.raw))
                nameModel.typeArguments.mapTo(nameGenerators, ::obtainNameImpl)
                val variants = nameGenerators.mapTo(arrayListOf(), Iterator<String>::next)
                while (true) {
                    yield(variants.joinWithCamelCase())
                    val available = nameGenerators.withIndex().find { it.value.hasNext() }
                    if (available != null) {
                        variants[available.index] = available.value.next()
                    } else break
                }
            }
            is WildcardNameModel ->
                nameModel.lowerBound?.let(::obtainNameImpl)
                    ?: nameModel.upperBound?.let(::obtainNameImpl)
                    ?: singleValueIterator("Any")
            is ArrayNameModel -> iterator {
                for (name in obtainNameImpl(nameModel.elementType)) {
                    yield("arrayOf$name")
                }
            }
            is KeywordTypeNameModel -> singleValueIterator(nameModel.name)
            is InvalidNameModel -> singleValueIterator(nameModel.toString())
        }
    }

    fun name(
        string: String,
        firstCapital: Boolean = false,
    ): String {
        val name = sequenceOf(this.prefix, string).asIterable().joinWithCamelCase(firstCapital = firstCapital)
        if (name !in names) {
            names[name] = 0
            return name
        }

        // Fallback to duplicate count of the last yielded name
        val count = names.merge(name, 0) { old, _ -> old + 1 }
        return name + count
    }

    fun name(
        nameModel: CtTypeNameModel,
        qualifier: Annotation? = null,
        prefix: String = "",
        suffix: String = "",
        firstCapital: Boolean = false,
    ): String {
        val qualifierString = qualifier?.toString()?.split(NonIdentifierCharacters)?.joinWithCamelCase() ?: ""
        val variants: Iterator<String> = obtainNameImpl(nameModel)
        var name: String? = null
        for (nameVariant in variants) {
            name = sequenceOf(this.prefix, prefix, qualifierString, nameVariant, suffix).asIterable()
                .joinWithCamelCase(firstCapital = firstCapital)
            if (name !in names) {
                names[name] = 0
                return name
            }
        }

        // Fallback to duplicate count of the last yielded name
        val count = names.merge(name!!, 0) { old, _ -> old + 1 }
        return name + count
    }
}
