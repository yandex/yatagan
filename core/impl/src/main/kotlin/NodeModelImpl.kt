package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.core.Binding
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.ProvisionBinding
import com.yandex.daggerlite.core.lang.AnnotatedLangModel
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith
import javax.inject.Inject

internal class NodeModelImpl private constructor(
    override val type: TypeLangModel,
    override val qualifier: AnnotationLangModel?,
) : NodeModel() {

    constructor(
        type: TypeLangModel,
        forQualifier: AnnotatedLangModel,
    ) : this(type, forQualifier.annotations.find { it.isQualifier })

    override fun implicitBinding(): Binding? {
        if (qualifier != null)
            return null

        return type.declaration.constructors.find { it.isAnnotatedWith<Inject>() }?.let { injectConstructor ->
            ProvisionBinding(
                target = this,
                requiredModuleInstance = null,
                scope = type.declaration.annotations.find(AnnotationLangModel::isScope),
                provider = injectConstructor,
                params = injectConstructor.parameters.map { param ->
                    nodeModelDependency(type = param.type, forQualifier = param)
                }.toList(),
            )
        }
    }
}