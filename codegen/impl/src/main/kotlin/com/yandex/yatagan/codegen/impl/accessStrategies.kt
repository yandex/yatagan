/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yandex.yatagan.codegen.impl

import com.squareup.javapoet.ClassName
import com.yandex.yatagan.Assisted
import com.yandex.yatagan.AssistedInject
import com.yandex.yatagan.codegen.poetry.ExpressionBuilder
import com.yandex.yatagan.codegen.poetry.TypeSpecBuilder
import com.yandex.yatagan.codegen.poetry.buildExpression
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.DependencyKind
import com.yandex.yatagan.core.model.isOptional
import com.yandex.yatagan.instrumentation.impl.InstrumentedAround
import com.yandex.yatagan.instrumentation.impl.instrumentedCreation
import javax.lang.model.element.Modifier.PRIVATE
import javax.lang.model.element.Modifier.VOLATILE

internal abstract class CachingStrategyBase(
    protected val binding: Binding,
    fieldsNs: Namespace,
    methodsNs: Namespace,
) : AccessStrategy {
    protected val instanceFieldName: String
    protected val instanceAccessorName: String

    init {
        val target = binding.target
        instanceFieldName = fieldsNs.name(target.name, suffix = "instance", qualifier = target.qualifier)
        instanceAccessorName = methodsNs.name(target.name, prefix = "cache", qualifier = target.qualifier)
    }

    override fun generateAccess(
        builder: ExpressionBuilder,
        kind: DependencyKind,
        inside: BindingGraph,
        isInsideInnerClass: Boolean
    ) {
        builder.apply {
            when (kind) {
                DependencyKind.Direct -> {
                    val component = componentForBinding(
                        inside = inside,
                        binding = binding,
                        isInsideInnerClass = isInsideInnerClass,
                    )
                    +"%L.%N()".formatCode(component, instanceAccessorName)
                }
                else -> throw AssertionError()
            }.let { /*exhaustive*/ }
        }
    }
}

internal class CachingStrategySingleThread @AssistedInject constructor(
    @Assisted binding: Binding,
    @FieldsNamespace fieldsNs: Namespace,
    @MethodsNamespace methodsNs: Namespace,
    private val options: ComponentGenerator.Options,
) : CachingStrategyBase(binding, fieldsNs, methodsNs) {
    override fun generateInComponent(builder: TypeSpecBuilder) = with(builder) {
        val targetType = binding.target.typeName()
        field(ClassName.OBJECT, instanceFieldName) {
            modifiers(PRIVATE)  // PRIVATE: only accessed via its accessor.
        }
        method(instanceAccessorName) {
            modifiers(/*package-private*/)
            returnType(targetType)
            +"%T local = this.%N".formatCode(ClassName.OBJECT, instanceFieldName)
            controlFlow("if (local == null)") {
                if (options.enableThreadChecks) {
                    +"%T.assertThreadAccess()".formatCode(Names.ThreadAssertions)
                }
                val instrumented = binding.instrumentedCreation
                doInstrument(
                    graph = binding.owner,
                    statements = instrumented.before,
                )
                +buildExpression {
                    +"local = "
                    binding.generateCreation(builder = this, inside = binding.owner, isInsideInnerClass = false)
                }
                +"this.%N = local".formatCode(instanceFieldName)
                doInstrument(
                    graph = binding.owner,
                    statements = instrumented.after,
                    instrumentedInstance = "local" to targetType,
                )
            }
            +"return (%T) local".formatCode(targetType)
        }
    }
}

internal class CachingStrategyMultiThread @AssistedInject constructor(
    @Assisted binding: Binding,
    @FieldsNamespace fieldsNs: Namespace,
    @MethodsNamespace methodsNs: Namespace,
    lock: LockGenerator,
) : CachingStrategyBase(binding, fieldsNs, methodsNs) {
    private val lockName = lock.name

    override fun generateInComponent(builder: TypeSpecBuilder) = with(builder) {
        val targetType = binding.target.typeName()
        field(ClassName.OBJECT, instanceFieldName) {
            modifiers(PRIVATE, VOLATILE)  // PRIVATE: only accessed via its accessor.
            initializer {
                +"new %T()".formatCode(lockName)
            }
        }
        method(instanceAccessorName) {
            modifiers(/*package-private*/)
            returnType(targetType)
            +"%T local = this.%N".formatCode(ClassName.OBJECT, instanceFieldName)
            controlFlow("if (local instanceof %T)".formatCode(lockName)) {
                controlFlow("synchronized (local)") {
                    +"local = this.%N".formatCode(instanceFieldName)
                    controlFlow("if (local instanceof %T)".formatCode(lockName)) {
                        val instrumented = binding.instrumentedCreation
                        doInstrument(
                            graph = binding.owner,
                            statements = instrumented.before,
                        )
                        +buildExpression {
                            +"local = "
                            binding.generateCreation(builder = this, inside = binding.owner, isInsideInnerClass = false)
                        }
                        +"this.%N = local".formatCode(instanceFieldName)
                        doInstrument(
                            graph = binding.owner,
                            statements = instrumented.after,
                            instrumentedInstance = "local" to targetType,
                        )
                    }
                }
            }
            +"return (%T) local".formatCode(targetType)
        }
    }
}

internal class SlotProviderStrategy @AssistedInject constructor(
    @Assisted private val binding: Binding,
    cachingProvider: UnscopedProviderGenerator,
    slotSwitchingGenerator: SlotSwitchingGenerator,
) : AccessStrategy {
    private val slot = slotSwitchingGenerator.requestSlot(binding)
    private val providerName = cachingProvider.name

    override fun generateAccess(
        builder: ExpressionBuilder,
        kind: DependencyKind,
        inside: BindingGraph,
        isInsideInnerClass: Boolean
    ) {
        when (kind) {
            DependencyKind.Lazy, DependencyKind.Provider -> builder.apply {
                val component = componentForBinding(
                    inside = inside,
                    binding = binding,
                    isInsideInnerClass = isInsideInnerClass,
                )
                // Provider class is chosen based on component (delegate) type - it will be the right one.
                +"new %T(%L, $slot)".formatCode(providerName, component)
            }
            else -> throw AssertionError()
        }.let { /*exhaustive*/ }
    }
}

internal class InlineCreationStrategy(
    private val binding: Binding,
) : AccessStrategy {
    override fun generateAccess(
        builder: ExpressionBuilder,
        kind: DependencyKind,
        inside: BindingGraph,
        isInsideInnerClass: Boolean
    ) {
        assert(kind == DependencyKind.Direct)
        binding.generateCreation(builder = builder, inside = inside, isInsideInnerClass = isInsideInnerClass)
    }
}

internal class WrappingAccessorStrategy @AssistedInject constructor(
    @Assisted private val binding: Binding,
    @Assisted private val underlying: AccessStrategy,
    @Assisted private val instrumented: InstrumentedAround,
    @MethodsNamespace methodsNs: Namespace,
) : AccessStrategy {
    private val accessorName: String

    init {
        val target = binding.target
        accessorName = methodsNs.name(target.name, prefix = "access", qualifier = target.qualifier)
    }

    override fun generateInComponent(builder: TypeSpecBuilder) = with(builder) {
        method(accessorName) {
            modifiers(/*package-private*/)
            val resultType = binding.target.typeName()
            returnType(resultType)
            doInstrument(graph = binding.owner, statements = instrumented.before)
            if (instrumented.after.isEmpty()) {
                +buildExpression {
                    +"return "
                    generateThisBindingAccess()
                }
            } else {
                val name = "instance$"
                +buildExpression {
                    "%T %N = ".format(
                        resultType,
                        name,
                    )
                    generateThisBindingAccess()
                }
                doInstrument(
                    graph = binding.owner,
                    statements = instrumented.after,
                    instrumentedInstance = name to resultType,
                )
                +"return %N".formatCode(name)
            }
        }
    }

    override fun generateAccess(
        builder: ExpressionBuilder,
        kind: DependencyKind,
        inside: BindingGraph,
        isInsideInnerClass: Boolean
    ) {
        assert(kind == DependencyKind.Direct)
        val component = componentForBinding(inside = inside, binding = binding, isInsideInnerClass = isInsideInnerClass)
        builder.apply {
            +"%L.%N()".formatCode(component, accessorName)
        }
    }

    private fun ExpressionBuilder.generateThisBindingAccess() {
        underlying.generateAccess(
            builder = this,
            kind = DependencyKind.Direct,
            inside = binding.owner,
            isInsideInnerClass = false,
        )
    }
}

internal class CompositeStrategy(
    private val directStrategy: AccessStrategy,
    private val lazyStrategy: AccessStrategy? = directStrategy,
    private val providerStrategy: AccessStrategy? = lazyStrategy,
    private val conditionalStrategy: AccessStrategy? = null,
    private val conditionalLazyStrategy: AccessStrategy? = null,
    private val conditionalProviderStrategy: AccessStrategy? = null,
) : AccessStrategy {
    override fun generateInComponent(builder: TypeSpecBuilder) {
        setOfNotNull(
            directStrategy,
            lazyStrategy,
            providerStrategy,
            conditionalStrategy,
            conditionalLazyStrategy,
            conditionalProviderStrategy,
        ).forEach { it.generateInComponent(builder) }
    }

    override fun generateAccess(
        builder: ExpressionBuilder,
        kind: DependencyKind,
        inside: BindingGraph,
        isInsideInnerClass: Boolean
    ) {
        fun delegateTo(strategy: AccessStrategy?) = checkNotNull(strategy).generateAccess(
            builder = builder,
            kind = kind,
            inside = inside,
            isInsideInnerClass = isInsideInnerClass,
        )
        when (kind) {
            DependencyKind.Direct -> delegateTo(directStrategy)
            DependencyKind.Lazy -> delegateTo(lazyStrategy)
            DependencyKind.Provider -> delegateTo(providerStrategy)
            DependencyKind.Optional -> delegateTo(conditionalStrategy)
            DependencyKind.OptionalLazy -> delegateTo(conditionalLazyStrategy)
            DependencyKind.OptionalProvider -> delegateTo(conditionalProviderStrategy)
        }.let { /*exhaustive*/ }
    }
}

internal class OnTheFlyScopedProviderCreationStrategy @AssistedInject constructor(
    cachingProvider: ScopedProviderGenerator,
    slotSwitchingGenerator: SlotSwitchingGenerator,
    @Assisted private val binding: Binding,
) : AccessStrategy {
    private val slot = slotSwitchingGenerator.requestSlot(binding)
    private val providerName = cachingProvider.name

    override fun generateAccess(
        builder: ExpressionBuilder,
        kind: DependencyKind,
        inside: BindingGraph,
        isInsideInnerClass: Boolean
    ) {
        require(kind == DependencyKind.Lazy || kind == DependencyKind.Provider)
        builder.apply {
            val component = componentForBinding(inside = inside, binding = binding, isInsideInnerClass = isInsideInnerClass)
            +"new %T(%L, $slot)".formatCode(providerName, component)
        }
    }
}

internal class OnTheFlyUnscopedProviderCreationStrategy @AssistedInject constructor(
    unscopedProviderGenerator: UnscopedProviderGenerator,
    slotSwitchingGenerator: SlotSwitchingGenerator,
    @Assisted private val binding: Binding,
) : AccessStrategy {
    private val slot = slotSwitchingGenerator.requestSlot(binding)
    private val providerName = unscopedProviderGenerator.name

    override fun generateAccess(
        builder: ExpressionBuilder,
        kind: DependencyKind,
        inside: BindingGraph,
        isInsideInnerClass: Boolean
    ) {
        require(kind == DependencyKind.Provider)
        builder.apply {
            val component = componentForBinding(inside = inside, binding = binding, isInsideInnerClass = isInsideInnerClass)
            +"new %T(%L, $slot)".formatCode(providerName, component)
        }
    }
}

internal class ConditionalAccessStrategy @AssistedInject constructor(
    @Assisted private val underlying: AccessStrategy,
    @Assisted private val binding: Binding,
    @MethodsNamespace methodsNs: Namespace,
    @Assisted private val dependencyKind: DependencyKind,
) : AccessStrategy {
    private val accessorName: String

    init {
        val target = binding.target
        accessorName = methodsNs.name(target.name, prefix = "optOf", qualifier = target.qualifier)
    }

    override fun generateInComponent(builder: TypeSpecBuilder) = with(builder) {
        method(accessorName) {
            modifiers(/*package-private*/)
            returnType(Names.Optional)
            when (val conditionScope = binding.conditionScope) {
                ConditionScope.Always -> +buildExpression {
                    +"return %T.of(".formatCode(Names.Optional)
                    underlying.generateAccess(this, dependencyKind, binding.owner, isInsideInnerClass = false)
                    +")"
                }
                ConditionScope.Never -> +buildExpression {
                    +"return %T.empty()".formatCode(Names.Optional)
                }
                is ConditionScope.ExpressionScope -> {
                    val expression = buildExpression {
                        binding.owner[GeneratorComponent].conditionGenerator.expression(
                            builder = this,
                            conditionScope = conditionScope,
                            inside = binding.owner,
                            isInsideInnerClass = false,
                        )
                    }
                    +buildExpression {
                        +"return %L ? %T.of(".formatCode(expression, Names.Optional)
                        underlying.generateAccess(this, dependencyKind, binding.owner, isInsideInnerClass = false)
                        +") : %T.empty()".formatCode(Names.Optional)
                    }
                }
            }
        }
    }

    override fun generateAccess(
        builder: ExpressionBuilder,
        kind: DependencyKind,
        inside: BindingGraph,
        isInsideInnerClass: Boolean
    ) {
        check(dependencyKind.asOptional() == kind)
        val component = componentForBinding(inside = inside, binding = binding, isInsideInnerClass = isInsideInnerClass)
        builder.apply {
            +"%L.%N()".formatCode(component, accessorName)
        }
    }
}

private fun DependencyKind.asOptional(): DependencyKind = when (this) {
    DependencyKind.Direct -> DependencyKind.Optional
    DependencyKind.Lazy -> DependencyKind.OptionalLazy
    DependencyKind.Provider -> DependencyKind.OptionalProvider
    else -> this
}

object EmptyAccessStrategy : AccessStrategy {
    override fun generateAccess(
        builder: ExpressionBuilder,
        kind: DependencyKind,
        inside: BindingGraph,
        isInsideInnerClass: Boolean
    ) {
        assert(kind.isOptional)
        with(builder) {
            +"%T.empty()".formatCode(Names.Optional)
        }
    }
}