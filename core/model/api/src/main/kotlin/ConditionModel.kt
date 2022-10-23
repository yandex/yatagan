package com.yandex.daggerlite.core.model

import com.yandex.daggerlite.lang.MemberLangModel
import com.yandex.daggerlite.validation.MayBeInvalid

/**
 * Represents a parsed runtime condition model.
 */
interface ConditionModel : ConditionExpression.Literal, MayBeInvalid {
    /**
     * A class/object/companion that contains the first member from [path].
     * [NodeModel] type is used to be able to resolve the instance of a graph in case of
     * [non-static condition][requiresInstance].
     */
    val root: NodeModel

    /**
     * A chain of members, that, if evaluated sequentially on the result of each other,  lead to boolean value.
     * The first member if evaluated on [root].
     */
    val path: List<MemberLangModel>

    /**
     * `true` if this condition requires an injectable instance to evaluate (non-static condition).
     * `false` if condition can be evaluated from the static context (plain/static condition).
     */
    val requiresInstance: Boolean

    override fun not(): ConditionModel
}