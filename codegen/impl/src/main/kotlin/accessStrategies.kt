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

import com.yandex.yatagan.Assisted
import com.yandex.yatagan.AssistedInject
import com.yandex.yatagan.codegen.poetry.Access
import com.yandex.yatagan.codegen.poetry.ExpressionBuilder
import com.yandex.yatagan.codegen.poetry.TypeName
import com.yandex.yatagan.codegen.poetry.TypeSpecBuilder
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.DependencyKind
import com.yandex.yatagan.core.model.isOptional

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
        when (kind) {
            DependencyKind.Direct -> {
                appendComponentForBinding(
                    builder = builder,
                    inside = inside,
                    binding = binding,
                    isInsideInnerClass = isInsideInnerClass,
                )
                builder.append(".").appendName(instanceAccessorName).append("()")
            }
            else -> throw AssertionError()
        }.let { /*exhaustive*/ }
    }
}

internal class CachingStrategySingleThread @AssistedInject constructor(
    @Assisted binding: Binding,
    @FieldsNamespace fieldsNs: Namespace,
    @MethodsNamespace methodsNs: Namespace,
    private val options: ComponentGenerator.Options,
) : CachingStrategyBase(binding, fieldsNs, methodsNs) {
    override fun generateInComponent(builder: TypeSpecBuilder) = with(builder) {
        val targetType = TypeName.Inferred(binding.target.type)
        field(
            type = TypeName.Nullable(TypeName.AnyObject),
            name = instanceFieldName,
            access = Access.Private,
            isMutable = true,
        ) {}
        method(
            name = instanceAccessorName,
            access = Access.Internal,
        ) {
            returnType(targetType)
            code {
                appendVariableDeclaration(
                    type = TypeName.Nullable(TypeName.AnyObject),
                    name = "local",
                    mutable = true,
                    initializer = { append("this.").appendName(instanceFieldName) },
                )
                appendIfControlFlow(
                    condition = { append("local == null") },
                    ifTrue = {
                        if (options.enableThreadChecks) {
                            appendStatement { appendType(TypeName.ThreadAssertions).append(".assertThreadAccess()") }
                        }
                        appendStatement {
                            append("local = ")
                            binding.generateCreation(
                                builder = this,
                                inside = binding.owner,
                                isInsideInnerClass = false,
                            )
                        }
                        appendStatement { append("this.").appendName(instanceFieldName).append(" = local") }
                    },
                )
                appendReturnStatement {
                    appendCast(asType = targetType) { append("local") }
                }
            }
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
        val targetType = TypeName.Inferred(binding.target.type)
        field(
            type = TypeName.AnyObject,
            name = instanceFieldName,
            access = Access.Private,
            isMutable = true,
        ) {
            volatile()
            initializer {
                appendObjectCreation(lockName)
            }
        }
        method(
            instanceAccessorName,
            access = Access.Internal,
        ) {
            returnType(targetType)
            code {
                appendVariableDeclaration(
                    type = TypeName.AnyObject,
                    name = "local",
                    mutable = true,
                    initializer = { append("this.").appendName(instanceFieldName) },
                )
                appendIfControlFlow(
                    condition = {
                        appendTypeCheck(
                            expression = { append("local") },
                            type = lockName,
                        )
                    },
                    ifTrue = {
                        appendSynchronizedBlock(
                            lock = { append("local") },
                        ) {
                            appendIfControlFlow(
                                condition = {
                                    appendTypeCheck(
                                        expression = { append("local") },
                                        type = lockName,
                                    )
                                },
                                ifTrue = {
                                    appendStatement {
                                        append("local = ")
                                        binding.generateCreation(
                                            builder = this,
                                            inside = binding.owner,
                                            isInsideInnerClass = false,
                                        )
                                    }
                                    appendStatement {
                                        append("this.").appendName(instanceFieldName).append(" = local")
                                    }
                                },
                            )
                        }
                    },
                )
                appendReturnStatement {
                    appendCast(asType = targetType) { append("local") }
                }
            }
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
        isInsideInnerClass: Boolean,
    ) {
        when (kind) {
            DependencyKind.Lazy, DependencyKind.Provider -> {
                builder.appendObjectCreation(
                    type = providerName,
                    argumentCount = 2,
                    argument = {
                        when(it) {
                            0 -> appendComponentForBinding(
                                builder = builder,
                                inside = inside,
                                binding = binding,
                                isInsideInnerClass = isInsideInnerClass,
                            )
                            1 -> append("$slot")
                        }
                    }
                )
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
    @MethodsNamespace methodsNs: Namespace,
) : AccessStrategy {
    private val accessorName: String

    init {
        val target = binding.target
        accessorName = methodsNs.name(target.name, prefix = "access", qualifier = target.qualifier)
    }

    override fun generateInComponent(builder: TypeSpecBuilder) = with(builder) {
        method(
            name = accessorName,
            access = Access.Internal,
        ) {
            returnType(TypeName.Inferred(binding.target.type))
            code {
                appendReturnStatement {
                    underlying.generateAccess(
                        builder = this,
                        kind = DependencyKind.Direct,
                        inside = binding.owner,
                        isInsideInnerClass = false,
                    )
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
        assert(kind == DependencyKind.Direct)
        appendComponentForBinding(
            builder = builder,
            inside = inside,
            binding = binding,
            isInsideInnerClass = isInsideInnerClass,
        )
        builder.append(".").appendName(accessorName).append("()")
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
        builder.appendObjectCreation(
            type = providerName,
            argumentCount = 2,
            argument = {
                when(it) {
                    0 -> appendComponentForBinding(builder = this, inside = inside,
                        binding = binding, isInsideInnerClass = isInsideInnerClass)
                    1 -> append(slot.toString())
                }
            },
        )
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
        builder.appendObjectCreation(
            type = providerName,
            argumentCount = 2,
            argument = {
                when(it) {
                    0 -> appendComponentForBinding(builder = builder, inside = inside,
                        binding = binding, isInsideInnerClass = isInsideInnerClass)
                    1 -> append(slot.toString())
                }
            }
        )
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
        method(
            name = accessorName,
            access = Access.Internal,
        ) {
            returnType(TypeName.Optional(typeByDependencyKind(
                kind = dependencyKind,
                type = TypeName.Inferred(binding.target.type),
            )))
            when (val conditionScope = binding.conditionScope) {
                ConditionScope.Always -> code {
                    appendReturnStatement {
                        appendType(TypeName.OptionalRaw)
                        append(".of(")
                        underlying.generateAccess(this, dependencyKind, binding.owner, isInsideInnerClass = false)
                        append(")")
                    }
                }
                ConditionScope.Never -> code {
                    appendReturnStatement {
                        appendType(TypeName.OptionalRaw).append(".empty()")
                    }
                }
                is ConditionScope.ExpressionScope -> code {
                    appendReturnStatement {
                        appendTernaryExpression(
                            condition = {
                                binding.owner[GeneratorComponent].conditionGenerator.expression(
                                    builder = this,
                                    conditionScope = conditionScope,
                                    inside = binding.owner,
                                    isInsideInnerClass = false,
                                )
                            },
                            ifTrue = {
                                appendType(TypeName.OptionalRaw).append(".of(")
                                underlying.generateAccess(this, dependencyKind, binding.owner, isInsideInnerClass = false)
                                append(")")
                            },
                            ifFalse = {
                                appendType(TypeName.OptionalRaw).append(".empty()")
                            },
                        )
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
        appendComponentForBinding(builder = builder, inside = inside,
            binding = binding, isInsideInnerClass = isInsideInnerClass)
        builder.append(".").appendName(accessorName).append("()")
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
        builder.appendType(TypeName.OptionalRaw)
            .append(".empty()")
    }
}