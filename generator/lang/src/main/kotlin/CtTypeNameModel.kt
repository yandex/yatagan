package com.yandex.daggerlite.generator.lang

/**
 *  Represents a [com.yandex.daggerlite.generator.lang.CtTypeLangModel] via its name and type arguments.
 */
sealed interface CtTypeNameModel

data class ClassNameModel(
    val packageName: String,
    val simpleNames: List<String>,
) : CtTypeNameModel {
    init {
        require(simpleNames.isNotEmpty()) { "class with no name?" }
    }

    override fun toString() = buildString {
        append(packageName).append('.')
        simpleNames.joinTo(this, separator = ".")
    }
}

data class ParameterizedNameModel(
    val raw: ClassNameModel,
    val typeArguments: List<CtTypeNameModel>,
) : CtTypeNameModel {
    override fun toString() = buildString {
        append(raw)
        append('<')
        typeArguments.joinTo(this, separator = ".")
        append('>')
    }
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

    override fun toString() = buildString {
        append("?")
        upperBound?.let { append(" extends ").append(it) }
        lowerBound?.let { append(" super ").append(it) }
    }

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