package com.yandex.daggerlite.generator

import com.yandex.daggerlite.core.ClassBackedModel
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.ProvisionBinding

// MAYBE: this most likely requires generalization to support non-class types: builtin types, arrays, etc..
class ClassNameModel(
    val packageName: String,
    val simpleNames: List<String>,
    val typeArguments: List<ClassNameModel>,
) : ClassBackedModel.Id {
    init {
        require(simpleNames.isNotEmpty()) { "class with no name?" }
    }
    private val precomputedHash = run {
        var result = packageName.hashCode()
        result = 31 * result + simpleNames.hashCode()
        result = 31 * result + typeArguments.hashCode()
        result
    }

    override fun toString() = buildString {
        append(packageName).append('.')
        simpleNames.joinTo(this, separator = ".")
        if (typeArguments.isNotEmpty()) {
            append('<')
            typeArguments.joinTo(this, separator = ".")
            append('>')
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ClassNameModel
        if (precomputedHash != other.precomputedHash) return false
        if (simpleNames != other.simpleNames) return false
        if (typeArguments != other.typeArguments) return false
        if (packageName != other.packageName) return false
        return true
    }

    override fun hashCode(): Int = precomputedHash

    fun withArguments(typeArguments: List<ClassNameModel>) = ClassNameModel(packageName, simpleNames, typeArguments)
}

sealed interface CallableNameModel : ProvisionBinding.ProvisionDescriptor

sealed interface MemberCallableNameModel : CallableNameModel, ComponentModel.EntryPoint.Id {
    val ownerName: ClassNameModel
    val isOwnerKotlinObject: Boolean
}

data class FunctionNameModel(
    override val ownerName: ClassNameModel,
    override val isOwnerKotlinObject: Boolean,
    val function: String,
) : MemberCallableNameModel {
    override fun toString() = "$ownerName.$function()"
}

data class ConstructorNameModel(
    val type: ClassNameModel,
) : CallableNameModel {
    override fun toString() = "$type()"
}

data class PropertyNameModel(
    override val ownerName: ClassNameModel,
    override val isOwnerKotlinObject: Boolean,
    val property: String
) : MemberCallableNameModel {
    override fun toString() = "$ownerName.$property:"
}

internal val ClassBackedModel.name: ClassNameModel
    get() = id as ClassNameModel

internal val ComponentModel.EntryPoint.getter: MemberCallableNameModel
    get() = id as MemberCallableNameModel

internal val ProvisionBinding.provider: CallableNameModel
    get() = descriptor as CallableNameModel

class NamedEntryPoint(
    override val id: MemberCallableNameModel,
    override val dependency: NodeModel.Dependency,
) : ComponentModel.EntryPoint

internal operator fun ComponentModel.EntryPoint.component1() = getter
internal operator fun ComponentModel.EntryPoint.component2() = dependency