package com.yandex.yatagan.core.model

import com.yandex.yatagan.lang.Member
import com.yandex.yatagan.validation.MayBeInvalid

/**
 * Represents a parsed runtime condition model.
 */
public interface ConditionModel : ConditionExpression.Literal, MayBeInvalid {
    /**
     * A class/object/companion that contains the first member from [path].
     * [NodeModel] type is used to be able to resolve the instance of a graph in case of
     * [non-static condition][requiresInstance].
     */
    public val root: NodeModel

    /**
     * A chain of members, that, if evaluated sequentially on the result of each other,  lead to boolean value.
     * The first member if evaluated on [root].
     */
    public val path: List<Member>

    /**
     * `true` if this condition requires an injectable instance to evaluate (non-static condition).
     * `false` if condition can be evaluated from the static context (plain/static condition).
     */
    public val requiresInstance: Boolean

    override fun not(): ConditionModel
}