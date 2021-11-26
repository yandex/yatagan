package com.yandex.daggerlite.jap.lang

import kotlinx.metadata.Flag
import kotlinx.metadata.KmClass
import kotlinx.metadata.jvm.KotlinClassHeader
import kotlinx.metadata.jvm.KotlinClassMetadata
import javax.lang.model.element.TypeElement

internal fun TypeElement.obtainKotlinClassIfApplicable(): KmClass? =
    when (val metadata = getAnnotation(Metadata::class.java)?.run {
        KotlinClassHeader(kind, metadataVersion, data1, data2, extraString, packageName, extraInt)
    }?.let(KotlinClassMetadata::read)) {
        is KotlinClassMetadata.Class -> metadata.toKmClass()
        else -> null
    }

internal val KmClass.isObject get() = Flag.Class.IS_OBJECT(flags)

internal val KmClass.isCompanionObject get() = Flag.Class.IS_COMPANION_OBJECT(flags)
