package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.lang.AnnotationDeclarationLangModel
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.AnnotationLangModel.Value
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.generator.lang.CtAnnotationLangModel
import kotlin.LazyThreadSafetyMode.PUBLICATION

internal class KspAnnotationImpl(
    private val impl: KSAnnotation,
) : CtAnnotationLangModel() {
    private val descriptor by lazy {
        this@KspAnnotationImpl.toString()
    }

    override val annotationClass: AnnotationDeclarationLangModel by lazy {
        AnnotationClassImpl(
            annotation = impl,
        )
    }

    override val platformModel: KSAnnotation
        get() = impl

    override fun getValue(attribute: AnnotationDeclarationLangModel.Attribute): Value {
        require(attribute is AttributeImpl) { "Invalid attribute type" }
        val arg = impl.arguments.find { (it.name?.asString() ?: "value") == attribute.name }
        requireNotNull(arg) {
            "Attribute with the name ${attribute.name} is missing"
        }
        return ValueImpl(arg.value)
    }

    override fun hashCode(): Int = descriptor.hashCode()

    override fun equals(other: Any?): Boolean {
        return this === other || (other is KspAnnotationImpl && descriptor == other.descriptor)
    }

    private class ValueImpl(
        private val value: Any?,
    ) : ValueBase() {
        private val identity by lazy(PUBLICATION) {
            accept(object : Value.Visitor<Any?> {
                override fun visitBoolean(value: Boolean) = value
                override fun visitByte(value: Byte) = value
                override fun visitShort(value: Short) = value
                override fun visitInt(value: Int) = value
                override fun visitLong(value: Long) = value
                override fun visitChar(value: Char) = value
                override fun visitFloat(value: Float) = value
                override fun visitDouble(value: Double) = value
                override fun visitString(value: String) = value
                override fun visitType(value: TypeLangModel) = value
                override fun visitAnnotation(value: AnnotationLangModel) = value
                override fun visitEnumConstant(enum: TypeLangModel, constant: String) = enum to constant
                override fun visitArray(value: List<Value>) = value
                override fun visitUnresolved() = null
            })
        }

        override val platformModel: Any?
            get() = value

        override fun <R> accept(visitor: Value.Visitor<R>): R {
            return when(value) {
                is Boolean -> visitor.visitBoolean(value)
                is Byte -> visitor.visitByte(value)
                is Char -> visitor.visitChar(value)
                is Double -> visitor.visitDouble(value)
                is Float -> visitor.visitFloat(value)
                is Int -> visitor.visitInt(value)
                is Long -> visitor.visitLong(value)
                is Short -> visitor.visitShort(value)
                is String -> visitor.visitString(value)
                is KSType -> {
                    val declaration = value.declaration as? KSClassDeclaration
                    if (declaration != null && declaration.classKind == ClassKind.ENUM_ENTRY) {
                        val enumDeclaration = declaration.parentDeclaration as KSClassDeclaration
                        visitor.visitEnumConstant(
                            enum = KspTypeImpl(impl = enumDeclaration.asType(emptyList())),
                            constant = declaration.simpleName.asString(),
                        )
                    } else {
                        visitor.visitType(KspTypeImpl(impl = value))
                    }
                }
                is KSAnnotation -> visitor.visitAnnotation(KspAnnotationImpl(value))
                is List<*> -> visitor.visitArray(value.map { ValueImpl(it ?: "<error>") })
                null -> visitor.visitUnresolved()
                else -> throw AssertionError("Unexpected value type: $value")
            }
        }

        override fun equals(other: Any?) = this === other ||
                (other is ValueImpl && other.identity == other.identity)

        override fun hashCode() = identity.hashCode()
    }

    private class AttributeImpl(
        val impl: KSPropertyDeclaration,
    ) : AnnotationDeclarationLangModel.Attribute {

        override val name: String
            get() = impl.simpleName.asString()

        override val type: TypeLangModel by lazy {
            val kotlinType = impl.type.resolve().resolveAliasIfNeeded()
            val declaration = kotlinType.declaration as? KSClassDeclaration
            if (declaration?.qualifiedName?.asString() == "kotlin.reflect.KClass") {
                // Replacing `kotlin.reflect.KClass<*>` with `java.lang.Class<?>`
                KspTypeImpl(
                    impl = Utils.classType.asType(kotlinType.arguments),
                )
            } else {
                KspTypeImpl(
                    reference = impl.type,
                    jvmSignatureHint = Utils.resolver.mapToJvmSignature(impl),
                )
            }
        }
    }

    private class AnnotationClassImpl private constructor(
        private val reference: KSTypeReference,
        private val shortName: String,
    ) : AnnotationDeclarationLangModel {
        private val annotated by lazy {
            KspAnnotatedImpl(reference.resolve().resolveAliasIfNeeded().declaration as KSClassDeclaration)
        }

        override val annotations: Sequence<AnnotationLangModel>
            get() = annotated.annotations

        override fun <A : Annotation> isAnnotatedWith(type: Class<A>): Boolean {
            return annotated.isAnnotatedWith(type)
        }

        override val attributes: Sequence<AnnotationDeclarationLangModel.Attribute> by lazy {
            annotated.impl.getAllProperties().map {
                AttributeImpl(impl = it)
            }.memoize()
        }

        override fun isClass(clazz: Class<out Annotation>): Boolean {
            return shortName == clazz.simpleName &&
                    annotated.impl.qualifiedName?.asString() == clazz.canonicalName
        }

        override fun toString() = annotated.impl.qualifiedName?.asString() ?: ""

        companion object Factory : ObjectCache<Pair<KSTypeReference, String>, AnnotationClassImpl>() {
            operator fun invoke(
                annotation: KSAnnotation,
            ): AnnotationClassImpl {
                val shortName = annotation.shortName.getShortName()
                val reference = annotation.annotationType
                return createCached(reference to shortName) {
                    AnnotationClassImpl(
                        reference = reference,
                        shortName = shortName,
                    )
                }
            }
        }
    }
}
