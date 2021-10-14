package com.yandex.dagger3.generator

import com.squareup.javapoet.ClassName
import com.yandex.dagger3.generator.poetry.Names
import com.yandex.dagger3.generator.poetry.TypeSpecBuilder
import com.yandex.dagger3.generator.poetry.buildClass
import javax.lang.model.element.Modifier

internal class ScopedProviderGenerator(
    private val componentImplName: ClassName,
) : ComponentGenerator.Contributor {
    val name: ClassName = componentImplName.nestedClass("CachingProvider")

    override fun generate(builder: TypeSpecBuilder) {
        builder.nestedType {
            buildClass(name) {
                implements(Names.Lazy)
                modifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                field(componentImplName, "mFactory") { modifiers(Modifier.PRIVATE, Modifier.FINAL) }
                field(ClassName.INT, "mIndex") { modifiers(Modifier.PRIVATE, Modifier.FINAL) }
                field(ClassName.OBJECT, "mValue") { modifiers(Modifier.PRIVATE) }
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
                    +"%T local = mValue".formatCode(ClassName.OBJECT)
                    controlFlow("if (local == null)") {
                        +"local = mFactory._new(mIndex)"
                        +"mValue = local"
                    }
                    +"return local"
                }
            }
        }
    }
}