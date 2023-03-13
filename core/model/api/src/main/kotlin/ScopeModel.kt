package com.yandex.yatagan.core.model

import com.yandex.yatagan.lang.AnnotationDeclaration

/**
 * Represents a scope of a binding or a component.
 */
public interface ScopeModel {
    public val customAnnotationClass: AnnotationDeclaration?

    public companion object {
        /**
         * Reusable scope.
         */
        public val Reusable: ScopeModel = object : ScopeModel {
            override val customAnnotationClass: AnnotationDeclaration? get() = null
            override fun toString() = "@Reusable [builtin]"
        }
    }
}