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
import com.yandex.daggerlite.lang.AnnotatedLangModel
import com.yandex.daggerlite.lang.AnnotationLangModel
import com.yandex.daggerlite.lang.AssistedAnnotationLangModel
import com.yandex.daggerlite.lang.ComponentAnnotationLangModel
import com.yandex.daggerlite.lang.ComponentFlavorAnnotationLangModel
import com.yandex.daggerlite.lang.ConditionLangModel
import com.yandex.daggerlite.lang.ConditionalAnnotationLangModel
import com.yandex.daggerlite.lang.ConstructorLangModel
import com.yandex.daggerlite.lang.FieldLangModel
import com.yandex.daggerlite.lang.FunctionLangModel
import com.yandex.daggerlite.lang.ModuleAnnotationLangModel
import com.yandex.daggerlite.lang.ParameterLangModel
import com.yandex.daggerlite.lang.TypeDeclarationKind
import com.yandex.daggerlite.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.lang.TypeLangModel
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

    override val isAbstract: Boolean
        get() = Modifier.isAbstract(impl.modifiers) && !impl.isAnnotation

    override val qualifiedName: String
        get() = impl.canonicalName

    override val kind: TypeDeclarationKind by lazy(PUBLICATION) {
        when {
            impl.isAnnotation -> TypeDeclarationKind.Annotation
            impl.isInterface -> TypeDeclarationKind.Interface
            impl.isEnum -> TypeDeclarationKind.Enum
            impl.isPrimitive || impl.isArray -> TypeDeclarationKind.None
            impl.simpleName == "Companion" && impl.enclosingClass?.declaredFields?.any {
                it.isPublicStaticFinal && it.name == "Companion" && it.type == impl
            } == true -> TypeDeclarationKind.KotlinCompanion

            impl.declaredFields.any {
                it.isPublicStaticFinal && it.name == "INSTANCE" && it.type == impl
            } -> TypeDeclarationKind.KotlinObject

            else -> TypeDeclarationKind.Class
        }
    }

    override val enclosingType: TypeDeclarationLangModel?
        get() = impl.enclosingClass?.let { Factory(RtTypeImpl(it)) }

    override val interfaces: Sequence<TypeLangModel>
        get() = superTypes
            .filter { it.impl.isInterface }
            .map { it.type }

    override val superType: TypeLangModel?
        get() = superTypes.firstOrNull { !it.impl.isInterface }
            ?.takeUnless { it.qualifiedName == "java.lang.Object" }?.type

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
                when (kind) {
                    TypeDeclarationKind.KotlinCompanion -> filterNot {
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

    internal val superTypes: Sequence<RtTypeDeclarationImpl> by lazy(PUBLICATION) {
        sequence {
            impl.genericSuperclass?.let { superClass ->
                yield(Factory(superClass.resolveGenerics(genericsInfo)))
            }
            for (superInterface in impl.genericInterfaces) {
                yield(Factory(superInterface.resolveGenerics(genericsInfo)))
            }
        }.memoize()
    }

    internal val genericsInfo: Lazy<Map<TypeVariable<*>, Type>>? = when (val type = type.impl) {
        is ParameterizedType -> lazy {
            val typeArgs = type.actualTypeArguments
            buildMap(typeArgs.size) {
                val typeParams = impl.typeParameters
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
