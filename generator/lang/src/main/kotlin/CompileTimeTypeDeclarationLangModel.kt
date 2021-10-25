package com.yandex.daggerlite.generator.lang

import com.yandex.daggerlite.Component
import com.yandex.daggerlite.Module
import com.yandex.daggerlite.core.lang.ComponentAnnotationLangModel
import com.yandex.daggerlite.core.lang.ModuleAnnotationLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.hasType

/**
 * [TypeDeclarationLangModel] specialized for compile time implementations.
 */
interface CompileTimeTypeDeclarationLangModel : TypeDeclarationLangModel {
    override val annotations: Sequence<CompileTimeAnnotationLangModel>

    override val componentAnnotationIfPresent: ComponentAnnotationLangModel?
        get() = annotations.find { it.hasType<Component>() }?.let(::CompileTimeComponentAnnotationImpl)
    override val moduleAnnotationIfPresent: ModuleAnnotationLangModel?
        get() = annotations.find { it.hasType<Module>() }?.let(::CompileTimeModuleAnnotationImpl)
}