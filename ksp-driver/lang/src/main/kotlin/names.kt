package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.KSDeclaration

internal data class ClassName(val packageName: String, val qualifiedName: String) {
    // MAYBE: use KSName api instead of string manipulation.
    val simpleNames get() = qualifiedName
        .substring(startIndex = packageName.length + 1)
        .split('.')
}

private fun <T> ClassName(_class: Class<T>) = ClassName(_class.packageName, _class.name)

internal fun KSDeclaration.resolveJavaTypeName(): ClassName {
    return when (qualifiedName!!.asString()) {
        "kotlin.String" -> return Names.String
        // MAYBE: it's not always necessary to use wrapper types?
        "kotlin.Int" -> return Names.Integer
        "kotlin.Long" -> return Names.Long
        "kotlin.Boolean" -> return Names.Boolean
        "kotlin.Char" -> return Names.Character
        "kotlin.Byte" -> return Names.Byte
        "kotlin.Any" -> return Names.Object
        else -> ClassName(packageName.asString(), qualifiedName!!.asString())
    }
}

internal object Names {
    val String: ClassName = ClassName(java.lang.String::class.java)
    val Integer: ClassName = ClassName(java.lang.Integer::class.java)
    val Long: ClassName = ClassName(java.lang.Long::class.java)
    val Boolean: ClassName = ClassName(java.lang.Boolean::class.java)
    val Character: ClassName = ClassName(java.lang.Character::class.java)
    val Byte: ClassName = ClassName(java.lang.Byte::class.java)
    val Object: ClassName = ClassName(java.lang.Object::class.java)
}