package com.yandex.yatagan.codegen.poetry.kotlin

import com.google.devtools.ksp.symbol.KSPropertyAccessor
import com.google.devtools.ksp.symbol.KSPropertyGetter
import com.google.devtools.ksp.symbol.KSPropertySetter
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import com.yandex.yatagan.base.api.Internal
import com.yandex.yatagan.codegen.poetry.ClassName
import com.yandex.yatagan.codegen.poetry.ExpressionBuilder
import com.yandex.yatagan.codegen.poetry.TypeName
import com.yandex.yatagan.lang.Field
import com.yandex.yatagan.lang.Member
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.compiled.SyntheticKotlinObjectInstanceFieldMarker

internal class ExpressionBuilderKotlinImpl(
    private val builder: CodeBlock.Builder = CodeBlock.builder(),
) : ExpressionBuilder {
    fun build(): CodeBlock = builder.build()

    override fun appendExplicitThis(className: ClassName) = apply {
        builder.add("this@%T", KotlinClassName(className))
    }

    override fun append(literalCode: String) = apply {
        builder.add("%L", literalCode)
    }

    override fun appendString(string: String) = apply {
        builder.add("%S", string)
    }

    override fun appendType(type: TypeName) = apply {
        builder.add("%T", KotlinTypeName(type))
    }

    override fun appendClassLiteral(type: TypeName) = apply {
        builder.add("%T::class.java", KotlinTypeName(type))
    }

    override fun appendCast(asType: TypeName, expression: ExpressionBuilder.() -> Unit) = apply {
        expression()
        builder.add(" as %T", KotlinTypeName(asType))
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
        builder.add("%T(", removeProjections(KotlinTypeName(type)))
        for (i in 0..<argumentCount) {
            argument(i)
            if (i != argumentCount - 1) {
                builder.add(", ")
            }
        }
        builder.add(")")
    }

    override fun appendName(memberName: String) = apply {
        builder.add("%N", memberName)
    }

    override fun appendTypeCheck(expression: ExpressionBuilder.() -> Unit, type: TypeName) = apply {
        expression()
        builder.add(" is %T", KotlinTypeName(type))
    }

    override fun appendTernaryExpression(
        condition: ExpressionBuilder.() -> Unit,
        ifTrue: ExpressionBuilder.() -> Unit,
        ifFalse: ExpressionBuilder.() -> Unit,
    ) = apply {
        builder.add("if (")
        condition()
        builder.add(") ")
        ifTrue()
        builder.add(" else ")
        ifFalse()
    }

    @OptIn(Internal::class)
    override fun appendDotAndAccess(member: Member) = apply {
        member.accept(object : Member.Visitor<Unit> {
            override fun visitField(model: Field) {
                if (model.platformModel == SyntheticKotlinObjectInstanceFieldMarker) {
                    // Skip the synthetic field at all
                    return
                }
                builder.add(".").add("%N", model.name)
            }

            override fun visitMethod(model: Method) {
                check(model.parameters.none()) { "Can't treat method with parameters as accessor" }
                builder.add(".")
                when(val pModel = model.platformModel) {
                    is KSPropertyAccessor -> {
                        builder.add("%N", pModel.receiver.simpleName.asString())
                    }
                    else -> {
                        builder.add("%N()", model.name)
                    }
                }
            }
            override fun visitOther(model: Member) = throw AssertionError()
        })
    }

    @OptIn(Internal::class)
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
            builder.add("%T", KotlinTypeName(method.owner.asType()))
                .add(".")
        }

        when(val model = method.platformModel) {
            is KSPropertyGetter -> {
                require(argumentCount == 0) { "No arguments expected for property getter" }
                builder.add("%N", model.receiver.simpleName.asString())
            }
            is KSPropertySetter -> {
                require(argumentCount == 1) { "Single argument expected for property getter" }
                builder.add("%N", model.receiver.simpleName.asString())
                builder.add(" = ")
                argument(0)
            }
            else -> {
                builder.add("%N(", method.name)
                for (i in 0..<argumentCount) {
                    argument(i)
                    if (i != argumentCount - 1) {
                        append(", ")
                    }
                }
                builder.add(")")
            }
        }
    }

    override fun coerceAsByte(expression: ExpressionBuilder.() -> Unit) = apply {
        expression()
        builder.add(".toByte()")
    }

    override fun appendCheckProvisionNotNull(expression: ExpressionBuilder.() -> Unit) = apply {
        builder.add("%M(", MemberName("com.yandex.yatagan.internal", "checkProvisionNotNull"))
        expression()
        builder.add(")")
    }

    override fun appendCheckInputNotNull(expression: ExpressionBuilder.() -> Unit) = apply {
        builder.add("%M(", MemberName("com.yandex.yatagan.internal", "checkInputNotNull"))
        expression()
        builder.add(")")
    }

    override fun appendReportUnexpectedBuilderInput(
        inputClassArgument: ExpressionBuilder.() -> Unit,
        expectedTypes: List<TypeName>,
    ) = apply {
        builder.add("TODO(\"appendReportUnexpectedBuilderInput\")")
    }

    override fun appendReportMissingBuilderInput(missingType: TypeName) = apply {
        builder.add("TODO(\"appendReportMissingBuilderInput\")")
    }
}