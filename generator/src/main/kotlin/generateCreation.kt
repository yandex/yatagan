package com.yandex.daggerlite.generator

import com.squareup.javapoet.CodeBlock
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.component1
import com.yandex.daggerlite.core.component2
import com.yandex.daggerlite.core.isAlways
import com.yandex.daggerlite.core.isNever
import com.yandex.daggerlite.core.lang.CallableLangModel
import com.yandex.daggerlite.core.lang.ConstructorLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.KotlinObjectKind
import com.yandex.daggerlite.generator.poetry.ExpressionBuilder
import com.yandex.daggerlite.generator.poetry.buildExpression
import com.yandex.daggerlite.graph.AlternativesBinding
import com.yandex.daggerlite.graph.AssistedInjectFactoryBinding
import com.yandex.daggerlite.graph.Binding
import com.yandex.daggerlite.graph.BindingGraph
import com.yandex.daggerlite.graph.ComponentDependencyBinding
import com.yandex.daggerlite.graph.ComponentDependencyEntryPointBinding
import com.yandex.daggerlite.graph.ComponentInstanceBinding
import com.yandex.daggerlite.graph.EmptyBinding
import com.yandex.daggerlite.graph.InstanceBinding
import com.yandex.daggerlite.graph.MapBinding
import com.yandex.daggerlite.graph.MultiBinding
import com.yandex.daggerlite.graph.ProvisionBinding
import com.yandex.daggerlite.graph.SubComponentFactoryBinding

private class CreationGeneratorVisitor(
    private val builder: ExpressionBuilder,
    private val inside: BindingGraph,
    private val isInsideInnerClass: Boolean,
) : Binding.Visitor<Unit> {
    override fun visitProvision(binding: ProvisionBinding) {
        with(builder) {
            val instance = if (binding.requiresModuleInstance) {
                "%L.%N".formatCode(
                    componentForBinding(binding),
                    binding.owner[ComponentFactoryGenerator].fieldNameFor(binding.originModule!!),
                )
            } else null
            binding.provision.accept(object : CallableLangModel.Visitor<Unit> {
                fun genArgs() {
                    join(seq = binding.inputs.asIterable()) { (node, kind) ->
                        inside.resolveBinding(node).generateAccess(
                            builder = this,
                            inside = inside,
                            kind = kind,
                            isInsideInnerClass = isInsideInnerClass,
                        )
                    }
                }

                override fun visitFunction(function: FunctionLangModel) {
                    +"%T.checkProvisionNotNull(".formatCode(Names.Checks)
                    if (instance != null) {
                        +"%L.%N(".formatCode(instance, function.name)
                    } else {
                        val ownerObject = when (function.owner.kotlinObjectKind) {
                            KotlinObjectKind.Object -> ".INSTANCE"
                            else -> ""
                        }
                        +"%T%L.%L(".formatCode(function.ownerName.asTypeName(), ownerObject, function.name)
                    }
                    genArgs()
                    +"))"
                }

                override fun visitConstructor(constructor: ConstructorLangModel) {
                    +"new %T(".formatCode(constructor.constructee.asType().typeName().asRawType())
                    genArgs()
                    +")"
                }
            })
        }
    }

    override fun visitAssistedInjectFactory(binding: AssistedInjectFactoryBinding) {
        binding.owner[AssistedInjectFactoryGenerator].generateCreation(
            builder = builder,
            binding = binding,
            inside = inside,
            isInsideInnerClass = isInsideInnerClass,
        )
    }

    override fun visitInstance(binding: InstanceBinding) {
        with(builder) {
            +"%L.%N".formatCode(
                componentForBinding(binding),
                binding.owner[ComponentFactoryGenerator].fieldNameFor(binding.target),
            )
        }
    }

    override fun visitAlternatives(binding: AlternativesBinding) {
        with(builder) {
            var exhaustive = false
            for (alternative: NodeModel in binding.alternatives) {
                val altBinding = inside.resolveBinding(alternative)
                if (!altBinding.conditionScope.isAlways) {
                    if (altBinding.conditionScope.isNever) {
                        // Never scoped is, by definition, unreached, so just skip it.
                        continue
                    }
                    val expression = buildExpression {
                        val gen = inside[ConditionGenerator]
                        gen.expression(
                            builder = this,
                            conditionScope = altBinding.conditionScope,
                            inside = inside,
                            isInsideInnerClass = isInsideInnerClass,
                        )
                    }
                    +"%L ? ".formatCode(expression)
                    altBinding.generateAccess(
                        builder = builder,
                        inside = inside,
                        isInsideInnerClass = isInsideInnerClass,
                    )
                    +" : "
                } else {
                    altBinding.generateAccess(
                        builder = builder,
                        inside = inside,
                        isInsideInnerClass = isInsideInnerClass,
                    )
                    exhaustive = true
                    break  // no further generation, the rest are (if any) unreachable.
                }
            }
            if (!exhaustive) {
                +"null /*empty*/"
            }
        }
    }

    override fun visitSubComponentFactory(binding: SubComponentFactoryBinding) {
        with(builder) {
            +"new %T(".formatCode(binding.targetGraph[ComponentFactoryGenerator].implName)
            join(binding.targetGraph.usedParents) { parentGraph ->
                +buildExpression {
                    +"%L".formatCode(componentInstance(
                        inside = inside,
                        graph = parentGraph,
                        isInsideInnerClass = isInsideInnerClass,
                    ))
                }
            }
            +")"
        }
    }

    override fun visitComponentDependency(binding: ComponentDependencyBinding) {
        with(builder) {
            +"%L.%N".formatCode(
                componentForBinding(binding),
                binding.owner[ComponentFactoryGenerator].fieldNameFor(binding.dependency),
            )
        }
    }

    override fun visitComponentInstance(binding: ComponentInstanceBinding) {
        with(builder) {
            +componentForBinding(binding)
        }
    }

    override fun visitComponentDependencyEntryPoint(binding: ComponentDependencyEntryPointBinding) {
        with(builder) {
            +"%L.%N.%N()".formatCode(
                componentForBinding(binding),
                binding.owner[ComponentFactoryGenerator].fieldNameFor(binding.dependency),
                binding.getter.name,
            )
        }
    }

    override fun visitMulti(binding: MultiBinding) {
        binding.owner[MultiBindingGenerator].generateCreation(
            builder = builder,
            binding = binding,
            inside = inside,
            isInsideInnerClass = isInsideInnerClass,
        )
    }

    override fun visitMap(binding: MapBinding) {
        binding.owner[MapBindingGenerator].generateCreation(
            builder = builder,
            binding = binding,
            inside = inside,
            isInsideInnerClass = isInsideInnerClass,
        )
    }

    override fun visitEmpty(binding: EmptyBinding) {
        throw AssertionError("Not reached: unreported empty/missing binding: `$binding`")
    }

    private fun componentForBinding(binding: Binding): CodeBlock {
        return componentForBinding(
            inside = inside,
            binding = binding,
            isInsideInnerClass = isInsideInnerClass,
        )
    }
}

internal fun Binding.generateCreation(
    builder: ExpressionBuilder,
    inside: BindingGraph,
    isInsideInnerClass: Boolean,
) {
    accept(CreationGeneratorVisitor(
        builder = builder,
        inside = inside,
        isInsideInnerClass = isInsideInnerClass,
    ))
}