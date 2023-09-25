package com.yandex.yatagan.codegen.poetry

import com.yandex.yatagan.lang.Type

sealed interface TypeName {
    data class Inferred(val type: Type) : TypeName

    data class Nullable(val type: TypeName) : TypeName

    data object AnyObject : TypeName

    data class TypeVariable(val name: String) : TypeName

    data class ArrayList(val elementType: TypeName) : TypeName
    data class HashSet(val elementType: TypeName) : TypeName
    data class HashMap(val keyType: TypeName, val valueType: TypeName): TypeName
    data class Class(val t: TypeName): TypeName

    data class AutoBuilder(val t: TypeName): TypeName

    data object AssertionError : TypeName
    data object Boolean : TypeName
    data object Byte : TypeName
    data object Int : TypeName

    data object ThreadAssertions : TypeName
    data object Optional : TypeName
    data object Lazy : TypeName
}