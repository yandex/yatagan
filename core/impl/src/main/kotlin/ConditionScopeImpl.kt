package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.core.ConditionScope
import com.yandex.daggerlite.core.ConditionScope.Literal
import com.yandex.daggerlite.core.lang.AnyConditionAnnotationLangModel
import com.yandex.daggerlite.core.lang.ConditionAnnotationLangModel
import com.yandex.daggerlite.core.lang.ConditionLangModel
import com.yandex.daggerlite.core.lang.FieldLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.MemberLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel

internal val ConditionScope.Companion.Unscoped get() = ConditionScopeImpl.Unscoped
internal val ConditionScope.Companion.NeverScoped get() = ConditionScopeImpl.NeverScoped

internal fun ConditionScope(conditionModels: Sequence<ConditionLangModel>): ConditionScope {
    return ConditionScopeImpl(conditionModels.map { conditionModel ->
        when (conditionModel) {
            is AnyConditionAnnotationLangModel ->
                conditionModel.conditions.map { ConditionLiteralImpl(it) }.toSet()
            is ConditionAnnotationLangModel ->
                setOf(ConditionLiteralImpl(conditionModel))
        }
    }.toSet())
}

private class ConditionScopeImpl(
    override val expression: Set<Set<Literal>>,
) : ConditionScope {
    override fun contains(another: ConditionScope): Boolean {
        TODO("Required in validation step - implement later")
    }

    override fun and(rhs: ConditionScope): ConditionScope {
        if (this === Unscoped) return rhs
        if (rhs === Unscoped) return this
        return ConditionScopeImpl(expression + rhs.expression)
    }

    override fun or(rhs: ConditionScope): ConditionScope {
        if (this === NeverScoped) return rhs
        if (rhs === NeverScoped) return this
        return ConditionScopeImpl(buildSet {
            for (p in expression) for (q in rhs.expression) {
                add(p + q)
            }
        })
    }

    override fun not(): ConditionScope {
        fun impl(clause: Set<Literal>, rest: Set<Set<Literal>>): Set<Set<Literal>> {
            return clause.fold(emptySet()) { acc, literal ->
                acc + if (rest.isEmpty()) {
                    setOf(setOf(literal))
                } else {
                    impl(rest.first(), rest.drop(1).toSet()).map { it + !literal }
                }
            }
        }

        return when (this) {
            Unscoped -> NeverScoped
            NeverScoped -> Unscoped
            else -> ConditionScopeImpl(impl(expression.first(), expression.drop(1).toSet()))
        }
    }

    companion object {
        val Unscoped: ConditionScope = ConditionScopeImpl(emptySet())
        val NeverScoped: ConditionScope = ConditionScopeImpl(setOf(emptySet()))
    }
}


private class ConditionLiteralImpl private constructor(
    override val negated: Boolean,
    override val root: TypeDeclarationLangModel,
    override val path: List<MemberLangModel>,
) : Literal {
    private val precomputedHash = run {
        var result = negated.hashCode()
        result = 31 * result + root.hashCode()
        result = 31 * result + path.hashCode()
        result
    }

    override fun not(): Literal {
        return ConditionLiteralImpl(negated = !negated, root = root, path = path)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConditionLiteralImpl

        if (path != other.path) return false
        if (root != other.root) return false
        if (negated != other.negated) return false

        return true
    }

    override fun hashCode(): Int = precomputedHash

    companion object Factory {
        operator fun invoke(model: ConditionAnnotationLangModel): Literal {
            val matched = ConditionRegex.matchEntire(model.condition)
                ?: throw RuntimeException("invalid condition ${model.condition}")
            val (negate, names) = matched.destructured
            val root = model.target.declaration
            return ConditionLiteralImpl(
                negated = negate.isNotEmpty(),
                root = root,
                path = inflatePath(root.asType(), names.split('.')),
            )
        }

        private fun inflatePath(root: TypeLangModel, path: List<String>): List<MemberLangModel> = buildList {
            var currentType = root
            var finished = false

            path.forEach { rawName ->
                check(!finished) { "Unable to reach boolean result with the given condition" }

                val member = checkNotNull(findAccessor(currentType.declaration, rawName)) {
                    "Can't find accessible '$rawName' member in $currentType"
                }
                add(member)

                val type = when (member) {
                    is FunctionLangModel -> member.returnType
                    is FieldLangModel -> member.type
                }
                if (type.isBoolean) {
                    finished = true
                } else {
                    currentType = type
                }
            }
            check(finished) { "Unable to reach boolean result with the given condition" }
        }

        private fun findAccessor(type: TypeDeclarationLangModel, name: String): MemberLangModel? {
            val field = type.allPublicFields.find { it.name == name }
            if (field != null) {
                return field
            }

            val allMethods = type.allPublicFunctions
            val method = allMethods.find { it.name == name }
            if (method != null) {
                return method
            }

            // TODO: support kotlin properties
            return null
        }

        private val ConditionRegex = "^(!?)((?:[A-Za-z][A-Za-z0-9_]*\\.)*[A-Za-z][A-Za-z0-9_]*)\$".toRegex()
    }
}
