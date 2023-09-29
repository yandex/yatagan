/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ConditionsApi::class, VariantApi::class)

package com.yandex.yatagan.lang.compiled

import com.yandex.yatagan.AllConditions
import com.yandex.yatagan.AnyCondition
import com.yandex.yatagan.AnyConditions
import com.yandex.yatagan.AssistedFactory
import com.yandex.yatagan.Component
import com.yandex.yatagan.ComponentFlavor
import com.yandex.yatagan.ComponentVariantDimension
import com.yandex.yatagan.Condition
import com.yandex.yatagan.ConditionExpression
import com.yandex.yatagan.Conditional
import com.yandex.yatagan.Conditionals
import com.yandex.yatagan.ConditionsApi
import com.yandex.yatagan.Module
import com.yandex.yatagan.VariantApi
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.common.TypeDeclarationBase

/**
 * [TypeDeclaration] specialized for compile time implementations.
 */
abstract class CtTypeDeclarationBase : TypeDeclarationBase() {
    abstract override val annotations: Sequence<CtAnnotationBase>

    override fun <T : BuiltinAnnotation.OnClass> getAnnotation(
        which: BuiltinAnnotation.Target.OnClass<T>
    ): T? {
        val value: BuiltinAnnotation.OnClass? = when (which) {
            BuiltinAnnotation.AssistedFactory -> (which as BuiltinAnnotation.AssistedFactory).takeIf {
                annotations.any { it.hasType<AssistedFactory>() }
            }
            BuiltinAnnotation.Module ->
                annotations.find { it.hasType<Module>() }?.let(::CtModuleAnnotationImpl)
            BuiltinAnnotation.Component ->
                annotations.find { it.hasType<Component>() }?.let(::CtComponentAnnotationImpl)
            BuiltinAnnotation.ComponentFlavor ->
                annotations.find { it.hasType<ComponentFlavor>() }?.let { CtComponentFlavorAnnotationImpl(it) }
            BuiltinAnnotation.ComponentVariantDimension -> (which as BuiltinAnnotation.ComponentVariantDimension).takeIf {
                annotations.any { it.hasType<ComponentVariantDimension>() }
            }
            BuiltinAnnotation.Component.Builder -> (which as BuiltinAnnotation.Component.Builder).takeIf {
                annotations.any { it.hasType<Component.Builder>() }
            }
            BuiltinAnnotation.ConditionExpression ->
                annotations.find { it.hasType<ConditionExpression>() }?.let { CtConditionExpressionAnnotationImpl(it) }
        }
        return which.modelClass.cast(value)
    }

    override fun <T : BuiltinAnnotation.OnClassRepeatable> getAnnotations(
        which: BuiltinAnnotation.Target.OnClassRepeatable<T>
    ): List<T> {
        return when (which) {
            BuiltinAnnotation.ConditionFamily -> buildList {
                for (annotation in annotations) {
                    when {
                        annotation.hasType<Condition>() ->
                            add(which.modelClass.cast(CtConditionAnnotationImpl(annotation)))
                        annotation.hasType<AnyCondition>() ->
                            add(which.modelClass.cast(CtAnyConditionAnnotationImpl(annotation)))
                        annotation.hasType<AllConditions>() -> for (contained in annotation.getAnnotations("value"))
                            add(which.modelClass.cast(CtConditionAnnotationImpl(contained)))
                        annotation.hasType<AnyConditions>() -> for (contained in annotation.getAnnotations("value"))
                            add(which.modelClass.cast(CtAnyConditionAnnotationImpl(contained)))
                    }
                }
            }
            BuiltinAnnotation.Conditional -> buildList {
                for (annotation in annotations) {
                    when {
                        annotation.hasType<Conditional>() ->
                            add(which.modelClass.cast(CtConditionalAnnotationImpl(annotation)))
                        annotation.hasType<Conditionals>() -> for (contained in annotation.getAnnotations("value"))
                            add(which.modelClass.cast(CtConditionalAnnotationImpl(contained)))
                    }
                }
            }
        }
    }
}
