package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.core.BindingGraph
import com.yandex.daggerlite.core.ComponentDependencyBinding
import com.yandex.daggerlite.core.ComponentDependencyInput
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.ConditionScope
import com.yandex.daggerlite.core.InstanceBinding
import com.yandex.daggerlite.core.InstanceInput
import com.yandex.daggerlite.core.ModuleInstanceInput
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeModel

internal class ComponentDependencyInputImpl(
    override val name: String,
    override val component: ComponentModel,
) : ComponentDependencyInput {
    override val target get() = component.asNode()

    override fun createBinding(forGraph: BindingGraph): ComponentDependencyBinding {
        return object : ComponentDependencyBinding {
            override val input get() = this@ComponentDependencyInputImpl
            override val target get() = input.component.asNode()
            override val conditionScope get() = ConditionScope.Unscoped
            override val scope: Nothing? get() = null
            override val owner get() = forGraph
            override fun dependencies(): List<Nothing> = emptyList()
        }
    }
}

internal class InstanceInputImpl(
    override val name: String,
    override val node: NodeModel,
) : InstanceInput {
    override val target get() = node

    override fun createBinding(forGraph: BindingGraph): InstanceBinding {
        return object : InstanceBinding {
            override val input get() = this@InstanceInputImpl
            override val conditionScope get() = ConditionScope.Unscoped
            override val scope: Nothing? get() = null
            override val target get() = node
            override val owner get() = forGraph
            override fun dependencies(): List<Nothing> = emptyList()
        }
    }
}

internal class ModuleInstanceInputImpl(
    override val name: String,
    override val module: ModuleModel,
) : ModuleInstanceInput {
    override val target get() = module
}
