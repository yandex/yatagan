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
import com.yandex.yatagan.core.model.ComponentDependencyModel
import com.yandex.yatagan.core.model.ComponentEntryPoint
import com.yandex.yatagan.core.model.ComponentFactoryWithBuilderModel
import com.yandex.yatagan.core.model.ComponentModel
import com.yandex.yatagan.core.model.ConditionalHoldingModel
import com.yandex.yatagan.core.model.HasNodeModel
import com.yandex.yatagan.core.model.MembersInjectorModel
import com.yandex.yatagan.core.model.ModuleModel
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.core.model.ScopeModel
import com.yandex.yatagan.core.model.SubComponentFactoryMethodModel
import com.yandex.yatagan.core.model.Variant
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.TypeDeclarationKind
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.Strings.Errors
import com.yandex.yatagan.validation.format.modelRepresentation
import com.yandex.yatagan.validation.format.reportError

internal class ComponentModelImpl private constructor(
    private val declaration: TypeDeclaration,
) : ComponentModel, ConditionalHoldingModel {
    private val impl = declaration.getAnnotation(BuiltinAnnotation.Component)

    private val conditionalsModel by lazy {
        ConditionalHoldingModelImpl(declaration.getAnnotations(BuiltinAnnotation.Conditional))
    }

    private val parsedMethods: MethodParser by lazy { MethodParser() }

    override val conditionals: List<ConditionalHoldingModel.ConditionalWithFlavorConstraintsModel>
        get() = conditionalsModel.conditionals

    override val type: Type
        get() = declaration.asType()

    override fun asNode(): NodeModel = NodeModelImpl(
        type = declaration.asType(),
        forQualifier = null,
    )

    override fun <R> accept(visitor: HasNodeModel.Visitor<R>): R {
        return visitor.visitComponent(this)
    }

    override val scopes: Set<ScopeModel> by lazy {
        buildScopeModels(declaration)
    }

    override val modules: Set<ModuleModel> by lazy {
        val allModules = mutableSetOf<ModuleModel>()
        val moduleQueue: ArrayDeque<ModuleModel> = ArrayDeque(
            impl?.modules?.map(Type::declaration)?.map { ModuleModelImpl(it) }?.toList() ?: emptyList())
        while (moduleQueue.isNotEmpty()) {
            val module = moduleQueue.removeFirst()
            if (!allModules.add(module)) {
                continue
            }
            moduleQueue += module.includes
        }
        allModules
    }

    override val dependencies: Set<ComponentDependencyModel> by lazy {
        impl?.dependencies?.map { ComponentDependencyModelImpl(it) }?.toSet() ?: emptySet()
    }

    override val entryPoints
        get() = parsedMethods.entryPoints

    override val memberInjectors
        get() = parsedMethods.memberInjectors

    override val subComponentFactoryMethods
        get() = parsedMethods.childComponentFactories

    override val factory: ComponentFactoryWithBuilderModel? by lazy {
        declaration.nestedClasses
            .find { ComponentFactoryWithBuilderModelImpl.canRepresent(it) }?.let {
                ComponentFactoryWithBuilderModelImpl(factoryDeclaration = it)
            }
    }

    override val isRoot: Boolean = impl?.isRoot ?: false

    override val requiresSynchronizedAccess: Boolean
        get() = impl?.multiThreadAccess ?: false

    override val variant: Variant by lazy {
        VariantImpl(impl?.variant ?: emptyList())
    }

    override fun validate(validator: Validator) {
        validator.child(conditionalsModel)

        for (module in modules) {
            validator.child(module)
        }
        for (dependency in dependencies) {
            validator.child(dependency)
        }
        for (entryPoint in entryPoints) {
            validator.child(entryPoint)
        }
        for (memberInjector in memberInjectors) {
            validator.child(memberInjector)
        }
        for (childComponentFactory in subComponentFactoryMethods) {
            validator.child(childComponentFactory)
        }

        subComponentFactoryMethods
            .groupBy { it.createdComponent }
            .forEach { (createdComponent, factories) ->
                if (factories.size > 1) {
                    validator.reportError(Strings.Errors.multipleChildComponentFactoryMethodsForComponent(
                        component = createdComponent,
                    )) {
                        factories.forEach {
                            addNote(Strings.Notes.duplicateChildComponentFactory(factory = it))
                        }
                    }
                }
            }

        if (declaration.nestedClasses.count(ComponentFactoryWithBuilderModelImpl::canRepresent) > 1) {
            validator.reportError(Errors.multipleCreators()) {
                declaration.nestedClasses.filter(ComponentFactoryWithBuilderModelImpl::canRepresent).forEach {
                    addNote(Strings.Notes.conflictingCreator(it))
                }
            }
        }

        factory?.let(validator::child)

        for (method in parsedMethods.unknown) {
            validator.reportError(Errors.unknownMethodInComponent(method = method))
        }

        if (impl == null) {
            validator.reportError(Errors.nonComponent())
        }

        if (declaration.kind != TypeDeclarationKind.Interface) {
            validator.reportError(Errors.nonInterfaceComponent())
        }
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = if (isRoot) "root-component" else "component",
        representation = declaration,
    )

    private inner class MethodParser {
        val entryPoints = arrayListOf<ComponentEntryPoint>()
        val memberInjectors = arrayListOf<MembersInjectorModel>()
        val childComponentFactories = arrayListOf<SubComponentFactoryMethodModel>()
        val unknown = arrayListOf<Method>()

        init {
            for (method in declaration.methods) {
                // Parse with priorities
                when {
                    SubComponentFactoryMethodImpl.canRepresent(method) -> {
                        childComponentFactories += SubComponentFactoryMethodImpl(
                            factoryMethod = method,
                        )
                    }
                    MembersInjectorModelImpl.canRepresent(method) -> {
                        memberInjectors += MembersInjectorModelImpl(
                            injector = method,
                        )
                    }
                    ComponentEntryPointImpl.canRepresent(method) -> {
                        entryPoints += ComponentEntryPointImpl(
                            dependency = NodeDependency(
                                type = method.returnType,
                                forQualifier = method,
                            ),
                            getter = method,
                        )
                    }
                    else -> {
                        if (method.isAbstract) {
                            unknown += method
                        }
                    }
                }
            }
        }
    }

    companion object Factory : ObjectCache<TypeDeclaration, ComponentModelImpl>() {
        operator fun invoke(key: TypeDeclaration) = createCached(key, ::ComponentModelImpl)

        fun canRepresent(declaration: TypeDeclaration): Boolean {
            return declaration.getAnnotation(BuiltinAnnotation.Component) != null
        }
    }
}
