package com.yandex.dagger3.rt

import com.yandex.dagger3.core.ComponentModel
import com.yandex.dagger3.core.EntryPointModel
import com.yandex.dagger3.core.NameModel
import dagger.Component

data class RtComponentModel(
    val clazz: Class<*>,
) : ComponentModel {
    override val name: NameModel
        get() = TODO("Not yet implemented")
    override val entryPoints: Set<EntryPointModel>
        get() = TODO("Not yet implemented")


    private val impl: Component = requireNotNull(clazz.getAnnotation(Component::class.java)) {
        "class $clazz can't be represented by ComponentModel"
    }

    override val modules: Set<RtModuleModel> = impl.modules
        .mapTo(hashSetOf()) { RtModuleModel(it.java) }

    override val dependencies: Set<RtComponentModel> = impl.dependencies.asSequence()
        .mapTo(hashSetOf()) { RtComponentModel(it.java) }

    override val isRoot: Boolean
        get() = TODO("Not yet implemented")
}