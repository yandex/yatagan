/*
 * Copyright 2023 Yandex LLC
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
import com.yandex.yatagan.core.model.ComponentFactoryModel
import com.yandex.yatagan.core.model.ComponentFactoryWithBuilderModel
import com.yandex.yatagan.core.model.ComponentFactoryWithBuilderModel.BuilderInputModel
import com.yandex.yatagan.core.model.ComponentModel
import com.yandex.yatagan.core.model.HasNodeModel
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.LangModelFactory
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.TypeDeclarationKind
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.append
import com.yandex.yatagan.validation.format.appendChildContextReference
import com.yandex.yatagan.validation.format.modelRepresentation
import com.yandex.yatagan.validation.format.reportError
import com.yandex.yatagan.validation.format.reportWarning

internal class ComponentFactoryWithBuilderModelImpl private constructor(
    private val factoryDeclaration: TypeDeclaration,
) : ComponentFactoryModelBase(), ComponentFactoryWithBuilderModel {
    override val createdComponent: ComponentModel by lazy {
        val declaration = factoryDeclaration.enclosingType
            ?: LangModelFactory.createNoType("missing-component-type").declaration
        ComponentModelImpl(declaration)
    }

    override val factoryMethod = factoryDeclaration.methods.find {
        it.isAbstract && it.returnType == createdComponent.type
    }

    override val type: Type
        get() = factoryDeclaration.asType()

    override fun asNode(): NodeModel = NodeModelImpl(
        type = factoryDeclaration.asType(),
        forQualifier = null,
    )

    override fun <R> accept(visitor: HasNodeModel.Visitor<R>): R {
        return visitor.visitComponentFactory(this)
    }

    override val builderInputs: Collection<BuilderInputModel> = factoryDeclaration.methods.filter {
        it.isAbstract && it != factoryMethod && it.parameters.count() == 1
    }.map { method ->
        object : BuilderInputModel {
            override val payload: ComponentFactoryModel.InputPayload by lazy(LazyThreadSafetyMode.PUBLICATION) {
                InputPayload(
                    param = method.parameters.first(),
                    hasBindsInstance = { method.getAnnotation(BuiltinAnnotation.BindsInstance) != null },
                )
            }
            override val name get() = method.name
            override val builderSetter get() = method

            override fun validate(validator: Validator) {
                validator.child(payload)
                if (method.parameters.first().getAnnotation(BuiltinAnnotation.BindsInstance) != null) {
                    validator.reportWarning(Strings.Warnings.ignoredBindsInstance())
                }
                if (!method.returnType.isVoid && !method.returnType.isAssignableFrom(factoryDeclaration.asType())) {
                    validator.reportError(Strings.Errors.invalidBuilderSetterReturn(
                        creatorType = factoryDeclaration.asType(),
                    ))
                }
            }

            override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
                modelClassName = "creator-setter",
                representation = {
                    append("$name(")
                    if (childContext != null) {
                        appendChildContextReference(reference = method.parameters.first())
                    } else {
                        append(method.parameters.first())
                    }
                    append("): ")
                    append(method.returnType)
                }
            )
        }
    }.toList()


    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "component-creator",
        representation = factoryDeclaration,
    )

    override fun validate(validator: Validator) {
        super.validate(validator)

        validator.inline(asNode())

        if (factoryDeclaration.kind != TypeDeclarationKind.Interface) {
            validator.reportError(Strings.Errors.nonInterfaceCreator())
        }

        for (method in factoryDeclaration.methods) {
            if (method == factoryMethod || method.parameters.count() == 1 || !method.isAbstract)
                continue
            validator.reportError(Strings.Errors.unknownMethodInCreator(method = method))
        }
    }

    override fun <R> accept(visitor: ComponentFactoryModel.Visitor<R>): R {
        return visitor.visitWithBuilder(this);
    }

    companion object Factory : ObjectCache<TypeDeclaration, ComponentFactoryWithBuilderModelImpl>() {
        operator fun invoke(
            factoryDeclaration: TypeDeclaration,
        ) = createCached(factoryDeclaration) {
            ComponentFactoryWithBuilderModelImpl(
                factoryDeclaration = it,
            )
        }

        fun canRepresent(declaration: TypeDeclaration): Boolean {
            return declaration.getAnnotation(BuiltinAnnotation.Component.Builder) != null
        }
    }
}