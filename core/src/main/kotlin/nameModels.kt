package com.yandex.dagger3.core

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
        simpleNames.forEach(this::append)
        if (typeArguments.isNotEmpty()) {
            append('<')
            typeArguments.forEach(this::append)
            append('>')
        }
    }
}

sealed interface CallableNameModel

sealed interface MemberCallableNameModel : CallableNameModel {
    val ownerName: ClassNameModel
}

data class FunctionNameModel(
    override val ownerName: ClassNameModel,
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
    val property: String
) : MemberCallableNameModel {
    override fun toString() = "$ownerName.$property:"
}