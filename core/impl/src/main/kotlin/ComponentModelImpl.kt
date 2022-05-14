package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.ComponentDependencyModel
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.ComponentModel.EntryPoint
import com.yandex.daggerlite.core.ConditionalHoldingModel
import com.yandex.daggerlite.core.HasNodeModel
import com.yandex.daggerlite.core.MembersInjectorModel
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.Variant
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.impl.Strings
import com.yandex.daggerlite.validation.impl.Strings.Errors
import com.yandex.daggerlite.validation.impl.reportError
import kotlin.LazyThreadSafetyMode.NONE

internal class ComponentModelImpl private constructor(
    private val declaration: TypeDeclarationLangModel,
) : ComponentModel, ConditionalHoldingModel {
    private val impl = declaration.componentAnnotationIfPresent

    private val conditionalsModel by lazy {
        ConditionalHoldingModelImpl(declaration.conditionals)
    }

    override val conditionals
        get() = conditionalsModel.conditionals

    override val type: TypeLangModel
        get() = declaration.asType()

    override fun asNode(): NodeModel = NodeModelImpl(
        type = declaration.asType(),
        forQualifier = null,
    )

    override fun <R> accept(visitor: HasNodeModel.Visitor<R>): R {
        return visitor.visitComponent(this)
    }

    override val scope = declaration.annotations.find(AnnotationLangModel::isScope)

    override val modules: Set<ModuleModel> by lazy(NONE) {
        val allModules = mutableSetOf<ModuleModel>()
        val moduleQueue: ArrayDeque<ModuleModel> = ArrayDeque(
            impl?.modules?.map(TypeLangModel::declaration)?.map { ModuleModelImpl(it) }?.toList() ?: emptyList())
        while (moduleQueue.isNotEmpty()) {
            val module = moduleQueue.removeFirst()
            if (!allModules.add(module)) {
                continue
            }
            moduleQueue += module.includes
        }
        allModules
    }

    override val dependencies: Set<ComponentDependencyModel> by lazy(NONE) {
        impl?.dependencies?.map { ComponentDependencyModelImpl(it) }?.toSet() ?: emptySet()
    }

    override val entryPoints: Set<EntryPoint> by lazy(NONE) {
        class EntryPointImpl(
            override val getter: FunctionLangModel,
            override val dependency: NodeDependency,
        ) : EntryPoint {
            override fun validate(validator: Validator) {
                validator.child(dependency.node)
            }

            override fun toString() = "[entry-point] ${getter.name}"
        }

        declaration.functions.filter {
            it.isAbstract && it.parameters.none()
        }.map { function ->
            EntryPointImpl(
                dependency = NodeDependency(
                    type = function.returnType,
                    forQualifier = function,
                ),
                getter = function,
            )
        }.toSet()
    }

    override val memberInjectors: Set<MembersInjectorModel> by lazy(NONE) {
        declaration.functions.filter {
            MembersInjectorModelImpl.canRepresent(it)
        }.map { function ->
            MembersInjectorModelImpl(
                injector = function,
            )
        }.toSet()
    }

    override val factory: ComponentFactoryModel? by lazy(NONE) {
        declaration.nestedClasses
            .find { ComponentFactoryModelImpl.canRepresent(it) }?.let {
                ComponentFactoryModelImpl(factoryDeclaration = it)
            }
    }

    override val isRoot: Boolean = impl?.isRoot ?: false

    override val requiresSynchronizedAccess: Boolean
        get() = impl?.multiThreadAccess ?: false

    override val variant: Variant by lazy(NONE) {
        VariantImpl(impl?.variant ?: emptySequence())
    }

    override fun validate(validator: Validator) {
        validator.inline(conditionalsModel)

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

        for (function in declaration.functions) {
            if (!function.isAbstract) continue
            if (function.parameters.count() > 1) {
                validator.reportError(Errors.unknownMethodInComponent(method = function))
            }
        }

        if (impl == null) {
            validator.reportError(Errors.nonComponent())
        }

        if (!declaration.isInterface) {
            validator.reportError(Errors.nonInterfaceComponent())
        }

        if (factory == null) {
            if (!isRoot) {
                validator.reportError(Errors.missingCreatorForNonRoot())
            }

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

    override fun toString() = declaration.toString()

    companion object Factory : ObjectCache<TypeDeclarationLangModel, ComponentModelImpl>() {
        operator fun invoke(key: TypeDeclarationLangModel) = createCached(key, ::ComponentModelImpl)

        fun canRepresent(declaration: TypeDeclarationLangModel): Boolean {
            return declaration.componentAnnotationIfPresent != null
        }
    }
}
