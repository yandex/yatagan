package com.yandex.yatagan.lang.jap

import com.yandex.yatagan.lang.LangModelFactory
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.common.scope.LexicalScopeBase
import com.yandex.yatagan.lang.compiled.scope.CachingFactorySimple
import com.yandex.yatagan.lang.scope.CachingMetaFactory
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

/**
 * Main entry point into javax annotation processing backed lang.
 *
 * @param types javax types utility
 * @param elements javax elements utility
 */
class JavaxLexicalScope(
    types: Types,
    elements: Elements,
) : LexicalScopeBase() {
    init {
        ext[CachingMetaFactory] = CachingFactorySimple
        ext[ProcessingUtils] = ProcessingUtils(types, elements)
        ext[LangModelFactory] = JavaxModelFactoryImpl(this)
    }

    fun getTypeDeclaration(typeElement: TypeElement): TypeDeclaration {
        return JavaxTypeDeclarationImpl(typeElement.asType().asDeclaredType())
    }
}
