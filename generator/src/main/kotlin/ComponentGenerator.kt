package com.yandex.dagger3.generator

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.TypeSpec
import com.yandex.dagger3.core.AliasBinding
import com.yandex.dagger3.core.Binding
import com.yandex.dagger3.core.BindingGraph
import com.yandex.dagger3.core.InstanceBinding
import com.yandex.dagger3.core.NodeModel
import com.yandex.dagger3.core.NonAliasBinding
import com.yandex.dagger3.core.ProvisionBinding
import com.yandex.dagger3.core.isScoped
import com.yandex.dagger3.generator.poetry.Names
import com.yandex.dagger3.generator.poetry.buildClass
import com.yandex.dagger3.generator.poetry.buildExpression
import javax.lang.model.element.Modifier

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

    fun generateTo(out: Appendable) {
        JavaFile.builder(targetPackageName, generate())
            .build()
            .writeTo(out)
    }

    private fun resolveAlias(maybeAlias: Binding?): NonAliasBinding? {
        var binding: Binding? = maybeAlias
        while (when (binding) {
                is AliasBinding -> true
                is ProvisionBinding -> return binding
                is InstanceBinding -> return binding
                null -> return null
            }
        ) {
            binding = graph.resolve(binding.source)
        }
        throw IllegalStateException("not reached")
    }

    private fun generate(): TypeSpec {
        return buildClass(_targetClassName) {
            implements(graph.root.name.asTypeName())
            modifiers(Modifier.FINAL)
            annotation<SuppressWarnings> { stringValues("unchecked", "rawtypes") }

            val factory = graph.root.factory
            if (factory != null) {
                factory.inputs.forEach { input ->
                    field(input.target.name.asTypeName(), input.paramName) {
                        modifiers(Modifier.PRIVATE, Modifier.FINAL)
                    }
                }
                constructor {
                    modifiers(Modifier.PRIVATE)
                    factory.inputs.forEach { input ->
                        parameter(input.target.name.asTypeName(), input.paramName)
                        +"this.%N = %N".formatCode(input.paramName, input.paramName)
                    }
                }
                val factoryImplName = _targetClassName.nestedClass("ComponentFactoryImpl")
                nestedType {
                    buildClass(factoryImplName) {
                        modifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                        implements(factory.name.asTypeName())

                        method("create") {
                            modifiers(Modifier.PUBLIC)
                            annotation<Override>()
                            returnType(graph.root.name.asTypeName())
                            factory.inputs.forEach { input ->
                                parameter(input.target.name.asTypeName(), input.paramName)
                            }
                            +buildExpression {
                                +"return new %T(".formatCode(_targetClassName)
                                join(factory.inputs.asSequence()) { +it.paramName }
                                +")"
                            }
                        }
                    }
                }
                method("factory") {
                    modifiers(Modifier.PUBLIC, Modifier.STATIC)
                    returnType(factory.name.asTypeName())
                    +"return new %T()".formatCode(factoryImplName)
                }
            } else {
                constructor {
                    modifiers(Modifier.PUBLIC)
                }
            }

            val reachable = graph.resolveReachable()
            val provisionBindings = reachable.asSequence()
                .onEach { (node, binding) ->
                    if (binding == null) {
                        logger.warning("Missing binding for $node")
                    }
                }.mapNotNull { (_, binding) -> binding }
                .filterIsInstance<ProvisionBinding>()
                .toList()

            // TODO(jeffset): Extract this into a provision manager of some king
            val internalProvisions = hashMapOf<NodeModel.Dependency, String>()
            fun internalProvision(dep: NodeModel.Dependency): String {
                // TODO(jeffset): reuse entry points to reduce method count
                return internalProvisions.getOrPut(dep) {
                    "_get${internalProvisions.size}"
                }
            }

            method("_new") {
                modifiers(Modifier.PRIVATE)
                returnType(ClassName.OBJECT)
                parameter(ClassName.INT, "i")
                controlFlow("switch(i)") {
                    provisionBindings.forEachIndexed { index, binding ->
                        +buildExpression {
                            +"case $index: return "
                            call(binding.provider, binding.params.asSequence()
                                .map { dep -> internalProvision(dep) + "()" })
                        }
                    }
                    +"default: throw new %T(%S)".formatCode(Names.AssertionError, "not reached")
                }
            }

            val cachingProviderName = _targetClassName.nestedClass("CachingProvider")
            var useCachingProvider = false

            provisionBindings
                .zip(0..provisionBindings.lastIndex)
                .filter { (binding, _) -> binding.isScoped } // fixme: not only provisions may be scoped - aliases too
                .forEach { (_, index) ->
                    useCachingProvider = true
                    field(cachingProviderName, "mProvider$index") {
                        modifiers(Modifier.PRIVATE)
                    }

                    method("_provider$index") {
                        returnType(cachingProviderName)
                        +"%T local = mProvider$index".formatCode(cachingProviderName)
                        controlFlow("if (local == null)") {
                            +"local = new %T(this, $index)"
                                .formatCode(cachingProviderName)
                            +"mProvider$index = local"
                        }
                        +"return local"
                    }
                }

            graph.root.entryPoints.forEach { (getter, dep) ->
                method(getter.functionName()) {
                    modifiers(Modifier.PUBLIC)
                    annotation<Override>()
                    returnType(dep.asTypeName())
                    +"return %N()".formatCode(internalProvision(dep))
                }
            }

            val factoryName = _targetClassName.nestedClass("Factory")
            var useFactory = false

            for ((dep, name) in internalProvisions) {
                val binding: NonAliasBinding? = resolveAlias(graph.resolve(dep.node))
                val index = provisionBindings.indexOf(binding)
                method(name) {
                    modifiers(Modifier.PRIVATE)
                    returnType(dep.asTypeName())
                    if (binding == null) {
                        +"throw new %T(%S)".formatCode(Names.AssertionError, "missing binding")
                        return@method
                    }

                    when (dep.kind) {
                        DependencyKind.Direct -> {
                            if (binding.isScoped) {
                                +"return (%T) _provider$index().get()".formatCode(dep.node.name.asTypeName())
                            } else {
                                +buildExpression {
                                    +"return "
                                    when (binding) {
                                        is InstanceBinding -> +"this.%N".formatCode(binding.paramName)
                                        is ProvisionBinding ->
                                            call(binding.provider, binding.params.asSequence()
                                                .map { dep -> internalProvision(dep) + "()" })
                                    }.let { /*exhaustive*/ }
                                }
                            }
                        }
                        DependencyKind.Lazy -> {
                            if (binding.isScoped) {
                                +"return _provider$index()"
                            } else {
                                +buildExpression {
                                    useCachingProvider = true
                                    +"return new %T(this, $index)"
                                        .formatCode(cachingProviderName)
                                }
                            }
                        }
                        DependencyKind.Provider -> {
                            if (binding.isScoped) {
                                +"return _provider$index()"
                            } else {
                                +buildExpression {
                                    useFactory = true
                                    +"return new %T(this, $index)"
                                        .formatCode(factoryName)
                                }
                            }
                        }
                    }
                }
            }

            if (useFactory) {
                nestedType {
                    buildClass(factoryName) {
                        implements(Names.Provider)
                        modifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                        field(_targetClassName, "mFactory") {
                            modifiers(Modifier.PRIVATE, Modifier.FINAL)
                        }
                        field(ClassName.INT, "mIndex") {
                            modifiers(Modifier.PRIVATE, Modifier.FINAL)
                        }
                        constructor {
                            parameter(_targetClassName, "factory")
                            parameter(ClassName.INT, "index")
                            +"mFactory = factory"
                            +"mIndex = index"
                        }
                        method("get") {
                            modifiers(Modifier.PUBLIC)
                            annotation<Override>()
                            returnType(ClassName.OBJECT)
                            +"return mFactory._new(mIndex)"
                        }
                    }
                }

                if (useCachingProvider) {
                    nestedType {
                        buildClass(cachingProviderName) {
                            implements(Names.Lazy)
                            modifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                            field(_targetClassName, "mFactory") {
                                modifiers(Modifier.PRIVATE, Modifier.FINAL)
                            }
                            field(ClassName.INT, "mIndex") {
                                modifiers(Modifier.PRIVATE, Modifier.FINAL)
                            }
                            field(ClassName.OBJECT, "mValue") {
                                modifiers(Modifier.PRIVATE)
                            }
                            constructor {
                                parameter(_targetClassName, "factory")
                                parameter(ClassName.INT, "index")
                                +"mFactory = factory"
                                +"mIndex = index"
                            }

                            method("get") {
                                modifiers(Modifier.PUBLIC)
                                annotation<Override>()
                                returnType(ClassName.OBJECT)
                                +"%T local = mValue".formatCode(ClassName.OBJECT)
                                controlFlow("if (local == null)") {
                                    +"local = mFactory._new(mIndex)"
                                    +"mValue = local"
                                }
                                +"return local"
                            }
                        }
                    }
                }
            }
        }
    }
}
