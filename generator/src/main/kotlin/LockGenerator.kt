package com.yandex.daggerlite.generator

import com.squareup.javapoet.ClassName
import com.yandex.daggerlite.generator.poetry.TypeSpecBuilder
import com.yandex.daggerlite.generator.poetry.buildClass
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PRIVATE
import javax.lang.model.element.Modifier.STATIC

internal class LockGenerator(
    componentImplName: ClassName,
) : ComponentGenerator.Contributor {
    val name: ClassName = componentImplName.nestedClass("UninitializedLock")

    override fun generate(builder: TypeSpecBuilder) {
        builder.nestedType {
            buildClass(name) {
                modifiers(PRIVATE, STATIC, FINAL)
            }
        }
    }
}