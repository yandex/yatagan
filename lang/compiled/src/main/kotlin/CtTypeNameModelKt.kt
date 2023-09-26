package com.yandex.yatagan.lang.compiled

sealed interface CtTypeNameModelKt

data class ClassNameModelKt(
    val packageName: String,
    val simpleNames: List<String>,
) : CtTypeNameModelKt {
    init {
        require(simpleNames.isNotEmpty()) { "class with no name?" }
    }

    private val asString by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildString {
            if (packageName.isNotEmpty()) {
                append(packageName).append('.')
            }
            simpleNames.joinTo(this, separator = ".")
        }
    }

    override fun toString() = asString
}

sealed interface KotlinTypeArgument {
    sealed interface WithType : KotlinTypeArgument {
        val type: CtTypeNameModelKt
    }

    data class Invariant(override val type: CtTypeNameModelKt) : WithType {
        override fun toString() = type.toString()
    }

    data class In(override val type: CtTypeNameModelKt) : WithType {
        override fun toString() = "in $type"
    }

    data class Out(override val type: CtTypeNameModelKt) : WithType {
        override fun toString() = "out $type"
    }

    object Star : KotlinTypeArgument {
        override fun toString() = "*"
    }
}

data class ParameterizedNameModelKt(
    val raw: ClassNameModel,
    val typeArguments: List<KotlinTypeArgument>,
) : CtTypeNameModelKt {
    private val asString by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildString {
            append(raw)
            append('<')
            typeArguments.joinTo(this, separator = ", ")
            append('>')
        }
    }

    override fun toString() = asString
}
