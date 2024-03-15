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
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.TypeName
import com.yandex.yatagan.codegen.poetry.CodeBuilder
import com.yandex.yatagan.codegen.poetry.ExpressionBuilder
import com.yandex.yatagan.codegen.poetry.buildExpression
import com.yandex.yatagan.codegen.poetry.invoke
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.DependencyKind
import com.yandex.yatagan.instrumentation.Expression
import com.yandex.yatagan.instrumentation.Statement
import com.yandex.yatagan.instrumentation.impl.asNode
import com.yandex.yatagan.instrumentation.spi.InstrumentableAfter
import com.yandex.yatagan.lang.TypeDeclarationKind
import com.yandex.yatagan.lang.compiled.ClassNameModel
import com.yandex.yatagan.lang.compiled.ParameterizedNameModel

internal fun componentInstance(
    inside: BindingGraph,
    graph: BindingGraph,
    isInsideInnerClass: Boolean,
): CodeBlock {
    return buildExpression {
        if (isInsideInnerClass) {
            +"%T.this".formatCode(inside[GeneratorComponent].implementationClassName)
        } else {
            +"this"
        }
        if (inside != graph) {
            +".%N".formatCode(
                inside[GeneratorComponent].componentFactoryGenerator.fieldNameFor(graph = graph)
            )
        }
    }
}

internal fun componentForBinding(
    inside: BindingGraph,
    binding: Binding,
    isInsideInnerClass: Boolean,
): CodeBlock {
    return componentInstance(
        inside = inside,
        graph = binding.owner,
        isInsideInnerClass = isInsideInnerClass,
    )
}

internal fun Binding.generateAccess(
    builder: ExpressionBuilder,
    inside: BindingGraph,
    isInsideInnerClass: Boolean,
    kind: DependencyKind = DependencyKind.Direct,
) {
    owner[GeneratorComponent].accessStrategyManager.strategyFor(this).generateAccess(
        builder = builder,
        inside = inside,
        isInsideInnerClass = isInsideInnerClass,
        kind = kind,
    )
}

internal inline fun CodeBuilder.generateUnderCondition(
    binding: Binding,
    inside: BindingGraph,
    isInsideInnerClass: Boolean,
    underConditionBlock: CodeBuilder.() -> Unit,
) {
    when (val conditionScope = binding.conditionScope) {
        ConditionScope.Always -> underConditionBlock()
        ConditionScope.Never -> {}
        is ConditionScope.ExpressionScope -> {
            val expression = buildExpression {
                val gen = binding.owner[GeneratorComponent].conditionGenerator
                gen.expression(
                    builder = this,
                    conditionScope = conditionScope,
                    inside = inside,
                    isInsideInnerClass = isInsideInnerClass,
                )
            }
            controlFlow("if (%L) ".formatCode(expression)) {
                underConditionBlock()
            }
        }
    }
}

internal fun formatImplementationClassName(graph: BindingGraph): ClassName {
    return when (val parent = graph.parent) {
        null -> graph.model.name.let {
            val name = when (it) {
                is ClassNameModel -> it
                is ParameterizedNameModel -> it.raw
                else -> throw AssertionError("Unexpected component name: $it")
            }
            // Keep name mangling in sync with loader!
            ClassName.get(name.packageName, "Yatagan$" + name.simpleNames.joinToString(separator = "$"))
        }

        else -> with(parent[GeneratorComponent]) {
            implementationClassName.nestedClass(
                subcomponentsNamespace.name(graph.model.name, suffix = "Impl", firstCapital = true)
            )
        }
    }
}

internal fun CodeBuilder.doInstrument(
    graph: BindingGraph,
    statements: List<Statement>,
    instrumentedInstance: Pair<String, TypeName>? = null,
) {
    if (statements.isEmpty()) return

    val context = InstrumentationContext(
        graph = graph,
        instrumentedInstance = instrumentedInstance,
    )
    for (statement in statements) {
        statement.accept(InstrumentationCodeBuilder(builder = this, context = context))
    }
}

internal fun mangleInstrumentationValueName(name: String) = "i\$$name"

private class InstrumentationContext(
    val graph: BindingGraph,
    val instrumentedInstance: Pair<String, TypeName>?,
) {
    val userValues: MutableMap<String, TypeName> = hashMapOf()
}

private class InstrumentationCodeBuilder(
    private val builder: CodeBuilder,
    private val context: InstrumentationContext,
) : Statement.Visitor<Unit> {
    override fun visitEvaluate(evaluate: Statement.Evaluate) = with(builder) {
        +buildExpression { evaluate.expression.accept(InstrumentationExpressionBuilder(this, context)) }
    }

    override fun visitAssignment(assignment: Statement.Assignment) = with(builder) {
        val (name, expression) = assignment
        val valueType = expression.accept(TypeResolver(context.userValues))
        context.userValues[name] = valueType
        +"%T %N = %L".formatCode(valueType, mangleInstrumentationValueName(name),
            buildExpression { expression.accept(InstrumentationExpressionBuilder(this, context)) })
    }
}

private class InstrumentationExpressionBuilder(
    private val builder: ExpressionBuilder,
    private val context: InstrumentationContext,
) : Expression.Visitor<Unit>, Expression.Literal.Visitor<Unit> {

    override fun visitLiteral(literal: Expression.Literal) {
        literal.accept(this as Expression.Literal.Visitor<Unit>)
    }

    override fun visitMethodCall(methodCall: Expression.MethodCall) = with(builder) {
        val (method, receiver, arguments) = methodCall
        val ownerKind = method.owner.kind
        if (method.isStatic || ownerKind == TypeDeclarationKind.KotlinCompanion) {
            require(receiver == null) { "Unexpected receiver" }
            +"%T.%N(".formatCode(method.owner.asType().typeName(), method.name)
        } else if (ownerKind == TypeDeclarationKind.KotlinObject) {
            require(receiver == null) { "Unexpected receiver" }
            +"%T.INSTANCE.%N(".formatCode(method.owner.asType().typeName(), method.name)
        } else {
            requireNotNull(receiver) { "Expected a non-null receiver" }
            +"(%L).%N(".formatCode(
                buildExpression { receiver.accept(InstrumentationExpressionBuilder(this, context)) },
                method.name,
            )
        }
        join(seq = arguments) { argument ->
            argument.accept(InstrumentationExpressionBuilder(this, context))
        }
        +")"
    }

    override fun visitReadValue(readValue: Expression.ReadValue) = with(builder) {
        val (name) = readValue
        when(name) {
            InstrumentableAfter.INSTANCE_VALUE_NAME -> {
                checkNotNull(context.instrumentedInstance) { "`$name` is not available in this context" }
                +"%N".formatCode(context.instrumentedInstance.first)
            }
            else -> {
                require(readValue.name in context.userValues) { "`$name` is not defined" }
                +"%N".formatCode(mangleInstrumentationValueName(readValue.name))
            }
        }
    }

    override fun visitResolveInstance(resolveInstance: Expression.ResolveInstance) {
        val binding = context.graph.resolveBinding(resolveInstance.asNode())
        binding.generateAccess(builder = builder, inside = context.graph, isInsideInnerClass = false)
    }

    //

    override fun visitNull() = with(builder) { +"null" }

    override fun visitBoolean(value: Expression.Literal.Boolean) = with(builder) {
        +if (value.value) "true" else "false"
    }

    override fun visitInt(value: Expression.Literal.Int) = with(builder) { +value.value.toString() }

    override fun visitByte(value: Expression.Literal.Byte) = with(builder) { +value.value.toString() }

    override fun visitChar(value: Expression.Literal.Char) = with(builder) { +"'%L'".formatCode(value.value) }

    override fun visitShort(value: Expression.Literal.Short) = with(builder) { +value.value.toString() }

    override fun visitLong(value: Expression.Literal.Long) = with(builder) { +(value.value.toString() + 'L') }

    override fun visitDouble(value: Expression.Literal.Double) = with(builder) { +value.value.toString() }

    override fun visitFloat(value: Expression.Literal.Float) = with(builder) { +(value.value.toString() + 'f') }

    override fun visitString(value: Expression.Literal.String) = with(builder) { +"%S".formatCode(value.value) }

    override fun visitClass(value: Expression.Literal.Class) = with(builder) {
        +"%T.class".formatCode(value.type.typeName())
    }

    override fun visitEnumConstant(value: Expression.Literal.EnumConstant) = with(builder) {
        +"%T.%N".formatCode(value.enum.typeName(), value.constant)
    }
}

private object LiteralTypeResolver : Expression.Literal.Visitor<TypeName> {
    override fun visitNull(): TypeName = TypeName.OBJECT  // Is that right?
    override fun visitBoolean(value: Expression.Literal.Boolean): TypeName = TypeName.BOOLEAN
    override fun visitInt(value: Expression.Literal.Int): TypeName = TypeName.INT
    override fun visitByte(value: Expression.Literal.Byte): TypeName = TypeName.BYTE
    override fun visitChar(value: Expression.Literal.Char): TypeName = TypeName.CHAR
    override fun visitShort(value: Expression.Literal.Short): TypeName = TypeName.SHORT
    override fun visitLong(value: Expression.Literal.Long): TypeName = TypeName.LONG
    override fun visitDouble(value: Expression.Literal.Double): TypeName = TypeName.DOUBLE
    override fun visitFloat(value: Expression.Literal.Float): TypeName = TypeName.FLOAT
    override fun visitString(value: Expression.Literal.String) = Names.String
    override fun visitClass(value: Expression.Literal.Class) = Names.Class(value.type.typeName())
    override fun visitEnumConstant(value: Expression.Literal.EnumConstant) = value.enum.typeName()
}

private class TypeResolver(
    private val values: Map<String, TypeName>,
) : Expression.Visitor<TypeName> {
    override fun visitLiteral(literal: Expression.Literal) = literal.accept(LiteralTypeResolver)
    override fun visitMethodCall(methodCall: Expression.MethodCall) = methodCall.method.returnType.typeName()
    override fun visitReadValue(readValue: Expression.ReadValue) =
        // TODO: instrumented instance support?
        checkNotNull(values[readValue.name]) { "Undeclared value '$readValue'" }

    override fun visitResolveInstance(resolveInstance: Expression.ResolveInstance) = resolveInstance.type.typeName()
}