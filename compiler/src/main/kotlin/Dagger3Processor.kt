package com.yandex.dagger3.compiler

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.TypeSpec
import com.yandex.dagger3.core.BindingGraph
import com.yandex.dagger3.core.NameModel
import com.yandex.dagger3.core.NodeModel
import java.util.*
import javax.lang.model.element.Modifier

internal class Dagger3Processor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.getSymbolsWithAnnotation(AnnotationNames.Component)
            .filterIsInstance<KSClassDeclaration>()
            .forEach { symbol ->
                val model = KspComponentModel(symbol)
                val graph = BindingGraph(
                    root = model,
                )
                val name = graph.root.name
                environment.codeGenerator.createNewFile(
                    Dependencies(aggregating = false),
                    packageName = name.packageName,
                    fileName = "Dagger" + name.name,
                    extensionName = "java",
                ).use { file ->
                    file.bufferedWriter().use {
                        JavaFile.builder(name.packageName, generate(graph))
                            .build()
                            .writeTo(it)
                    }
                }
            }
        return emptyList()
    }

    private fun generate(graph: BindingGraph): TypeSpec {
        return buildClass(graph.root.name.asClassName { "Dagger$it" }) {
            implements(graph.root.name.asClassName())

            constructor {
                modifiers(Modifier.PUBLIC)
            }

            val stack = LinkedList<NodeModel>()
            for (entryPoint in graph.root.entryPoints) {
                method(entryPoint.first) {
                    modifiers(Modifier.PUBLIC)
                    returnType(entryPoint.second.name.asClassName())
                    +"throw new RuntimeException()"
                }
                stack.add(entryPoint.second)
            }

            while (stack.isNotEmpty()) {
                val node = stack.pop()
                val binding = graph.resolve(node)
                if (binding == null) {
                    environment.logger.error("Missing binding for $node")
                    continue
                }

                stack += binding.dependencies
            }
        }
    }
}

private inline fun NameModel.asClassName(
    transformName: (String) -> String = { it }
) = ClassName.get(packageName, transformName(name))