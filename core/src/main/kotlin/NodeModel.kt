package com.yandex.daggerlite.core

import com.yandex.daggerlite.core.lang.AnnotationLangModel

/**
 * Represents a node in a Dagger Graph, that can be resolved.
 * Basically, it's a type with some other information to fine tune resolution.
 */
interface NodeModel : ClassBackedModel {
    /**
     * Optional qualifier.
     * An opaque object representing additional qualifier information that can help to disambiguate nodes with the
     * same type.
     */
    val qualifier: AnnotationLangModel?

    /**
     * self-provision binding if supported by underlying type.
     */
    val implicitBinding: InjectConstructorBindingModel?

    /**
     * TODO: doc.
     */
    val bootstrapInterfaces: Collection<BootstrapInterfaceModel>
}
