package com.yandex.daggerlite.generator

import com.squareup.javapoet.ClassName
import com.yandex.daggerlite.generator.poetry.Names
import com.yandex.daggerlite.generator.poetry.TypeSpecBuilder
import com.yandex.daggerlite.generator.poetry.buildClass
import javax.lang.model.element.Modifier

internal class UnscopedProviderGenerator(
    private val componentImplName: ClassName,
) : ComponentGenerator.Contributor {
    val name: ClassName = componentImplName.nestedClass("FactoryImpl")

    override fun generate(builder: TypeSpecBuilder) {
        builder.nestedType {
            buildClass(name) {
                implements(Names.Provider)
                modifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                field(componentImplName, "mFactory") {
                    modifiers(Modifier.PRIVATE, Modifier.FINAL)
                }
                field(ClassName.INT, "mIndex") {
                    modifiers(Modifier.PRIVATE, Modifier.FINAL)
                }
                constructor {
                    parameter(componentImplName, "factory")
                    parameter(ClassName.INT, "index")
                    +"mFactory = factory"
                    +"mIndex = index"
                }
                method("get") {
                    modifiers(Modifier.PUBLIC)
                    annotation<Override>()
                    returnType(ClassName.OBJECT)
                    +"return mFactory._new(mIndex)"
                }
            }
        }
    }
}