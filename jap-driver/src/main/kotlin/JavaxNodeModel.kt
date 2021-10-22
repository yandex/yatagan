package com.yandex.daggerlite.jap

import com.yandex.daggerlite.core.Binding
import com.yandex.daggerlite.core.ClassNameModel
import com.yandex.daggerlite.core.ConstructorNameModel
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.ProvisionBinding
import javax.inject.Inject
import javax.lang.model.type.TypeMirror

class JavaxNodeModel(
    private val type: TypeMirror,
) : NodeModel() {

    override val qualifier = type.asElement().qualify<javax.inject.Qualifier>()
    override val name: ClassNameModel = classNameModel(type)

    override fun implicitBinding(): Binding? {
        if (qualifier != null)
            return null

        val typeElement = type.asTypeElement()
        return typeElement.constructors()
            .find { it.isAnnotatedWith<Inject>() }
            ?.let {
                ProvisionBinding(
                    target = this@JavaxNodeModel,
                    scope = typeElement.qualify<javax.inject.Scope>(),
                    provider = ConstructorNameModel(classNameModel(type)),
                    params = it.parameters.map { Dependency(JavaxNodeModel(type)) },
                    requiredModuleInstance = null
                )
            }
    }

    override fun toString() = buildString { qualifier?.let { append("$it ") }; append(name) }
}