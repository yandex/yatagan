package com.yandex.daggerlite.core.model.impl

import com.yandex.daggerlite.base.BiObjectCache
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.model.ConditionModel
import com.yandex.daggerlite.core.model.ConditionScope
import com.yandex.daggerlite.core.model.ConditionalHoldingModel
import com.yandex.daggerlite.lang.AnyConditionAnnotationLangModel
import com.yandex.daggerlite.lang.ConditionAnnotationLangModel
import com.yandex.daggerlite.lang.FieldLangModel
import com.yandex.daggerlite.lang.LangModelFactory
import com.yandex.daggerlite.lang.Member
import com.yandex.daggerlite.lang.Method
import com.yandex.daggerlite.lang.Type
import com.yandex.daggerlite.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.lang.isKotlinObject
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.format.ErrorMessage
import com.yandex.daggerlite.validation.format.Negation
import com.yandex.daggerlite.validation.format.Strings
import com.yandex.daggerlite.validation.format.TextColor
import com.yandex.daggerlite.validation.format.WarningMessage
import com.yandex.daggerlite.validation.format.append
import com.yandex.daggerlite.validation.format.appendRichString
import com.yandex.daggerlite.validation.format.buildRichString
import com.yandex.daggerlite.validation.format.modelRepresentation
import com.yandex.daggerlite.validation.format.reportError
import com.yandex.daggerlite.validation.format.reportWarning
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

    override val type: Type
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
        get() = NodeModelImpl(
            type = payload.root.asType(),
            qualifier = null,
        )

    override val requiresInstance: Boolean
        get() = payload.nonStatic

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
                    override val root: TypeDeclarationLangModel
                        get() = LangModelFactory.errorType.declaration
                    override val path: List<Member> get() = emptyList()
                    override fun validate(validator: Validator) {
                        // Always invalid
                        validator.reportError(Strings.Errors.invalidCondition(expression = condition))
                    }
                    override val nonStatic: Boolean get() = false

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
    val root: TypeDeclarationLangModel
    val path: List<Member>
    val nonStatic: Boolean
}

private object MemberTypeVisitor : Member.Visitor<Type> {
    override fun visitMethod(model: Method) = model.returnType
    override fun visitField(model: FieldLangModel) = model.type
}

private typealias ValidationReport = (Validator) -> Unit

private class LiteralPayloadImpl private constructor(
    override val root: TypeDeclarationLangModel,
    private val pathSource: String,
) : LiteralPayload {
    private var validationReport: ValidationReport? = null
    private var _nonStatic = false

    override val nonStatic: Boolean
        get() {
            path  // Ensure path is parsed
            return _nonStatic
        }

    override fun validate(validator: Validator) {
        path  // Ensure path is parsed
        validationReport?.invoke(validator)
    }

    override fun toString(childContext: MayBeInvalid?) = buildRichString {
        appendRichString {
            color = TextColor.BrightYellow
            append(root)
        }
        append(".$pathSource")
    }

    override val path: List<Member> by lazy {
        buildList {
            var currentType = root.asType()
            var finished = false

            var isFirst = true
            pathSource.split('.').forEach { name ->
                if (finished) {
                    validationReport = SimpleErrorReport(Strings.Errors.invalidConditionNoBoolean())
                    return@forEach
                }

                val currentDeclaration = currentType.declaration
                if (!currentDeclaration.isEffectivelyPublic) {
                    validationReport = SimpleErrorReport(
                        Strings.Errors.invalidAccessForConditionClass(`class` = currentDeclaration))
                    return@buildList
                }

                val member = findAccessor(currentDeclaration, name)
                if (member == null) {
                    validationReport = SimpleErrorReport(
                        Strings.Errors.invalidConditionMissingMember(name = name, type = currentType))
                    return@buildList
                }
                if (isFirst) {
                    if (!member.isStatic) {
                        if (currentType.declaration.isKotlinObject) {
                            // Issue warning
                            validationReport = SimpleWarningReport(Strings.Warnings.nonStaticConditionOnKotlinObject())
                        }
                        _nonStatic = true
                    }
                    isFirst = false
                }
                if (!member.isEffectivelyPublic) {
                    validationReport = SimpleErrorReport(
                        Strings.Errors.invalidAccessForConditionMember(member = member))
                    return@buildList
                }
                add(member)

                val type = member.accept(MemberTypeVisitor)
                if (type.asBoxed().declaration.qualifiedName == "java.lang.Boolean") {
                    finished = true
                } else {
                    currentType = type
                }
            }
            if (!finished) {
                validationReport = CompositeErrorReport(
                    base = validationReport,
                    added = SimpleErrorReport(Strings.Errors.invalidConditionNoBoolean()),
                )
            }
        }
    }

    companion object Factory : BiObjectCache<TypeDeclarationLangModel, String, LiteralPayload>() {
        operator fun invoke(root: TypeDeclarationLangModel, pathSource: String): LiteralPayload {
            return createCached(root, pathSource) {
                LiteralPayloadImpl(root, pathSource)
            }
        }

        private fun findAccessor(type: TypeDeclarationLangModel, name: String): Member? {
            val field = type.fields.find { it.name == name }
            if (field != null) {
                return field
            }

            val method = type.methods.find { method ->
                method.name == name
            }
            if (method != null) {
                return method
            }
            return null
        }

        class SimpleErrorReport(val error: ErrorMessage): ValidationReport {
            override fun invoke(validator: Validator) = validator.reportError(error)
        }

        class SimpleWarningReport(val warning: WarningMessage): ValidationReport {
            override fun invoke(validator: Validator) = validator.reportWarning(warning)
        }

        class CompositeErrorReport(
            val base: ValidationReport?,
            val added: ValidationReport,
        ) : ValidationReport {
            override fun invoke(validator: Validator) {
                base?.invoke(validator)
                added.invoke(validator)
            }
        }
    }
}