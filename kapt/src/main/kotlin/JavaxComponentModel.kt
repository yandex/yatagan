package com.yandex.daggerlite.compiler

import com.yandex.daggerlite.core.ClassNameModel
import com.yandex.daggerlite.core.ComponentDependencyFactoryInput
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.FunctionNameModel
import com.yandex.daggerlite.core.InstanceBinding
import com.yandex.daggerlite.core.ModuleInstanceFactoryInput
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeModel.Dependency.Kind
import com.yandex.daggerlite.core.ProvisionBinding
import dagger.BindsInstance
import dagger.Component
import dagger.Module
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

class JavaxComponentModel(
    element: TypeElement,
    types: Types,
    elements: Elements,
) : ComponentModel() {
    private val impl = element.getAnnotationMirror<Component>()

    override val name: ClassNameModel = classNameModel(element)
    override val modules: Set<ModuleModel> =
        impl.typesValue("modules").map { JavaxModuleModel(it, types, elements) }.toSet()
    override val isRoot: Boolean = element.isRoot
    override val scope: ProvisionBinding.Scope? = element.qualify<javax.inject.Scope>()
    override val dependencies: Set<ComponentModel> =
        impl.typesValue("dependencies").map { JavaxComponentModel(it, types, elements) }.toSet()

    override val entryPoints: Set<EntryPoint> = element.allMethods(types, elements).map { method ->
        val returnType = method.returnType
        val kind = when (returnType.asTypeElement().toString()) {
            dagger.Lazy::class.qualifiedName -> Kind.Lazy
            javax.inject.Provider::class.qualifiedName -> Kind.Provider
            else -> Kind.Direct
        }

        val entryType = when (kind) {
            Kind.Direct -> returnType
            else -> returnType.asDeclaredType().typeArguments.first()
        }

        EntryPoint(
            FunctionNameModel(name, method.simpleName.toString()),
            Dependency(JavaxNodeModel(entryType), kind)
        )
    }.toSet()

    override val factory: ComponentFactoryModel? = run {
        val declaration = element.types().find { it.isAnnotatedWith<Component.Factory>() } ?: return@run null
        val createMethod = declaration.methods().find { it.simpleName.toString() == "create" } ?: return@run null

        object : ComponentFactoryModel() {
            override val name: ClassNameModel = classNameModel(declaration)
            override val createdComponent: ComponentModel = this@JavaxComponentModel
            override val inputs: Collection<Input> = createMethod.parameters.map { param ->
                when {
                    param.isAnnotatedWith<Component>() -> ComponentDependencyFactoryInput(
                        JavaxComponentModel(param.asTypeElement(), types, elements),
                        param.simpleName.toString()
                    )
                    param.isAnnotatedWith<Module>() -> ModuleInstanceFactoryInput(
                        JavaxModuleModel(param.asTypeElement(), types, elements),
                        param.simpleName.toString()
                    )
                    param.isAnnotatedWith<BindsInstance>() -> InstanceBinding(
                        JavaxNodeModel(param.asType()),
                        param.simpleName.toString()
                    )
                    else -> throw IllegalStateException("invalid factory method parameter")
                }
            }
        }
    }
}
