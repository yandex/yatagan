package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.core.MultiBindingDeclarationModel
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.format.Strings
import com.yandex.daggerlite.validation.format.TextColor
import com.yandex.daggerlite.validation.format.append
import com.yandex.daggerlite.validation.format.modelRepresentation
import com.yandex.daggerlite.validation.format.reportError

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

internal class ListDeclarationImpl(
    method: FunctionLangModel,
) : MultiBindingDeclarationBase(method), MultiBindingDeclarationModel.ListDeclarationModel {
    init {
        assert(canRepresent(method))
    }

    override val listType: NodeModel?
        get() = method.returnType.typeArguments.firstOrNull()?.let { type ->
            NodeModelImpl(
                type = type,
                forQualifier = method,
            )
        }

    override fun <R> accept(visitor: MultiBindingDeclarationModel.Visitor<R>): R {
        return visitor.visitListDeclaration(this)
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
        return modelRepresentation(modelClassName = "multibinding declaration (list)") {
            append(method)
        }
    }

    override fun hashCode(): Int = listType.hashCode()
    override fun equals(other: Any?) = this === other || (other is ListDeclarationImpl && listType == other.listType)

    companion object {
        fun canRepresent(method: FunctionLangModel): Boolean {
            return method.returnType.declaration.qualifiedName == "java.util.List"
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

    override val keyType: TypeLangModel?
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
            return method.returnType.declaration.qualifiedName == "java.util.Map"
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