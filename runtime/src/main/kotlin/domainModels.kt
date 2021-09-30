package com.yandex.dagger3.rt

import com.yandex.dagger3.core.*
import dagger.Component
import dagger.Module

data class RtComponentModel(
    val clazz: Class<*>,
) : ComponentModel {
    override val name: NameModel
        get() = TODO("Not yet implemented")
    override val entryPoints: Set<Pair<String, NodeModel>>
        get() = TODO("Not yet implemented")


    private val impl: Component = requireNotNull(clazz.getAnnotation(Component::class.java)) {
        "class $clazz can't be represented by ComponentModel"
    }

    override val modules: Set<RtModuleModel> = impl.modules
        .mapTo(hashSetOf()) { RtModuleModel(it.java) }

    override val dependencies: Set<RtComponentModel> = impl.dependencies.asSequence()
        .mapTo(hashSetOf()) { RtComponentModel(it.java) }
}

data class RtModuleModel(
    val clazz: Class<*>,
) : ModuleModel {
    private val impl = requireNotNull(clazz.getAnnotation(Module::class.java)) {
        "class $clazz can't be represented by ModuleModel"
    }

    override val bindings: Collection<Binding> = buildList {
        clazz.methods.mapNotNull {
            TODO("Implement")
        }
    }
}