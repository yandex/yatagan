package com.yandex.dagger3.generator

import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.TypeSpec
import com.yandex.dagger3.core.AliasBinding
import com.yandex.dagger3.core.Binding
import com.yandex.dagger3.core.BindingGraph
import com.yandex.dagger3.core.NodeModel
import com.yandex.dagger3.core.ProvisionBinding
import javax.lang.model.element.Modifier

interface GenerationLogger {
    fun error(message: String)
    fun warning(message: String)
}

enum class Language(val extension: String) {
    Java("java"),
    Kotlin("kt"),
}

class ComponentGenerator(
    private val logger: GenerationLogger,
    private val graph: BindingGraph,
) {
    private val _targetClassName = graph.root.name.asClassName { "Dagger$it" }

    val targetPackageName: String
        get() = _targetClassName.packageName()
    val targetClassName: String
        get() = _targetClassName.simpleName()

    val targetLanguage: Language get() = Language.Java

    private val nodes = hashSetOf<NodeModel>()

    fun generateTo(out: Appendable) {
        JavaFile.builder(targetPackageName, generate())
            .build()
            .writeTo(out)
    }

    private fun resolveAlias(maybeAlias: Binding): Binding? {
        var binding: Binding? = maybeAlias
        while (binding is AliasBinding) {
            binding = graph.resolve(binding.source)
        }
        return binding
    }

    private fun generate(): TypeSpec {
        return buildClass(_targetClassName) {
            implements(graph.root.name.asClassName())

            constructor {
                modifiers(Modifier.PUBLIC)
            }

            val nodes: Map<NodeModel, Binding?> = graph.resolveReachable()

            graph.root.entryPoints.forEach { (getterName, dep) ->
                val (node, kind) = dep
                method(getterName) {
                    modifiers(Modifier.PUBLIC)
                    annotation<Override>()
                    returnType(node.name.asClassName())
                    +"throw new RuntimeException()"
                }
            }

            for ((node, binding) in nodes) {
                if (binding == null) {
                    logger.warning("Missing binding for $node")
                    continue
                }

                val resolved = resolveAlias(binding)
                method(node.name.qualifiedName.replace('.', '_')) {
                    modifiers(Modifier.PRIVATE)
                    when (resolved) {
                        is ProvisionBinding -> {
                            returnType(resolved.target.name.asClassName())
                            val name = resolved.provider.name
                            if (name == "<init>") {
                                +"return new %T()".formatCode(
                                    resolved.provider.ownerName.asClassName(),
                                )
                            } else {
                                +"return %T.%N()".formatCode(
                                    resolved.provider.ownerName.asClassName(),
                                    resolved.provider.name,
                                )
                            }
                        }
                        else -> +"throw new RuntimeException()"
                    }.let {/*exhaustive*/ }
                }
            }
        }
    }
}

