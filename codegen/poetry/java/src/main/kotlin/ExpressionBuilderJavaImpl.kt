package com.yandex.yatagan.codegen.poetry.java

import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.CodeBlock.Builder
import com.yandex.yatagan.codegen.poetry.ClassName
import com.yandex.yatagan.codegen.poetry.ExpressionBuilder
import com.yandex.yatagan.codegen.poetry.TypeName
import com.yandex.yatagan.lang.Field
import com.yandex.yatagan.lang.Member
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.TypeDeclarationKind

internal class ExpressionBuilderJavaImpl(
    private val builder: Builder = CodeBlock.builder(),
) : ExpressionBuilder {
    fun build(): CodeBlock = builder.build()

    override fun appendExplicitThis(className: ClassName) = apply {
        builder.add("\$T.this", JavaClassName(className))
    }

    override fun append(literalCode: String) = apply {
        builder.add("\$L", literalCode)
    }

    override fun appendString(string: String) = apply {
        builder.add("\$S", string)
    }

    override fun appendType(type: TypeName) = apply {
        builder.add("\$T", JavaTypeName(type))
    }

    override fun appendClassLiteral(type: TypeName) = apply {
        builder.add("\$T.class", JavaTypeName(type))
    }

    override fun appendCast(asType: TypeName, expression: ExpressionBuilder.() -> Unit) = apply {
        builder.add("(\$T) \$L", JavaTypeName(asType), buildExpression(expression))
    }

    override fun appendObjectCreation(
        type: TypeName,
        argumentCount: Int,
        argument: ExpressionBuilder.(index: Int) -> Unit,
        receiver: (ExpressionBuilder.() -> Unit)?,
    ) = apply {
        if (receiver != null) {
            receiver()
            builder.add(".")
        }
        builder.add("new \$T(", removeWildcards(type))
        for (i in 0..<argumentCount) {
            argument(i)
            if (i != argumentCount - 1) {
                builder.add(", ")
            }
        }
        builder.add(")")
    }

    override fun appendName(memberName: String) = apply {
        builder.add("\$N", memberName)
    }

    override fun appendTypeCheck(expression: ExpressionBuilder.() -> Unit, type: TypeName) = apply {
        expression()
        builder.add(" instanceof \$T", JavaTypeName(type))
    }

    override fun appendTernaryExpression(
        condition: ExpressionBuilder.() -> Unit,
        ifTrue: ExpressionBuilder.() -> Unit,
        ifFalse: ExpressionBuilder.() -> Unit,
    ) = apply {
        condition()
        builder.add(" ? ")
        ifTrue()
        builder.add(" : ")
        ifFalse()
    }

    override fun appendAccess(member: Member) = apply {
        member.accept(object : Member.Visitor<Unit> {
            override fun visitField(model: Field) {
                builder.add(model.name)
            }

            override fun visitMethod(model: Method) {
                check(model.parameters.none()) { "Can't treat method with parameters as accessor" }
                builder.add(model.name).add("()")
            }
            override fun visitOther(model: Member) = throw AssertionError()
        })
    }

    override fun appendCall(
        receiver: (ExpressionBuilder.() -> Unit)?,
        method: Method,
        argumentCount: Int,
        argument: ExpressionBuilder.(index: Int) -> Unit,
    ) = apply {
        check(method.parameters.count() == argumentCount) { "Invalid number of parameters" }
        if (receiver != null) {
            receiver()
            builder.add(".")
        } else {
            val ownerObject = when (method.owner.kind) {
                TypeDeclarationKind.KotlinObject -> ".INSTANCE"
                else -> ""
            }
            builder.add("\$T", JavaTypeName(method.owner.asType()))
                .add(ownerObject)
                .add(".")
        }
        builder.add(method.name).add("(")
        for (i in 0..<argumentCount) {
            argument(i)
            if (i != argumentCount - 1) {
                append(", ")
            }
        }
        builder.add(")")
    }

    override fun appendCheckProvisionNotNull(expression: ExpressionBuilder.() -> Unit) = apply {
        builder.add("\$T.checkProvisionNotNull(", JavaClassName.get("com.yandex.yatagan.internal", "Checks"))
        expression()
        builder.add(")")
    }

    override fun appendCheckInputNotNull(expression: ExpressionBuilder.() -> Unit) = apply {
        builder.add("\$T.checkInputNotNull(", JavaClassName.get("com.yandex.yatagan.internal", "Checks"))
        expression()
        builder.add(")")
    }

    override fun appendReportUnexpectedBuilderInput(
        inputClassArgument: ExpressionBuilder.() -> Unit,
        expectedTypes: List<TypeName>,
    ) = apply {
        builder.add("\$T.reportUnexpectedAutoBuilderInput(", JavaClassName.get("com.yandex.yatagan.internal", "Checks"))
        inputClassArgument()
        builder.add(", \$T.asList(", JavaClassName.get("java.util", "Arrays"))
        expectedTypes.forEachIndexed { index, typeName ->
            builder.add("\$T.class", JavaTypeName(typeName))
            if (index != expectedTypes.lastIndex) {
                builder.add(", ")
            }
        }
        builder.add("))")
    }

    override fun appendReportMissingBuilderInput(missingType: TypeName) = apply {
        builder.add("\$T.reportMissingAutoBuilderInput(\$T.class)",
            JavaClassName.get("com.yandex.yatagan.internal", "Checks"), JavaTypeName(missingType))
    }
}