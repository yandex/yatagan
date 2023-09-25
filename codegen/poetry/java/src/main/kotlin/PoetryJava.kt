package com.yandex.yatagan.codegen.poetry.java

import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.TypeSpec
import com.yandex.yatagan.codegen.poetry.ClassName
import com.yandex.yatagan.codegen.poetry.Poetry
import com.yandex.yatagan.codegen.poetry.TypeSpecBuilder
import javax.lang.model.element.Modifier

class PoetryJava : Poetry {
    override fun buildClass(name: ClassName, into: Appendable, block: TypeSpecBuilder.() -> Unit) {
        val builder = TypeSpec.classBuilder(JavaClassName(name))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        val spec = TypeSpecBuilderJavaImpl(builder).apply(block).build()
        JavaFile.builder(name.packageName, spec)
            .build().writeTo(into)
    }
}
