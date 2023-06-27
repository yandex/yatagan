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

package com.yandex.yatagan.core.model.impl

import com.yandex.yatagan.base.ObjectCache
import com.yandex.yatagan.core.model.AssistedInjectFactoryModel
import com.yandex.yatagan.core.model.AssistedInjectFactoryModel.Parameter
import com.yandex.yatagan.core.model.ConditionalHoldingModel
import com.yandex.yatagan.core.model.HasNodeModel
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.TypeDeclarationKind
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.modelRepresentation
import com.yandex.yatagan.validation.format.reportError

internal class AssistedInjectFactoryModelImpl private constructor(
    private val impl: TypeDeclaration,
) : AssistedInjectFactoryModel {
    init {
        assert(canRepresent(impl))
    }

    private val assistedInjectClassConditionalModel by lazy {
        assistedInjectType?.let {
            ConditionalHoldingModelImpl(it.declaration.getAnnotations(BuiltinAnnotation.Conditional))
        }
    }

    private val unexpectedFactoryConditionalModel by lazy {
        ConditionalHoldingModelImpl(impl.getAnnotations(BuiltinAnnotation.Conditional))
    }

    override val conditionals: List<ConditionalHoldingModel.ConditionalWithFlavorConstraintsModel>
        get() = assistedInjectClassConditionalModel?.conditionals ?: emptyList()

    override val factoryMethod: Method? by lazy {
        impl.methods.singleOrNull { it.isAbstract }
    }

    private val assistedInjectType: Type? by lazy {
        factoryMethod?.returnType
    }

    override val assistedInjectConstructor by lazy {
        assistedInjectType?.declaration?.constructors?.find {
            it.getAnnotation(BuiltinAnnotation.AssistedInject) != null
        }
    }

    override val assistedConstructorParameters by lazy {
        assistedInjectConstructor?.parameters?.map { parameter ->
            when (val assisted = parameter.getAnnotation(BuiltinAnnotation.Assisted)) {
                null -> Parameter.Injected(NodeDependency(type = parameter.type, forQualifier = parameter))
                else -> Parameter.Assisted(identifier = assisted.value, type = parameter.type)
            }
        }?.toList() ?: emptyList()
    }

    override val assistedFactoryParameters by lazy {
        factoryMethod?.parameters?.map { parameter ->
            Parameter.Assisted(
                identifier = when (val assisted = parameter.getAnnotation(BuiltinAnnotation.Assisted)) {
                    null -> ""
                    else -> assisted.value
                },
                type = parameter.type,
            )
        }?.toList() ?: emptyList()
    }

    override val type: Type
        get() = impl.asType()

    override fun asNode(): NodeModel = NodeModelImpl(
        type = impl.asType(),
        qualifier = null,
    )

    override fun <R> accept(visitor: HasNodeModel.Visitor<R>): R {
        return visitor.visitAssistedInjectFactory(this)
    }

    override fun validate(validator: Validator) {
        validator.child(asNode())
        assistedInjectClassConditionalModel?.let(validator::child)

        if (impl.kind != TypeDeclarationKind.Interface) {
            validator.reportError(Strings.Errors.assistedInjectFactoryNotInterface())
        }

        if (impl.methods.count { it.isAbstract } != 1) {
            validator.reportError(Strings.Errors.assistedInjectFactoryNoMethod())
            return  // All the following errors here will be induced, skip them.
        }

        if (assistedFactoryParameters.size > assistedFactoryParameters.toSet().size) {
            validator.reportError(Strings.Errors.assistedInjectFactoryDuplicateParameters())
        }

        val assistedInjectType = assistedInjectType
        val assistedInjectConstructor = assistedInjectConstructor
        if (assistedInjectType == null || assistedInjectConstructor == null) {
            validator.reportError(Strings.Errors.assistedInjectTypeNoConstructor(assistedInjectType))
            return  // All the following errors here will be induced, skip them.
        }

        if (!assistedInjectType.declaration.isEffectivelyPublic || !assistedInjectConstructor.isEffectivelyPublic) {
            validator.reportError(Strings.Errors.invalidAccessForAssistedInject())
        }


        val allAssistedParameters = assistedConstructorParameters.filterIsInstance<Parameter.Assisted>()
        val allConstructorAssistedParameters: Set<Parameter.Assisted> = allAssistedParameters.toSet()
        if (allAssistedParameters.size > allConstructorAssistedParameters.size) {
            validator.reportError(Strings.Errors.assistedInjectDuplicateParameters())
        }

        val allFactoryAssistedParameters: Set<Parameter.Assisted> = assistedFactoryParameters.toSet()
        if (allConstructorAssistedParameters != allFactoryAssistedParameters) {
            validator.reportError(Strings.Errors.assistedInjectMismatch()) {
                addNote(Strings.Notes.assistedInjectMismatchFromConstructor(params = allConstructorAssistedParameters))
                addNote(Strings.Notes.assistedInjectMismatchFromFactory(params = allFactoryAssistedParameters))
            }
        }

        if (unexpectedFactoryConditionalModel.conditionals.isNotEmpty()) {
            validator.reportError(Strings.Errors.assistedInjectConditionalsOnFactory())
        }
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "assisted-factory",
        representation = impl,
    )

    companion object Factory : ObjectCache<TypeDeclaration, AssistedInjectFactoryModelImpl>() {
        operator fun invoke(declaration: TypeDeclaration): AssistedInjectFactoryModelImpl {
            return createCached(declaration, ::AssistedInjectFactoryModelImpl)
        }

        fun canRepresent(declaration: TypeDeclaration): Boolean {
            return declaration.getAnnotation(BuiltinAnnotation.AssistedFactory) != null
        }
    }
}