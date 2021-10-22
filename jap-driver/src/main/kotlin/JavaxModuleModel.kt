package com.yandex.daggerlite.jap

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

    private val declaration = element.asTypeElement()
    private val annotation = element.getAnnotationMirror<Module>()

    override val subcomponents: Collection<ComponentModel> =
        annotation.typesValue("subcomponents").map { JavaxComponentModel(it, types, elements) }.toList()

    override val name: ClassNameModel = classNameModel(element.asType())

    override val bindings: Collection<BaseBinding>
    override val isInstanceRequired: Boolean

    init {
        val mayRequireInstance = with(declaration) { !isAbstract && !isKotlinObject }
        var isInstanceRequired = false
        bindings = declaration.allMethods(types, elements).mapNotNull { method ->

            when {
                method.isAnnotatedWith<Binds>() -> AliasBinding(
                    target = JavaxNodeModel(method.returnType),
                    source = JavaxNodeModel(method.parameters[0].asType())
                )
                method.isAnnotatedWith<Provides>() -> ProvisionBinding(
                    target = JavaxNodeModel(method.returnType),
                    scope = element.qualify<javax.inject.Scope>(),
                    provider = FunctionNameModel(
                        name,
                        declaration.isKotlinObject,
                        method.simpleName.toString()
                    ),
                    params = method.parameters.map { NodeModel.Dependency(JavaxNodeModel(it.asType())) },
                    requiredModuleInstance = this@JavaxModuleModel.takeIf { mayRequireInstance && !method.isStatic }
                        ?.also { isInstanceRequired = true }
                )
                else -> null
            }
        }

        this.isInstanceRequired = isInstanceRequired
    }

    override fun toString() = name.toString()

    override fun equals(other: Any?) = (other as?  JavaxModuleModel)?.name == name

    override fun hashCode() = name.hashCode()
}