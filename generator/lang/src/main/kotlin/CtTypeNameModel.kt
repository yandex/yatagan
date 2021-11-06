package com.yandex.daggerlite.generator.lang

/**
 * Represents a [com.yandex.daggerlite.generator.lang.CtTypeLangModel] via its name and type arguments.
 * MAYBE: this most likely requires generalization to support non-class types: builtin types, arrays, etc..
 */
class CtTypeNameModel(
    val packageName: String,
    val simpleNames: List<String>,
    val typeArguments: List<CtTypeNameModel>,
) {
    init {
        require(simpleNames.isNotEmpty()) { "class with no name?" }
    }
    private val precomputedHash = run {
        var result = packageName.hashCode()
        result = 31 * result + simpleNames.hashCode()
        result = 31 * result + typeArguments.hashCode()
        result
    }

    override fun toString() = buildString {
        append(packageName).append('.')
        simpleNames.joinTo(this, separator = ".")
        if (typeArguments.isNotEmpty()) {
            append('<')
            typeArguments.joinTo(this, separator = ".")
            append('>')
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CtTypeNameModel
        if (precomputedHash != other.precomputedHash) return false
        if (simpleNames != other.simpleNames) return false
        if (typeArguments != other.typeArguments) return false
        if (packageName != other.packageName) return false
        return true
    }

    override fun hashCode(): Int = precomputedHash

    fun withArguments(typeArguments: List<CtTypeNameModel>) = CtTypeNameModel(packageName, simpleNames, typeArguments)
}
