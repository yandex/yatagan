package com.yandex.yatagan.codegen.poetry.kotlin

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import com.yandex.yatagan.codegen.poetry.ClassName
import com.yandex.yatagan.codegen.poetry.Poetry
import com.yandex.yatagan.codegen.poetry.TypeSpecBuilder

class PoetryKotlin : Poetry {
    override fun buildClass(name: ClassName, into: Appendable, block: TypeSpecBuilder.() -> Unit) {
        val className = KotlinClassName(name)
        FileSpec.builder(className)
            .addAnnotation(AnnotationSpec.builder(KotlinClassName("kotlin", "OptIn"))
                .addMember("%T::class", KotlinClassName("com.yandex.yatagan.internal", "YataganInternal"))
                .build())
            .addType(TypeSpecBuilderKotlinImpl(TypeSpec.classBuilder(className)).apply(block).build())
            .build()
            .writeTo(into)
    }
}