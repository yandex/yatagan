package com.yandex.yatagan.codegen.poetry

data class ClassName(
    val packageName: String,
    val simpleNames: List<String>,
) : TypeName