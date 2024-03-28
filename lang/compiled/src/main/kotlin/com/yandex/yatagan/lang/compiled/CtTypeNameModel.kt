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

package com.yandex.yatagan.lang.compiled

import kotlin.LazyThreadSafetyMode.PUBLICATION

/**
 *  Represents a [com.yandex.yatagan.lang.compiled.CtTypeBase] via its name and type arguments.
 */
sealed interface CtTypeNameModel

data class ClassNameModel(
    val packageName: String,
    val simpleNames: List<String>,
) : CtTypeNameModel {
    init {
        require(simpleNames.isNotEmpty()) { "class with no name?" }
    }

    private val asString by lazy(PUBLICATION) {
        buildString {
            if (packageName.isNotEmpty()) {
                append(packageName).append('.')
            }
            simpleNames.joinTo(this, separator = ".")
        }
    }

    override fun toString() = asString
}

data class ParameterizedNameModel(
    val raw: ClassNameModel,
    val typeArguments: List<CtTypeNameModel>,
) : CtTypeNameModel {
    private val asString by lazy(PUBLICATION) {
        buildString {
            append(raw)
            append('<')
            typeArguments.joinTo(this, separator = ", ")
            append('>')
        }
    }

    override fun toString() = asString
}

data class WildcardNameModel(
    val upperBound: CtTypeNameModel? = null,
    val lowerBound: CtTypeNameModel? = null,
) : CtTypeNameModel {
    init {
        require(upperBound == null || lowerBound == null) {
            "Can't have both bounds: $upperBound and $lowerBound"
        }
    }

    private val asString by lazy(PUBLICATION) {
        buildString {
            append("?")
            upperBound?.let { append(" extends ").append(it) }
            lowerBound?.let { append(" super ").append(it) }
        }
    }

    override fun toString() = asString

    companion object {
        val Star = WildcardNameModel()
    }
}

data class ArrayNameModel(
    val elementType: CtTypeNameModel,
) : CtTypeNameModel {
    override fun toString() = "$elementType[]"
}

enum class KeywordTypeNameModel : CtTypeNameModel {
    Void,
    Boolean,
    Byte,
    Int,
    Short,
    Long,
    Float,
    Double,
    Char,
    ;

    override fun toString() = name.lowercase()
}

sealed interface InvalidNameModel : CtTypeNameModel {
    class Unresolved(private val hint: String?) : InvalidNameModel {
        override fun toString() = "<unresolved: ${hint ?: "???"}>"
    }

    class Error(private val error: String) : InvalidNameModel {
        override fun toString() = "<error-type: $error>"
    }

    class TypeVariable(private val typeVar: String) : InvalidNameModel {
        init {
            print("dsd")
        }
        override fun toString() = "<unresolved-type-var: $typeVar>"
    }
}