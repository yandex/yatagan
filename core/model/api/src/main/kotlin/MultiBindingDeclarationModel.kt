package com.yandex.daggerlite.core.model

import com.yandex.daggerlite.lang.FunctionLangModel
import com.yandex.daggerlite.lang.Type
import com.yandex.daggerlite.validation.MayBeInvalid

/**
 * A [com.yandex.daggerlite.Multibinds] model.
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
     * Denotes invalid [com.yandex.daggerlite.Multibinds] with unrecognized return value.
     */
    interface InvalidDeclarationModel : MultiBindingDeclarationModel {
        val invalidMethod: FunctionLangModel
    }
}