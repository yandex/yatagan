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
import com.yandex.yatagan.core.model.ComponentFactoryModel
import com.yandex.yatagan.core.model.ComponentModel
import com.yandex.yatagan.core.model.ComponentModel.EntryPoint
import com.yandex.yatagan.core.model.ConditionalHoldingModel
import com.yandex.yatagan.core.model.HasNodeModel
import com.yandex.yatagan.core.model.MembersInjectorModel
import com.yandex.yatagan.core.model.ModuleModel
import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.core.model.ScopeModel
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
import com.yandex.yatagan.validation.format.appendChildContextReference
import com.yandex.yatagan.validation.format.modelRepresentation
import com.yandex.yatagan.validation.format.reportError

internal class ComponentModelImpl private constructor(
    private val declaration: TypeDeclaration,
) : ComponentModel, ConditionalHoldingModel {
    private val impl = declaration.getAnnotation(BuiltinAnnotation.Component)

    private val conditionalsModel by lazy {
        ConditionalHoldingModelImpl(declaration.getAnnotations(BuiltinAnnotation.Conditional))
    }

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

    override val entryPoints: Set<EntryPoint> by lazy {
        class EntryPointImpl(
            override val getter: Method,
            override val dependency: NodeDependency,
        ) : EntryPoint {
            override fun validate(validator: Validator) {
                validator.child(dependency.node)
            }

            override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
                modelClassName = "entry-point",
                representation = {
                    append("${getter.name}()")
                    if (childContext == dependency.node) {
                        append(": ")
                        appendChildContextReference(reference = getter.returnType)
                    }
                },
            )
        }

        declaration.methods.filter {
            it.isAbstract && it.parameters.none()
        }.map { method ->
            EntryPointImpl(
                dependency = NodeDependency(
                    type = method.returnType,
                    forQualifier = method,
                ),
                getter = method,
            )
        }.toSet()
    }

    override val memberInjectors: Set<MembersInjectorModel> by lazy {
        declaration.methods.filter {
            MembersInjectorModelImpl.canRepresent(it)
        }.map { method ->
            MembersInjectorModelImpl(
                injector = method,
            )
        }.toSet()
    }

    override val factory: ComponentFactoryModel? by lazy {
        declaration.nestedClasses
            .find { ComponentFactoryModelImpl.canRepresent(it) }?.let {
                ComponentFactoryModelImpl(factoryDeclaration = it)
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
        if (declaration.nestedClasses.count(ComponentFactoryModelImpl::canRepresent) > 1) {
            validator.reportError(Errors.multipleCreators()) {
                declaration.nestedClasses.filter(ComponentFactoryModelImpl::canRepresent).forEach {
                    addNote(Strings.Notes.conflictingCreator(it))
                }
            }
        }

        factory?.let(validator::child)

        for (method in declaration.methods) {
            if (!method.isAbstract) continue
            if (method.parameters.count() > 1) {
                validator.reportError(Errors.unknownMethodInComponent(method = method))
            }
        }

        if (impl == null) {
            validator.reportError(Errors.nonComponent())
        }

        if (declaration.kind != TypeDeclarationKind.Interface) {
            validator.reportError(Errors.nonInterfaceComponent())
        }

        if (factory == null) {
            if (dependencies.isNotEmpty()) {
                validator.reportError(Errors.missingCreatorForDependencies())
            }

            if (modules.any { it.requiresInstance && !it.isTriviallyConstructable }) {
                validator.reportError(Errors.missingCreatorForModules()) {
                    modules.filter {
                        it.requiresInstance && !it.isTriviallyConstructable
                    }.forEach { module ->
                        addNote(Strings.Notes.missingModuleInstance(module))
                    }
                }
            }
        }
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = if (isRoot) "root-component" else "component",
        representation = declaration,
    )

    companion object Factory : ObjectCache<TypeDeclaration, ComponentModelImpl>() {
        operator fun invoke(key: TypeDeclaration) = createCached(key, ::ComponentModelImpl)

        fun canRepresent(declaration: TypeDeclaration): Boolean {
            return declaration.getAnnotation(BuiltinAnnotation.Component) != null
        }
    }
}
