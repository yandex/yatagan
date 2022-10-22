package com.yandex.daggerlite.core.lang

import org.assertj.core.api.SoftAssertions
import org.junit.Assert
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

inline fun softAssertions(block: SoftAssertions.() -> Unit) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

    SoftAssertions().apply(block).assertAll()
}

fun LangModelFactory.declaration(
    name: String,
    vararg names: String,
    packageName: String = "",
): TypeDeclarationLangModel {
    return getTypeDeclaration(packageName, name, *names).apply {
        Assert.assertNotNull("Class `$packageName.$name.${names.contentToString()}` is not defined", this)
    }!!
}
