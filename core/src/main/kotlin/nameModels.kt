package com.yandex.dagger3.core

data class NameModel(
    val packageName: String,
    val qualifiedName: String,
    val simpleName: String,
    val typeArguments: List<NameModel>,
) {
    override fun toString() = "$qualifiedName<${typeArguments.joinToString(",")}>"
}

sealed interface CallableNameModel

sealed interface MemberCallableNameModel : CallableNameModel {
    val ownerName: NameModel
}

data class FunctionNameModel(
    override val ownerName: NameModel,
    val function: String,
) : MemberCallableNameModel {
    override fun toString() = "$ownerName.$function()"
}

data class ConstructorNameModel(
    val type: NameModel,
) : CallableNameModel {
    override fun toString() = "$type()"
}

data class PropertyNameModel(
    override val ownerName: NameModel,
    val property: String
) : MemberCallableNameModel {
    override fun toString() = "$ownerName.$property:"
}