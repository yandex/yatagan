package com.yandex.daggerlite.core

// MAYBE: this most likely requires generalization to support non-class types: builtin types, arrays, etc..
data class ClassNameModel(
    val packageName: String,
    val simpleNames: List<String>,
    val typeArguments: List<ClassNameModel>,
) {
    init {
        require(simpleNames.isNotEmpty()) { "class with no name?" }
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
}

sealed interface CallableNameModel

sealed interface MemberCallableNameModel : CallableNameModel {
    val ownerName: ClassNameModel
    val isOwnerKotlinObject: Boolean
}

data class FunctionNameModel(
    override val ownerName: ClassNameModel,
    override val isOwnerKotlinObject: Boolean,
    val function: String,
) : MemberCallableNameModel {
    override fun toString() = "$ownerName.$function()"
}

data class ConstructorNameModel(
    val type: ClassNameModel,
) : CallableNameModel {
    override fun toString() = "$type()"
}

data class PropertyNameModel(
    override val ownerName: ClassNameModel,
    override val isOwnerKotlinObject: Boolean,
    val property: String
) : MemberCallableNameModel {
    override fun toString() = "$ownerName.$property:"
}