package com.yandex.daggerlite.jap.lang

import kotlinx.metadata.Flag
import kotlinx.metadata.KmClass
import kotlinx.metadata.KmProperty
import kotlinx.metadata.jvm.JvmMethodSignature
import kotlinx.metadata.jvm.KotlinClassHeader
import kotlinx.metadata.jvm.KotlinClassMetadata
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.QualifiedNameable
import javax.lang.model.element.TypeElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.ExecutableType
import javax.lang.model.type.NoType
import javax.lang.model.type.PrimitiveType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.type.TypeVariable
import javax.lang.model.type.WildcardType

internal fun TypeElement.obtainKotlinClassIfApplicable(): KmClass? =
    when (val metadata = getAnnotation(Metadata::class.java)?.run {
        KotlinClassHeader(kind, metadataVersion, data1, data2, extraString, packageName, extraInt)
    }?.let(KotlinClassMetadata::read)) {
        is KotlinClassMetadata.Class -> metadata.toKmClass()
        else -> null
    }

internal val KmClass.isObject get() = Flag.Class.IS_OBJECT(flags)

internal val KmClass.isCompanionObject get() = Flag.Class.IS_COMPANION_OBJECT(flags)

internal val KmProperty.isOverride get() = Flag.Property.IS_FAKE_OVERRIDE(flags)

internal fun jvmSignatureOf(
    element: ExecutableElement,
    type: TypeMirror = element.asType(),
): JvmMethodSignature {
    return JvmMethodSignature(
        name = element.simpleName.toString(),
        desc = jvmDescriptorOf(type),
    )
}

private fun jvmDescriptorOf(type: TypeMirror): String {
    return when (type) {
        is NoType -> "V"
        is ExecutableType -> buildString {
            append("(")
            for (parameterType in type.parameterTypes)
                append(jvmDescriptorOf(parameterType))
            append(")").append(jvmDescriptorOf(type.returnType))
        }
        is ArrayType -> "[${jvmDescriptorOf(type.componentType)}"
        is WildcardType,
        is TypeVariable,
        -> jvmDescriptorOf(Utils.types.erasure(type))
        is PrimitiveType -> when (type.kind) {
            TypeKind.BYTE -> "B"
            TypeKind.CHAR -> "C"
            TypeKind.DOUBLE -> "D"
            TypeKind.FLOAT -> "F"
            TypeKind.INT -> "I"
            TypeKind.LONG -> "J"
            TypeKind.SHORT -> "S"
            TypeKind.BOOLEAN -> "Z"
            else -> throw AssertionError("Not reached: not a primitive type ${type.kind}")
        }
        is DeclaredType -> {
            val internalName = when (val element = type.asElement()) {
                is QualifiedNameable -> element.qualifiedName.toString().replace('.', '/')
                else -> element.simpleName.toString()
            }
            return "L$internalName;"
        }
        else -> throw AssertionError("Not reached: unsupported type for jvm-signature: $type")
    }
}