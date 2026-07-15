/*
 * Copyright 2026 Yandex LLC
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

@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package com.yandex.yatagan.lang.kcp

import com.yandex.yatagan.lang.Annotation
import com.yandex.yatagan.lang.AnnotationDeclaration
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.compiled.CtAnnotationBase
import com.yandex.yatagan.lang.compiled.CtAnnotationDeclarationBase
import com.yandex.yatagan.lang.scope.FactoryKey
import com.yandex.yatagan.lang.scope.LexicalScope
import com.yandex.yatagan.lang.scope.caching
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrAnnotation
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.parentAsClass

internal class KcpAnnotationImpl(
    private val lexicalScope: LexicalScope,
    private val impl: IrAnnotation,
) : CtAnnotationBase(), LexicalScope by lexicalScope {
    private val declaration: IrClass
        get() = impl.symbol.owner.parentAsClass

    override val annotationClass: AnnotationDeclaration
        get() = KcpAnnotationDeclarationImpl(declaration.symbol)

    override val platformModel: IrAnnotation
        get() = impl

    override fun getValue(attribute: AnnotationDeclaration.Attribute): Annotation.Value {
        require(attribute is KcpAnnotationAttributeImpl && attribute.owner == declaration.symbol) {
            "Attribute does not belong to ${annotationClass.qualifiedName}"
        }
        val expression = impl.arguments.getOrNull(attribute.impl.indexInParameters)
            ?: attribute.impl.defaultValue?.expression
        return ValueImpl(expression)
    }

    override fun equals(other: Any?): Boolean {
        return this === other || other is KcpAnnotationImpl && toString() == other.toString()
    }

    override fun hashCode(): Int = toString().hashCode()

    private inner class ValueImpl(
        private val expression: IrExpression?,
    ) : ValueBase() {
        private val identity by lazy {
            accept(object : Annotation.Value.Visitor<Any?> {
                override fun visitDefault(value: Any?) = throw AssertionError()
                override fun visitBoolean(value: Boolean) = value
                override fun visitByte(value: Byte) = value
                override fun visitShort(value: Short) = value
                override fun visitInt(value: Int) = value
                override fun visitLong(value: Long) = value
                override fun visitChar(value: Char) = value
                override fun visitFloat(value: Float) = value
                override fun visitDouble(value: Double) = value
                override fun visitString(value: String) = value
                override fun visitType(value: Type) = value
                override fun visitAnnotation(value: Annotation) = value
                override fun visitEnumConstant(enum: Type, constant: String) = enum to constant
                override fun visitArray(value: List<Annotation.Value>) = value
                override fun visitUnresolved() = null
            })
        }

        override val platformModel: IrExpression?
            get() = expression

        override fun <R> accept(visitor: Annotation.Value.Visitor<R>): R {
            return when (val value = expression) {
                is IrConst -> when (value.kind) {
                    IrConstKind.Boolean -> visitor.visitBoolean(value.value as Boolean)
                    IrConstKind.Byte -> visitor.visitByte(value.value as Byte)
                    IrConstKind.Short -> visitor.visitShort(value.value as Short)
                    IrConstKind.Int -> visitor.visitInt(value.value as Int)
                    IrConstKind.Long -> visitor.visitLong(value.value as Long)
                    IrConstKind.Char -> visitor.visitChar(value.value as Char)
                    IrConstKind.Float -> visitor.visitFloat(value.value as Float)
                    IrConstKind.Double -> visitor.visitDouble(value.value as Double)
                    IrConstKind.String -> visitor.visitString(value.value as String)
                    else -> visitor.visitUnresolved()
                }
                is IrClassReference -> visitor.visitType(kcpType(value.classType))
                is IrAnnotation -> visitor.visitAnnotation(KcpAnnotationImpl(this@KcpAnnotationImpl, value))
                is IrGetEnumValue -> visitor.visitEnumConstant(
                    enum = kcpType(value.symbol.owner.parentAsClass.symbol.defaultType),
                    constant = value.symbol.owner.name.asString(),
                )
                is IrVararg -> visitor.visitArray(value.elements.map { element ->
                    ValueImpl(element as? IrExpression)
                })
                null -> visitor.visitUnresolved()
                else -> visitor.visitUnresolved()
            }
        }

        override fun equals(other: Any?): Boolean {
            return this === other || other is ValueImpl && identity == other.identity
        }

        override fun hashCode(): Int = identity.hashCode()
    }
}

internal class KcpAnnotationAttributeImpl(
    val owner: IrClassSymbol,
    val impl: IrValueParameter,
    private val lexicalScope: LexicalScope,
) : AnnotationDeclaration.Attribute {
    override val name: String
        get() = impl.name.asString()

    override val type: Type
        get() = lexicalScope.kcpType(impl.type)
}

internal class KcpAnnotationDeclarationImpl private constructor(
    private val lexicalScope: LexicalScope,
    private val impl: IrClassSymbol,
) : CtAnnotationDeclarationBase(), LexicalScope by lexicalScope {
    private val declaration: IrClass
        get() = impl.owner

    override val annotations: Sequence<CtAnnotationBase>
        get() = declaration.annotations.asSequence().map { KcpAnnotationImpl(this, it) }

    override val qualifiedName: String
        get() = kcpType(impl.defaultType).declaration.qualifiedName

    override val attributes: Sequence<AnnotationDeclaration.Attribute>
        get() = declaration.constructors.asSequence()
            .singleOrNull()
            ?.nonDispatchParameters
            .orEmpty()
            .asSequence()
            .map { KcpAnnotationAttributeImpl(impl, it, this) }

    override fun getRetention(): AnnotationRetention {
        for (annotation in declaration.annotations) {
            val annotationName = annotation.symbol.owner.parentAsClass.let {
                kcpType(it.symbol.defaultType).declaration.qualifiedName
            }
            val enumValue = annotation.arguments.firstOrNull() as? IrGetEnumValue ?: continue
            when (annotationName) {
                "kotlin.annotation.Retention" -> return when (enumValue.symbol.owner.name.asString()) {
                    "SOURCE" -> AnnotationRetention.SOURCE
                    "BINARY" -> AnnotationRetention.BINARY
                    "RUNTIME" -> AnnotationRetention.RUNTIME
                    else -> continue
                }
                "java.lang.annotation.Retention" -> return when (enumValue.symbol.owner.name.asString()) {
                    "SOURCE" -> AnnotationRetention.SOURCE
                    "CLASS" -> AnnotationRetention.BINARY
                    "RUNTIME" -> AnnotationRetention.RUNTIME
                    else -> continue
                }
            }
        }
        return if (declaration.origin == IrDeclarationOrigin.IR_EXTERNAL_JAVA_DECLARATION_STUB) {
            AnnotationRetention.BINARY
        } else {
            AnnotationRetention.RUNTIME
        }
    }

    companion object Factory : FactoryKey<IrClassSymbol, KcpAnnotationDeclarationImpl> {
        override fun LexicalScope.factory() = caching(::KcpAnnotationDeclarationImpl)
    }
}
