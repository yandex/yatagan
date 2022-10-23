package com.yandex.daggerlite.core.model.impl

import com.yandex.daggerlite.base.setOf
import com.yandex.daggerlite.core.model.CollectionTargetKind
import com.yandex.daggerlite.core.model.MultiBindingDeclarationModel
import com.yandex.daggerlite.core.model.NodeModel
import com.yandex.daggerlite.lang.FunctionLangModel
import com.yandex.daggerlite.lang.Type
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.format.Strings
import com.yandex.daggerlite.validation.format.TextColor
import com.yandex.daggerlite.validation.format.append
import com.yandex.daggerlite.validation.format.modelRepresentation
import com.yandex.daggerlite.validation.format.reportError
import kotlin.LazyThreadSafetyMode.PUBLICATION

internal abstract class MultiBindingDeclarationBase(
    protected val method: FunctionLangModel,
) : MultiBindingDeclarationModel {
    override fun validate(validator: Validator) {
        if (!method.isAbstract || method.parameters.any()) {
            validator.reportError(Strings.Errors.invalidMultiBindingDeclaration()) {
                addNote(Strings.Notes.invalidMultiBindingAdvice())
            }
        }
    }
}

internal class CollectionDeclarationImpl(
    method: FunctionLangModel,
) : MultiBindingDeclarationBase(method), MultiBindingDeclarationModel.CollectionDeclarationModel {
    init {
        assert(canRepresent(method))
    }

    override val elementType: NodeModel?
        get() = method.returnType.typeArguments.firstOrNull()?.let { type ->
            NodeModelImpl(
                type = type,
                forQualifier = method,
            )
        }

    override fun <R> accept(visitor: MultiBindingDeclarationModel.Visitor<R>): R {
        return visitor.visitCollectionDeclaration(this)
    }

    override fun validate(validator: Validator) {
        super.validate(validator)
        if (method.returnType.typeArguments.isEmpty()) {
            validator.reportError(Strings.Errors.invalidMultiBindingDeclaration()) {
                addNote(Strings.Notes.invalidMultiBindingAdvice())
            }
        }
    }

    override fun toString(childContext: MayBeInvalid?): CharSequence {
        val type = when (kind) {
            CollectionTargetKind.List -> "list"
            CollectionTargetKind.Set -> "set"
        }
        return modelRepresentation(modelClassName = "multibinding declaration ($type)") {
            append(method)
        }
    }

    override val kind: CollectionTargetKind by lazy(PUBLICATION) {
        when (method.returnType.declaration.qualifiedName) {
            Names.List -> CollectionTargetKind.List
            Names.Set -> CollectionTargetKind.Set
            else -> throw AssertionError("Not reached")
        }
    }

    override fun hashCode(): Int {
        var result = elementType?.hashCode() ?: 0
        result = 31 * result + kind.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        return this === other || (other is CollectionDeclarationImpl &&
                kind == other.kind &&
                elementType == other.elementType)
    }

    companion object {
        private val SupportedCollectionNames = setOf(Names.List, Names.Set)

        fun canRepresent(method: FunctionLangModel): Boolean {
            return method.returnType.declaration.qualifiedName in SupportedCollectionNames
        }
    }
}

internal class MapDeclarationImpl(
    method: FunctionLangModel,
) : MultiBindingDeclarationBase(method), MultiBindingDeclarationModel.MapDeclarationModel {
    init {
        assert(canRepresent(method))
    }

    override fun <R> accept(visitor: MultiBindingDeclarationModel.Visitor<R>): R {
        return visitor.visitMapDeclaration(this)
    }

    override val keyType: Type?
        get() = method.returnType.typeArguments.firstOrNull()

    override val valueType: NodeModel?
        get() = method.returnType.typeArguments.getOrNull(1)?.let { type ->
            NodeModelImpl(
                type = type,
                forQualifier = method,
            )
        }

    override fun validate(validator: Validator) {
        super.validate(validator)
        if (method.returnType.typeArguments.size != 2) {
            validator.reportError(Strings.Errors.invalidMultiBindingDeclaration()) {
                addNote(Strings.Notes.invalidMultiBindingAdvice())
            }
        }
    }

    override fun toString(childContext: MayBeInvalid?): CharSequence {
        return modelRepresentation(modelClassName = "multibinding declaration (map)") {
            append(method)
        }
    }

    override fun hashCode() = 31 * keyType.hashCode() + valueType.hashCode()
    override fun equals(other: Any?) = this === other || (other is MapDeclarationImpl &&
            keyType == other.keyType && valueType == other.valueType)

    companion object {
        fun canRepresent(method: FunctionLangModel): Boolean {
            return method.returnType.declaration.qualifiedName == Names.Map
        }
    }
}

internal class InvalidDeclarationImpl(
    override val invalidMethod: FunctionLangModel,
) : MultiBindingDeclarationModel.InvalidDeclarationModel {
    override fun <R> accept(visitor: MultiBindingDeclarationModel.Visitor<R>): R {
        return visitor.visitInvalid(this)
    }

    override fun hashCode(): Int = invalidMethod.hashCode()
    override fun equals(other: Any?) = this === other || (other is InvalidDeclarationImpl &&
            invalidMethod == other.invalidMethod)

    override fun validate(validator: Validator) {
        validator.reportError(Strings.Errors.invalidMultiBindingDeclaration()) {
            addNote(Strings.Notes.invalidMultiBindingAdvice())
        }
    }

    override fun toString(childContext: MayBeInvalid?): CharSequence {
        return modelRepresentation(modelClassName = "multibinding declaration") {
            color = TextColor.Red
            append("invalid `").append(invalidMethod).append('`')
        }
    }
}