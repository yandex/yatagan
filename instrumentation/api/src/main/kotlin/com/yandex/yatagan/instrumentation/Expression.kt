package com.yandex.yatagan.instrumentation

import com.yandex.yatagan.core.model.DependencyKind
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.lang.Annotation
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.Type
import java.math.BigDecimal

public interface Expression {
    public fun <R> accept(visitor: Visitor<R>): R

    public interface Literal : Expression {
        override fun <R> accept(visitor: Expression.Visitor<R>): R = visitor.visitLiteral(this)

        public fun <R> accept(visitor: Visitor<R>): R

        public object Null : Literal {
            override fun <R> accept(visitor: Visitor<R>): R = visitor.visitNull()
        }

        public data class Boolean(val value: kotlin.Boolean) : Literal {
            override fun <R> accept(visitor: Visitor<R>): R = visitor.visitBoolean(this)
        }

        public data class Int(val value: kotlin.Int) : Literal {
            override fun <R> accept(visitor: Visitor<R>): R = visitor.visitInt(this)
        }

        public data class Byte(val value: kotlin.Byte) : Literal {
            override fun <R> accept(visitor: Visitor<R>): R = visitor.visitByte(this)
        }

        public data class Char(val value: kotlin.Char) : Literal {
            override fun <R> accept(visitor: Visitor<R>): R = visitor.visitChar(this)
        }

        public data class Short(val value: kotlin.Short) : Literal {
            override fun <R> accept(visitor: Visitor<R>): R = visitor.visitShort(this)
        }

        public data class Long(val value: kotlin.Long) : Literal {
            override fun <R> accept(visitor: Visitor<R>): R = visitor.visitLong(this)
        }

        public data class Double(val value: BigDecimal) : Literal {
            override fun <R> accept(visitor: Visitor<R>): R = visitor.visitDouble(this)
        }

        public data class Float(val value: BigDecimal) : Literal {
            override fun <R> accept(visitor: Visitor<R>): R = visitor.visitFloat(this)
        }

        public data class String(val value: kotlin.String) : Literal {
            override fun <R> accept(visitor: Visitor<R>): R = visitor.visitString(this)
        }

        public data class Class(val type: Type) : Literal {
            override fun <R> accept(visitor: Visitor<R>): R = visitor.visitClass(this)
        }

        public data class EnumConstant(val enum: Type, val constant: kotlin.String) : Literal {
            override fun <R> accept(visitor: Visitor<R>): R = visitor.visitEnumConstant(this)
        }

        public interface Visitor<R> {
            public fun visitNull(): R
            public fun visitBoolean(value: Boolean): R
            public fun visitInt(value: Int): R
            public fun visitByte(value: Byte): R
            public fun visitChar(value: Char): R
            public fun visitShort(value: Short): R
            public fun visitLong(value: Long): R
            public fun visitDouble(value: Double): R
            public fun visitFloat(value: Float): R
            public fun visitString(value: String): R
            public fun visitClass(value: Class): R
            public fun visitEnumConstant(value: EnumConstant): R
        }
    }

    public data class MethodCall(
        val method: Method,
        val receiver: Expression? = null,
        val arguments: List<Expression> = emptyList(),
    ) : Expression {
        override fun <R> accept(visitor: Visitor<R>): R = visitor.visitMethodCall(this)
    }

    public data class ReadValue(
        val name: String,
    ) : Expression {
        override fun <R> accept(visitor: Visitor<R>): R = visitor.visitReadValue(this)
    }

    public data class ResolveInstance(
        val type: Type,
        val qualifier: Annotation? = null,
        // TODO: maybe allow different `DependencyKind`s?
    ) : Expression {
        override fun <R> accept(visitor: Visitor<R>): R = visitor.visitResolveInstance(this)
    }

    public interface Visitor<R> {
        public fun visitLiteral(literal: Literal): R
        public fun visitMethodCall(methodCall: MethodCall): R
        public fun visitReadValue(readValue: ReadValue): R
        public fun visitResolveInstance(resolveInstance: ResolveInstance): R
    }
}