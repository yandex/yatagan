package com.yandex.yatagan.core.model

import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.validation.MayBeInvalid

/**
 * A [com.yandex.yatagan.Multibinds] model.
 */
interface MultiBindingDeclarationModel : MayBeInvalid {

    fun <R> accept(visitor: Visitor<R>): R

    interface Visitor<R> {
        fun visitCollectionDeclaration(model: CollectionDeclarationModel): R
        fun visitMapDeclaration(model: MapDeclarationModel): R
        fun visitInvalid(model: InvalidDeclarationModel): R
    }

    /**
     * Declares empty list binding
     */
    interface CollectionDeclarationModel : MultiBindingDeclarationModel {
        /**
         * An element's type for a multi-bound list.
         */
        val elementType: NodeModel?

        /**
         * Target collection kind for a multi-binding.
         */
        val kind: CollectionTargetKind
    }

    /**
     * Declares empty map binding
     */
    interface MapDeclarationModel : MultiBindingDeclarationModel {
        val keyType: Type?
        val valueType: NodeModel?
    }

    /**
     * Denotes invalid [com.yandex.yatagan.Multibinds] with unrecognized return value.
     */
    interface InvalidDeclarationModel : MultiBindingDeclarationModel {
        val invalidMethod: Method
    }
}