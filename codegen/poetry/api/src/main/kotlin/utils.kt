package com.yandex.yatagan.codegen.poetry

fun ClassName.nestedClass(
    name: String,
) = copy(simpleNames = simpleNames + name)