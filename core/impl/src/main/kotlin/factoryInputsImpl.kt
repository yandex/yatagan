package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.core.ComponentDependencyInput
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.InstanceInput
import com.yandex.daggerlite.core.ModuleInstanceInput
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeModel

internal class ComponentDependencyInputImpl(
    override val name: String,
    override val component: ComponentModel,
) : ComponentDependencyInput {
    override val target get() = component.asNode()
}

internal class InstanceInputImpl(
    override val name: String,
    override val node: NodeModel,
) : InstanceInput {
    override val target get() = node
}

internal class ModuleInstanceInputImpl(
    override val name: String,
    override val module: ModuleModel,
) : ModuleInstanceInput {
    override val target get() = module
}
