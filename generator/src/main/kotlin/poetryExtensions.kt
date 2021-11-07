package com.yandex.daggerlite.generator

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.yandex.daggerlite.core.ClassBackedModel
import com.yandex.daggerlite.core.DependencyKind
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.CtTypeNameModel
import com.yandex.daggerlite.generator.poetry.ExpressionBuilder
import com.yandex.daggerlite.generator.poetry.Names
import com.yandex.daggerlite.generator.poetry.invoke

internal inline fun CtTypeNameModel.asClassName(
    transformName: (String) -> String,
): ClassName {
    require(typeArguments.isEmpty()) {
        "Can't transform type name with type arguments"
    }
    return when (simpleNames.size) {
        1 -> ClassName.get(packageName, transformName(simpleNames.first()))
        else -> ClassName.get(
            packageName, simpleNames.first(), *simpleNames
                .mapIndexed { index, name ->
                    if (index == simpleNames.lastIndex) {
                        transformName(name)
                    } else name
                }.drop(1).toTypedArray()
        )
    }
}

internal fun ClassBackedModel.typeName(): TypeName {
    return name.asTypeName()
}

internal fun TypeLangModel.typeName(): TypeName {
    return name.asTypeName()
}

private fun CtTypeNameModel.asTypeName(): TypeName {
    val className = when (simpleNames.size) {
        0 -> throw IllegalArgumentException()
        1 -> ClassName.get(packageName, simpleNames.first())
        2 -> ClassName.get(packageName, simpleNames[0], simpleNames[1])
        3 -> ClassName.get(packageName, simpleNames[0], simpleNames[1], simpleNames[2])
        else -> ClassName.get(packageName, simpleNames.first(), *simpleNames.drop(1).toTypedArray())
    }
    return if (typeArguments.isNotEmpty()) {
        ParameterizedTypeName.get(className, *typeArguments.map(CtTypeNameModel::asTypeName).toTypedArray())
    } else className
}

internal fun NodeDependency.asTypeName(): TypeName {
    val typeName = node.typeName()
    return when (kind) {
        DependencyKind.Direct -> typeName
        DependencyKind.Lazy -> Names.Lazy(typeName)
        DependencyKind.Provider -> Names.Provider(typeName)
        DependencyKind.Optional -> Names.Optional(typeName)
        DependencyKind.OptionalLazy -> Names.Optional(Names.Lazy(typeName))
        DependencyKind.OptionalProvider -> Names.Optional(Names.Provider(typeName))
    }
}

internal inline fun <A> ExpressionBuilder.generateCall(
    function: FunctionLangModel,
    arguments: Iterable<A>,
    instance: String?,
    crossinline argumentBuilder: ExpressionBuilder.(A) -> Unit,
) {
    when {
        function.isConstructor -> +"new %T(".formatCode(function.ownerName.asTypeName())
        else -> {
            if (instance != null) {
                +"$instance.%N(".formatCode(function.name)
            } else {
                val ownerObject = when {
                    function.owner.isKotlinObject -> ".INSTANCE"
                    function.isFromCompanionObject && !function.isStatic -> ".Companion"
                    else -> ""
                }
                +"${function.ownerName.asTypeName()}$ownerObject.${function.name}("
            }
        }
    }
    join(arguments) { arg ->
        argumentBuilder(arg)
    }
    +")"
}
