package com.yandex.daggerlite.generator

import com.squareup.javapoet.ClassName
import com.yandex.daggerlite.generator.poetry.TypeSpecBuilder
import com.yandex.daggerlite.generator.poetry.buildClass
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PRIVATE
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC
import javax.lang.model.element.Modifier.VOLATILE

internal class ScopedProviderGenerator(
    private val componentImplName: ClassName,
    private val useDoubleChecking: Boolean,
) : ComponentGenerator.Contributor {
    val name: ClassName = componentImplName.nestedClass(if (useDoubleChecking) "DoubleCheck" else "CachingProvider")

    override fun generate(builder: TypeSpecBuilder) {
        builder.nestedType {
            buildClass(name) {
                implements(Names.Lazy)
                modifiers(PRIVATE, STATIC, FINAL)
                field(componentImplName, "mFactory") { modifiers(PRIVATE, FINAL) }
                field(ClassName.INT, "mIndex") { modifiers(PRIVATE, FINAL) }
                field(ClassName.OBJECT, "mValue") {
                    modifiers(PRIVATE)
                    if (useDoubleChecking) {
                        modifiers(VOLATILE)
                    }
                }
                constructor {
                    parameter(componentImplName, "factory")
                    parameter(ClassName.INT, "index")
                    +"mFactory = factory"
                    +"mIndex = index"
                }

                method("get") {
                    modifiers(PUBLIC)
                    annotation<Override>()
                    returnType(ClassName.OBJECT)
                    +"%T local = mValue".formatCode(ClassName.OBJECT)
                    controlFlow("if (local == null)") {
                        if (useDoubleChecking) {
                            controlFlow("synchronized (this)") {
                                +"local = mValue"
                                controlFlow("if (local == null)") {
                                    +"local = mFactory.%N(mIndex)".formatCode(SlotSwitchingGenerator.FactoryMethodName)
                                    +"mValue = local"
                                }
                            }
                        } else {
                            +"local = mFactory.%N(mIndex)".formatCode(SlotSwitchingGenerator.FactoryMethodName)
                            +"mValue = local"
                        }
                    }
                    +"return local"
                }
            }
        }
    }
}