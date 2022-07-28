package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.base.BiObjectCache
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.ConditionModel
import com.yandex.daggerlite.core.ConditionScope
import com.yandex.daggerlite.core.ConditionalHoldingModel
import com.yandex.daggerlite.core.lang.AnyConditionAnnotationLangModel
import com.yandex.daggerlite.core.lang.ConditionAnnotationLangModel
import com.yandex.daggerlite.core.lang.FieldLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.LangModelFactory
import com.yandex.daggerlite.core.lang.MemberLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.format.ErrorMessage
import com.yandex.daggerlite.validation.format.Negation
import com.yandex.daggerlite.validation.format.Strings
import com.yandex.daggerlite.validation.format.TextColor
import com.yandex.daggerlite.validation.format.append
import com.yandex.daggerlite.validation.format.appendRichString
import com.yandex.daggerlite.validation.format.buildRichString
import com.yandex.daggerlite.validation.format.modelRepresentation
import com.yandex.daggerlite.validation.format.reportError
import com.yandex.daggerlite.validation.format.toString

internal class FeatureModelImpl private constructor(
    private val impl: TypeDeclarationLangModel,
) : ConditionalHoldingModel.FeatureModel {
    override val conditionScope: ConditionScope by lazy {
        ConditionScope(impl.conditions.map { conditionModel ->
            when (conditionModel) {
                is AnyConditionAnnotationLangModel ->
                    conditionModel.conditions.map { ConditionLiteralImpl(it) }.toSet()

                is ConditionAnnotationLangModel ->
                    setOf(ConditionLiteralImpl(conditionModel))
            }
        }.toSet())
    }

    override fun validate(validator: Validator) {
        if (impl.conditions.none()) {
            // TODO: Forbid Never-scope/Always-scope.
            validator.reportError(Strings.Errors.noConditionsOnFeature())
        }
        for (literal in conditionScope.expression.asSequence().flatten().toSet()) {
            validator.child(literal)
        }
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "feature",
        representation = {
            append(impl)
            append(' ')
            when (conditionScope) {
                ConditionScope.Unscoped -> appendRichString {
                    color = TextColor.Red
                    append("<no-conditions-declared>")
                }
                ConditionScope.NeverScoped -> appendRichString {
                    color = TextColor.Red
                    append("<illegal-never>")
                }
                else -> append(conditionScope.toString(childContext = childContext))
            }
        },
    )

    override val type: TypeLangModel
        get() = impl.asType()

    companion object Factory : ObjectCache<TypeDeclarationLangModel, FeatureModelImpl>() {
        operator fun invoke(impl: TypeDeclarationLangModel) = createCached(impl, ::FeatureModelImpl)
    }
}

private class ConditionLiteralImpl private constructor(
    override val negated: Boolean,
    private val payload: LiteralPayload,
) : ConditionModel {

    override fun not(): ConditionModel = Factory(
        negated = !negated,
        payload = payload,
    )

    override val path
        get() = payload.path

    override val root
        get() = payload.path.firstOrNull()?.owner ?: LangModelFactory.errorType.declaration

    override fun validate(validator: Validator) {
        validator.inline(payload)
    }

    override fun toString(childContext: MayBeInvalid?) = buildRichString {
        if (negated) append(Negation)
        append(payload)
    }

    companion object Factory : BiObjectCache<Boolean, LiteralPayload, ConditionLiteralImpl>() {
        operator fun invoke(model: ConditionAnnotationLangModel): ConditionModel {
            val condition = model.condition
            return ConditionRegex.matchEntire(condition)?.let { matched ->
                val (negate, names) = matched.destructured
                this(
                    negated = negate.isNotEmpty(),
                    payload = LiteralPayloadImpl(model.target.declaration, names),
                )
            } ?: this(
                negated = false,
                payload = object : LiteralPayload {
                    override val path: List<MemberLangModel> get() = emptyList()
                    override fun validate(validator: Validator) {
                        // Always invalid
                        validator.reportError(Strings.Errors.invalidCondition(expression = condition))
                    }

                    override fun toString(childContext: MayBeInvalid?) = buildRichString {
                        color = TextColor.Red
                        append("<invalid-condition>")
                    }
                }
            )
        }

        private operator fun invoke(
            negated: Boolean,
            payload: LiteralPayload,
        ) = createCached(negated, payload) {
            ConditionLiteralImpl(negated, payload)
        }

        private val ConditionRegex = "^(!?)((?:[A-Za-z][A-Za-z0-9_]*\\.)*[A-Za-z][A-Za-z0-9_]*)\$".toRegex()
    }
}

private interface LiteralPayload : MayBeInvalid {
    val path: List<MemberLangModel>
}

private object MemberTypeVisitor : MemberLangModel.Visitor<TypeLangModel> {
    override fun visitFunction(model: FunctionLangModel) = model.returnType
    override fun visitField(model: FieldLangModel) = model.type
}

private class LiteralPayloadImpl private constructor(
    private val root: TypeDeclarationLangModel,
    private val pathSource: String,
) : LiteralPayload {
    private var pathParsingError: ErrorMessage? = null

    override fun validate(validator: Validator) {
        path  // Ensure path is parsed
        pathParsingError?.let(validator::reportError)
    }

    override fun toString(childContext: MayBeInvalid?) = buildRichString {
        appendRichString {
            color = TextColor.BrightYellow
            append(root)
        }
        append(".$pathSource")
    }

    override val path: List<MemberLangModel> by lazy {
        buildList {
            var currentType = root.asType()
            var finished = false

            var isFirst = true
            pathSource.split('.').forEach { name ->
                if (finished) {
                    pathParsingError = Strings.Errors.invalidConditionNoBoolean()
                    return@forEach
                }

                val currentDeclaration = currentType.declaration
                if (!currentDeclaration.isEffectivelyPublic) {
                    pathParsingError = Strings.Errors.invalidAccessForConditionClass(`class` = currentDeclaration)
                    return@buildList
                }

                val member = findAccessor(currentDeclaration, name)
                if (member == null) {
                    pathParsingError = Strings.Errors.invalidConditionMissingMember(name = name, type = currentType)
                    return@buildList
                }
                if (isFirst) {
                    if (!member.isStatic) {
                        pathParsingError = Strings.Errors.invalidNonStaticMember(name = name, type = currentType)
                        return@buildList
                    }
                    isFirst = false
                }
                if (!member.isEffectivelyPublic) {
                    pathParsingError = Strings.Errors.invalidAccessForConditionMember(member = member)
                    return@buildList
                }
                add(member)

                val type = member.accept(MemberTypeVisitor)
                if (type.isBoolean) {
                    finished = true
                } else {
                    currentType = type
                }
            }
            if (!finished && pathParsingError == null) {
                pathParsingError = Strings.Errors.invalidConditionNoBoolean()
            }
        }
    }

    companion object Factory : BiObjectCache<TypeDeclarationLangModel, String, LiteralPayload>() {
        operator fun invoke(root: TypeDeclarationLangModel, pathSource: String): LiteralPayload {
            return createCached(root, pathSource) {
                LiteralPayloadImpl(root, pathSource)
            }
        }

        private fun findAccessor(type: TypeDeclarationLangModel, name: String): MemberLangModel? {
            val field = type.fields.find { it.name == name }
            if (field != null) {
                return field
            }

            val method = type.functions.find { function ->
                function.name == name
            }
            if (method != null) {
                return method
            }
            return null
        }
    }
}