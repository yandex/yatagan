@file:OptIn(ConditionsApi::class, VariantApi::class)

package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.AllConditions
import com.yandex.daggerlite.AnyCondition
import com.yandex.daggerlite.AnyConditions
import com.yandex.daggerlite.Assisted
import com.yandex.daggerlite.Component
import com.yandex.daggerlite.ComponentFlavor
import com.yandex.daggerlite.Condition
import com.yandex.daggerlite.Conditional
import com.yandex.daggerlite.Conditionals
import com.yandex.daggerlite.ConditionsApi
import com.yandex.daggerlite.Module
import com.yandex.daggerlite.VariantApi
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.base.ifOrElseNull
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.lang.AnnotatedLangModel
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.AssistedAnnotationLangModel
import com.yandex.daggerlite.core.lang.ComponentAnnotationLangModel
import com.yandex.daggerlite.core.lang.ComponentFlavorAnnotationLangModel
import com.yandex.daggerlite.core.lang.ConditionLangModel
import com.yandex.daggerlite.core.lang.ConditionalAnnotationLangModel
import com.yandex.daggerlite.core.lang.ConstructorLangModel
import com.yandex.daggerlite.core.lang.FieldLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.KotlinObjectKind
import com.yandex.daggerlite.core.lang.ModuleAnnotationLangModel
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.lang.common.ConstructorLangModelBase
import com.yandex.daggerlite.lang.common.ParameterLangModelBase
import com.yandex.daggerlite.lang.common.TypeDeclarationLangModelBase
import java.lang.reflect.Constructor
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import kotlin.LazyThreadSafetyMode.PUBLICATION

internal class RtTypeDeclarationImpl private constructor(
    val type: RtTypeImpl,
) : TypeDeclarationLangModelBase(), AnnotatedLangModel by RtAnnotatedImpl(type.impl.asClass()) {
    private val impl = type.impl.asClass()

    override val isEffectivelyPublic: Boolean
        get() = impl.isPublic

    override val isInterface: Boolean
        get() = impl.isInterface

    override val isAbstract: Boolean
        get() = Modifier.isAbstract(impl.modifiers)

    override val qualifiedName: String
        get() = impl.canonicalName

    override val kotlinObjectKind: KotlinObjectKind? by lazy {
        ifOrElseNull(impl.isFromKotlin()) {
            when {
                impl.simpleName == "Companion" && impl.enclosingClass?.declaredFields?.any {
                    it.isPublicStaticFinal && it.name == "Companion" && it.type == impl
                } == true -> KotlinObjectKind.Companion
                impl.declaredFields.any {
                    it.isPublicStaticFinal && it.name == "INSTANCE" && it.type == impl
                } -> KotlinObjectKind.Object
                else -> null
            }
        }
    }

    override val enclosingType: TypeDeclarationLangModel?
        get() = impl.enclosingClass?.let { Factory(RtTypeImpl(it)) }

    override val implementedInterfaces: Sequence<TypeLangModel>
        get() = TODO("Not yet implemented")

    override fun asType(): TypeLangModel {
        return type
    }

    override val nestedClasses: Sequence<TypeDeclarationLangModel> by lazy {
        impl.declaredClasses.asSequence()
            .filter { !it.isPrivate }
            .map { Factory(RtTypeImpl(it)) }
            .memoize()
    }

    override val constructors: Sequence<ConstructorLangModel> by lazy {
        impl.declaredConstructors
            .asSequence()
            .filter { !it.isPrivate }
            .sortedWith(ConstructorSignatureComparator)
            .map {
                ConstructorImpl(
                    platformModel = it,
                    constructee = this,
                )
            }.memoize()
    }

    override val functions: Sequence<FunctionLangModel> by lazy {
        impl.getMethodsOverrideAware()
            .asSequence()
            .run {
                when (kotlinObjectKind) {
                    KotlinObjectKind.Companion -> filterNot {
                        // Such methods already have a truly static counterpart so skip them.
                        it.isAnnotationPresent(JvmStatic::class.java)
                    }
                    else -> this
                }
            }
            .map {
                RtFunctionImpl(impl = it, owner = this)
            }.memoize()
    }

    override val fields: Sequence<FieldLangModel> by lazy {
        impl.getAllFields()
            .asSequence()
            .filter { !it.isPrivate }
            .sortedBy { it.name }
            .map {
                RtFieldImpl(
                    impl = it,
                    owner = this,
                )
            }.memoize()
    }

    override val defaultCompanionObjectDeclaration: TypeDeclarationLangModel? by lazy {
        ifOrElseNull(impl.isFromKotlin()) {
            impl.declaredClasses.find {
                it.simpleName == "Companion"
            }?.let { maybeCompanion ->
                ifOrElseNull(impl.declaredFields.any {
                    it.name == "Companion" && it.isPublicStaticFinal
                }) { RtTypeDeclarationImpl(RtTypeImpl(maybeCompanion)) }
            }
        }
    }

    override val platformModel: Class<*>
        get() = impl

    //region Annotations

    override val componentAnnotationIfPresent: ComponentAnnotationLangModel?
        get() = impl.getAnnotation(Component::class.java)?.let { RtComponentAnnotationImpl(it) }
    override val moduleAnnotationIfPresent: ModuleAnnotationLangModel?
        get() = impl.getAnnotation(Module::class.java)?.let { RtModuleAnnotationImpl(it) }
    override val conditions: Sequence<ConditionLangModel>
        get() = buildList {
            for (annotation in impl.declaredAnnotations) when (annotation) {
                is Condition -> add(RtConditionAnnotationImpl(annotation))
                is AnyCondition -> add(RtAnyConditionAnnotationImpl(annotation))
                is AllConditions -> for (contained in annotation.value) add(RtConditionAnnotationImpl(contained))
                is AnyConditions -> for (contained in annotation.value) add(RtAnyConditionAnnotationImpl(contained))
            }
        }.asSequence()
    override val conditionals: Sequence<ConditionalAnnotationLangModel>
        get() = buildList {
            for (annotation in impl.declaredAnnotations) when (annotation) {
                is Conditional -> add(RtConditionalAnnotationImpl(annotation))
                is Conditionals -> for (contained in annotation.value) add(RtConditionalAnnotationImpl(contained))
            }
        }.asSequence()
    override val componentFlavorIfPresent: ComponentFlavorAnnotationLangModel?
        get() = impl.getAnnotation(ComponentFlavor::class.java)?.let { RtComponentFlavorAnnotationImpl(it) }
    //endregion

    private inner class ConstructorImpl(
        override val platformModel: Constructor<*>,
        override val constructee: TypeDeclarationLangModel,
    ) : ConstructorLangModelBase(), AnnotatedLangModel by RtAnnotatedImpl(platformModel) {
        private val parametersAnnotations by lazy { platformModel.parameterAnnotations }
        private val parametersTypes by lazy { platformModel.genericParameterTypes }
        private val parametersNames by lazy { platformModel.parameterNamesCompat() }

        override val isEffectivelyPublic: Boolean
            get() = platformModel.isPublic
        override val parameters: Sequence<ParameterLangModel> by lazy {
            Array(platformModel.getParameterCountCompat(), ::ParameterImpl).asSequence()
        }

        private inner class ParameterImpl(
            private val index: Int,
        ) : ParameterLangModelBase() {
            override val annotations: Sequence<AnnotationLangModel> by lazy {
                parametersAnnotations[index].map { RtAnnotationImpl(it) }.asSequence()
            }

            // region Annotations

            override val assistedAnnotationIfPresent: AssistedAnnotationLangModel?
                get() = parametersAnnotations[index]
                    .find { it.javaAnnotationClass === Assisted::class.java }
                    ?.let { RtAssistedAnnotationImpl(it as Assisted) }

            // endregion

            override val name: String
                get() = parametersNames[index]

            override val type: TypeLangModel by lazy {
                genericsInfo?.let { params ->
                    // No need to use "hierarchy aware" variant, as constructor can't be inherited.
                    RtTypeImpl(parametersTypes[index].resolveGenerics(params))
                } ?: RtTypeImpl(parametersTypes[index])
            }
        }
    }

    internal val typeHierarchy: Sequence<RtTypeDeclarationImpl> by lazy(PUBLICATION) {
        sequence {
            val queue = ArrayList<RtTypeDeclarationImpl>(4)
            queue += this@RtTypeDeclarationImpl
            do {
                val declaration = queue.removeLast()
                yield(declaration)
                declaration.impl.genericSuperclass?.let { superClass ->
                    queue += Factory(superClass.resolveGenerics(declaration.genericsInfo))
                }
                for (superInterface in declaration.impl.genericInterfaces) {
                    queue += Factory(superInterface.resolveGenerics(declaration.genericsInfo))
                }
            } while (queue.isNotEmpty())
        }.memoize()
    }

    internal val genericsInfo: Lazy<Map<TypeVariable<*>, Type>>? = when (val type = type.impl) {
        is ParameterizedType -> lazy {
            buildMap(type.actualTypeArguments.size) {
                val typeParams = impl.typeParameters
                val typeArgs = type.actualTypeArguments
                for (i in typeParams.indices) {
                    put(typeParams[i], typeArgs[i])
                }
            }
        }
        else -> null
    }

    companion object Factory : ObjectCache<RtTypeImpl, RtTypeDeclarationImpl>() {
        operator fun invoke(type: RtTypeImpl) = createCached(type, ::RtTypeDeclarationImpl)
        operator fun invoke(type: Type) = createCached(RtTypeImpl(type), ::RtTypeDeclarationImpl)
    }
}
