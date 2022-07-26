package com.yandex.daggerlite.core

import com.yandex.daggerlite.core.lang.MemberLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.validation.MayBeInvalid

/**
 * Represents a parsed runtime condition model.
 */
interface ConditionModel : ConditionExpression.Literal, MayBeInvalid {
    /**
     * A class/object/companion that contains the first member from [path].
     */
    val root: TypeDeclarationLangModel

    /**
     * A chain of members, that, if evaluated sequentially on the result of each other,  lead to boolean value.
     * The first member if evaluated on [root].
     */
    val path: List<MemberLangModel>

    override fun not(): ConditionModel
}