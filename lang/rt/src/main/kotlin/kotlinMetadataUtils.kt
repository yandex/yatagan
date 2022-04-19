package com.yandex.daggerlite.lang.rt

import kotlinx.metadata.Flag
import kotlinx.metadata.KmClass
import kotlinx.metadata.KmProperty
import kotlinx.metadata.jvm.JvmMethodSignature
import kotlinx.metadata.jvm.KotlinClassHeader
import kotlinx.metadata.jvm.KotlinClassMetadata
import java.lang.reflect.Method

internal fun Class<*>.obtainKotlinClassIfApplicable(): KmClass? =
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
    method: Method,
): JvmMethodSignature {
    val descriptor = buildString {
        append('(')
        for (parameterType in method.parameterTypes) {
            append(jvmDescriptorOf(parameterType))
        }
        append(')')
        append(jvmDescriptorOf(method.returnType))
    }
    return JvmMethodSignature(
        name = method.name,
        desc = descriptor,
    )
}

private fun jvmDescriptorOf(clazz: Class<*>): String {
    return when {
        clazz.isPrimitive -> when (clazz) {
            java.lang.Boolean.TYPE -> "Z"
            java.lang.Byte.TYPE -> "B"
            java.lang.Short.TYPE -> "S"
            java.lang.Character.TYPE -> "C"
            java.lang.Integer.TYPE -> "I"
            java.lang.Long.TYPE -> "J"
            java.lang.Float.TYPE -> "F"
            java.lang.Double.TYPE -> "D"
            java.lang.Void.TYPE -> "V"
            else -> throw AssertionError("Not reached: not a primitive type: $clazz")
        }
        clazz.isArray -> "[${jvmDescriptorOf(clazz.componentType)}"
        else -> "L${clazz.canonicalName.replace('.', '/')};"
    }
}