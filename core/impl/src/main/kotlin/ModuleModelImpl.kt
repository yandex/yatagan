package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.Binds
import com.yandex.daggerlite.Provides
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.BaseBinding
import com.yandex.daggerlite.core.BindingGraph
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.ConditionScope
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.core.lang.hasType
import kotlin.LazyThreadSafetyMode.NONE

internal class ModuleModelImpl private constructor(
    private val declaration: TypeDeclarationLangModel,
) : ModuleModel {
    init {
        require(canRepresent(declaration))
    }

    private val impl = declaration.moduleAnnotationIfPresent!!

    override val type: TypeLangModel
        get() = declaration.asType()

    override val includes: Collection<ModuleModel> by lazy(NONE) {
        impl.includes.map(TypeLangModel::declaration).map(Factory::invoke).toSet()
    }

    override val subcomponents: Collection<ComponentModel> by lazy(NONE) {
        impl.subcomponents.map(TypeLangModel::declaration).map { ComponentModelImpl(it) }.toSet()
    }

    override fun bindings(forGraph: BindingGraph): Sequence<BaseBinding> = sequence {
        val mayRequireInstance = !declaration.isAbstract && !declaration.isKotlinObject

        for (method in declaration.allPublicFunctions) {
            val target by lazy {
                NodeModelImpl(
                    type = method.returnType,
                    forQualifier = method,
                )
            }
            for (annotation in method.annotations) {
                when {
                    annotation.hasType<Binds>() -> when (method.parameters.count()) {
                        0 -> throw IllegalStateException("@Binds with no arguments")
                        1 -> AliasBindingImpl(
                            owner = forGraph,
                            target = target,
                            source = NodeModelImpl(
                                type = method.parameters.single().type,
                                forQualifier = method.parameters.single(),
                            ),
                        )
                        else -> AlternativesBindingImpl(
                            owner = forGraph,
                            target = target,
                            alternatives = method.parameters.map { parameter ->
                                NodeModelImpl(type = parameter.type, forQualifier = parameter)
                            }.toList(),
                            scope = method.annotations.find(AnnotationLangModel::isScope),
                        )
                    }
                    annotation.hasType<Provides>() -> ProvisionBindingImpl(
                        owner = forGraph,
                        target = target,
                        provider = method,
                        scope = method.annotations.find(AnnotationLangModel::isScope),
                        requiredModuleInstance = this@ModuleModelImpl.takeIf {
                            mayRequireInstance && !method.isStatic && !method.isFromCompanionObject
                        },
                        params = method.parameters.map { param ->
                            nodeModelDependency(type = param.type, forQualifier = param)
                        }.toList(),
                        conditionScope = ConditionScope.Unscoped, // TODO(jeffset): support "@ProvidesFeatureScoped"
                    )
                    else -> null
                }?.let { yield(it) }
            }
        }
    }

    companion object Factory : ObjectCache<TypeDeclarationLangModel, ModuleModelImpl>() {
        operator fun invoke(key: TypeDeclarationLangModel) = createCached(key, ::ModuleModelImpl)

        fun canRepresent(declaration: TypeDeclarationLangModel): Boolean {
            return declaration.moduleAnnotationIfPresent != null
        }
    }
}