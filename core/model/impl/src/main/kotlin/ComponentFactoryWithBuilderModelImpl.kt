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
import com.yandex.yatagan.lang.Method
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
    private val methodParser by lazy { MethodParser() }

    override val createdComponent: ComponentModel by lazy {
        val declaration = factoryDeclaration.enclosingType
            ?: LangModelFactory.createNoType("missing-component-type").declaration
        ComponentModelImpl(declaration)
    }

    override val factoryMethod by lazy {
        methodParser.factoryMethods.maxOrNull()
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

    override val builderInputs: Collection<BuilderInputModel>
        get() = methodParser.builderInputs

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

        for (method in methodParser.unknown) {
            validator.reportError(Strings.Errors.unknownMethodInCreator(method = method))
        }

        if (methodParser.factoryMethods.size > 1) {
            validator.reportError(Strings.Errors.duplicateFactoryMethodsInACreator()) {
                for (method in methodParser.factoryMethods.sorted()) {
                    addNote(Strings.Notes.conflictingFactory(method))
                }
            }
        }
    }

    override fun <R> accept(visitor: ComponentFactoryModel.Visitor<R>): R {
        return visitor.visitWithBuilder(this);
    }

    private inner class MethodParser {
        val factoryMethods = arrayListOf<Method>()
        val builderInputs = arrayListOf<BuilderInputModelImpl>()
        val unknown = arrayListOf<Method>()

        init {
            for (method in factoryDeclaration.methods) {
                when {
                    // Non-abstract method - skip
                    !method.isAbstract -> continue

                    // Factory method
                    method.returnType.isAssignableFrom(createdComponent.type) -> {
                        factoryMethods += method
                    }

                    // Builder setter
                    method.parameters.count() == 1 -> {
                        builderInputs += BuilderInputModelImpl(method)
                    }

                    else -> {
                        unknown += method
                    }
                }
            }
        }
    }

    private inner class BuilderInputModelImpl(private val method: Method) : BuilderInputModel {
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