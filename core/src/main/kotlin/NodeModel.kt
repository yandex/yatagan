package com.yandex.daggerlite.core

import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.validation.MayBeInvalid

/**
 * Represents a node in a Dagger Graph, that can be resolved.
 * Basically, it's a type with some other information to fine tune resolution.
 */
interface NodeModel : ClassBackedModel, MayBeInvalid {
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
    fun multiBoundListNode(): NodeModel

    /**
     * What specific core-level model the node represents. `null` if none.
     */
    val superModel: HasNodeModel?

    /**
     * Returns a node that is equal to this one without qualifier.
     *
     * @return the same node only with [qualifier] = `null`.
     * If `this` node already has no qualifier, `this` is returned.
     */
    fun dropQualifier(): NodeModel
}
