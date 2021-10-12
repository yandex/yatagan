package com.yandex.dagger3.rt

import com.yandex.dagger3.core.Binding
import com.yandex.dagger3.core.ComponentModel
import com.yandex.dagger3.core.ModuleModel
import dagger.Module

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

    override val subcomponents: Collection<ComponentModel>
        get() = TODO("Not yet implemented")
}