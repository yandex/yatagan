package com.yandex.daggerlite.lang.compiled

import kotlin.LazyThreadSafetyMode.PUBLICATION

/**
 *  Represents a [com.yandex.daggerlite.lang.compiled.CtType] via its name and type arguments.
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

class ErrorNameModel : CtTypeNameModel {
    // No equals/hashCode overloading - no need.
    override fun toString() = "error.UnresolvedCla$$"

    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}