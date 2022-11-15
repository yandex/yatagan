package com.yandex.yatagan.core.model

import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.validation.MayBeInvalid

/**
 * A [com.yandex.yatagan.Multibinds] model.
 */
public interface MultiBindingDeclarationModel : MayBeInvalid {

    public fun <R> accept(visitor: Visitor<R>): R

    public interface Visitor<R> {
        public fun visitCollectionDeclaration(model: CollectionDeclarationModel): R
        public fun visitMapDeclaration(model: MapDeclarationModel): R
        public fun visitInvalid(model: InvalidDeclarationModel): R
    }

    /**
     * Declares empty list binding
     */
    public interface CollectionDeclarationModel : MultiBindingDeclarationModel {
        /**
         * An element's type for a multi-bound list.
         */
        public val elementType: NodeModel?

        /**
         * Target collection kind for a multi-binding.
         */
        public val kind: CollectionTargetKind
    }

    /**
     * Declares empty map binding
     */
    public interface MapDeclarationModel : MultiBindingDeclarationModel {
        public val keyType: Type?
        public val valueType: NodeModel?
    }

    /**
     * Denotes invalid [com.yandex.yatagan.Multibinds] with unrecognized return value.
     */
    public interface InvalidDeclarationModel : MultiBindingDeclarationModel {
        public val invalidMethod: Method
    }
}