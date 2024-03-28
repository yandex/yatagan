package com.yandex.yatagan.core.model.impl

import com.yandex.yatagan.lang.Annotated
import com.yandex.yatagan.lang.Annotation
import com.yandex.yatagan.lang.Type

internal fun AndExpressionImpl(
    lhs: BooleanExpressionInternal,
    rhs: BooleanExpressionInternal,
) = with(lhs) { AndExpressionImpl(lhs to rhs) }

internal fun OrExpressionImpl(
    lhs: BooleanExpressionInternal,
    rhs: BooleanExpressionInternal,
) = with(lhs) { OrExpressionImpl(lhs to rhs) }

@Suppress("FunctionName")
internal fun ConditionModelImpl(
    type: Type,
    pathSource: String,
) = with(type) { ConditionModelImpl(type to pathSource) }

internal fun NodeModelImpl(
    type: Type,
    forQualifier: Annotated?,
) : NodeModelImpl = with(type) {
    NodeModelImpl(type to forQualifier?.annotations?.find(Annotation::isQualifier))
}

internal fun NodeModelImpl(
    type: Type,
    qualifier: Annotation? = null,
) : NodeModelImpl = with(type) { NodeModelImpl(type to qualifier) }
