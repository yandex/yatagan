package com.yandex.daggerlite.core

import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.validation.MayBeInvalid

/**
 * A [com.yandex.daggerlite.Multibinds] model.
 */
interface MultiBindingDeclarationModel : MayBeInvalid {

    fun <R> accept(visitor: Visitor<R>): R

    interface Visitor<R> {
        fun visitListDeclaration(model: ListDeclarationModel): R
        fun visitMapDeclaration(model: MapDeclarationModel): R
        fun visitInvalid(model: InvalidDeclarationModel): R
    }

    /**
     * Declares empty list binding
     */
    interface ListDeclarationModel : MultiBindingDeclarationModel {
        /**
         * An element's type for a multi-bound list.
         */
        val listType: NodeModel?
    }

    /**
     * Declares empty map binding
     */
    interface MapDeclarationModel : MultiBindingDeclarationModel {
        val keyType: TypeLangModel?
        val valueType: NodeModel?
    }

    /**
     * Denotes invalid [com.yandex.daggerlite.Multibinds] with unrecognized return value.
     */
    interface InvalidDeclarationModel : MultiBindingDeclarationModel {
        val invalidMethod: FunctionLangModel
    }
}