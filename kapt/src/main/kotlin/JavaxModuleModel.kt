package com.yandex.daggerlite.compiler

import com.yandex.daggerlite.core.AliasBinding
import com.yandex.daggerlite.core.BaseBinding
import com.yandex.daggerlite.core.ClassNameModel
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.FunctionNameModel
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.ProvisionBinding
import dagger.Binds
import dagger.Module
import dagger.Provides
import javax.lang.model.element.Element
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

class JavaxModuleModel(
    private val element: Element,
    private val types: Types,
    private val elements: Elements,
    ) : ModuleModel {
    private val annotation = element.getAnnotationMirror<Module>()
    override val subcomponents: Collection<ComponentModel> =
        annotation.typesValue("subcomponents").map{ JavaxComponentModel(it, types, elements) }.toList()
    override val name: ClassNameModel = classNameModel(element.asType())

    override val bindings: Collection<BaseBinding> = run {
        // todo: кажется так мы получим только методы объявленные внутри класса, и нужно заменить на allMethods
        element.asTypeElement().allMethods(types, elements).mapNotNull { method ->

            when {
                method.isAnnotatedWith<Binds>() -> AliasBinding(
                    target = JavaxNodeModel(method.returnType),
                    // todo: проверить что один параметр, проверить что он наследуется от таргета
                    source = JavaxNodeModel(method.typeParameters.first().asType())
                )
                method.isAnnotatedWith<Provides>() -> ProvisionBinding(
                    target = JavaxNodeModel(method.returnType),
                    scope = element.qualify<javax.inject.Scope>(),
                    provider = FunctionNameModel(
                        name,
                        method.simpleName.toString()
                    ),
                    params = method.parameters.map { NodeModel.Dependency(JavaxNodeModel(it.asType())) }
                )
                else -> null
            }
        }
    }
}