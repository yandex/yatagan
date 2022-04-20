package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.AllConditions
import com.yandex.daggerlite.AnyCondition
import com.yandex.daggerlite.AnyConditions
import com.yandex.daggerlite.Component
import com.yandex.daggerlite.ComponentFlavor
import com.yandex.daggerlite.Condition
import com.yandex.daggerlite.Conditional
import com.yandex.daggerlite.Conditionals
import com.yandex.daggerlite.Module
import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.AssistedAnnotationLangModel
import com.yandex.daggerlite.core.lang.ComponentAnnotationLangModel
import com.yandex.daggerlite.core.lang.ComponentFlavorAnnotationLangModel
import com.yandex.daggerlite.core.lang.ConditionLangModel
import com.yandex.daggerlite.core.lang.ConditionalAnnotationLangModel
import com.yandex.daggerlite.core.lang.ConstructorLangModel
import com.yandex.daggerlite.core.lang.FieldLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel.PropertyAccessorKind.Getter
import com.yandex.daggerlite.core.lang.FunctionLangModel.PropertyAccessorKind.Setter
import com.yandex.daggerlite.core.lang.KotlinObjectKind
import com.yandex.daggerlite.core.lang.ModuleAnnotationLangModel
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import kotlinx.metadata.KmClass
import kotlinx.metadata.jvm.getterSignature
import kotlinx.metadata.jvm.setterSignature
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable

internal class RtTypeDeclarationImpl private constructor(
    val type: RtTypeImpl,
) : RtAnnotatedImpl<Class<*>>(type.impl.asClass()), TypeDeclarationLangModel {

    private val kotlinClass: KmClass? by lazy {
        impl.obtainKotlinClassIfApplicable()
    }

    override val isEffectivelyPublic: Boolean
        get() = impl.isPublic

    override val isInterface: Boolean
        get() = impl.isInterface

    override val isAbstract: Boolean
        get() = Modifier.isAbstract(impl.modifiers)

    override val qualifiedName: String
        get() = impl.canonicalName

    override val kotlinObjectKind: KotlinObjectKind?
        get() = kotlinClass?.let {
            when {
                it.isCompanionObject -> KotlinObjectKind.Companion
                it.isObject -> KotlinObjectKind.Object
                else -> null
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
            .toList().asSequence()
    }

    override val constructors: Sequence<ConstructorLangModel> by lazy {
        impl.declaredConstructors
            .asSequence()
            .filter { !it.isPrivate }
            .sortedWith(ExecutableSignatureComparator)
            .map {
                ConstructorImpl(
                    impl = it,
                    constructee = this,
                )
            }.toList().asSequence()
    }

    override val functions: Sequence<FunctionLangModel> by lazy {
        impl.getMethodsOverrideAware()
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
            }.asSequence()
    }

    override val fields: Sequence<FieldLangModel> by lazy {
        impl.declaredFields
            .asSequence()
            .filter { !it.isPrivate }
            .sortedBy { it.name }
            .map {
                RtFieldImpl(
                    impl = it,
                    owner = this,
                )
            }.toList().asSequence()
    }

    override val companionObjectDeclaration: TypeDeclarationLangModel? by lazy {
        kotlinClass?.companionObject?.let { companion ->
            val companionClass = impl.classLoader.loadClass("${impl.canonicalName}$$companion")
            RtTypeDeclarationImpl(RtTypeImpl(companionClass))
        }
    }

    override val platformModel: Class<*>
        get() = impl

    //region Annotations

    override val componentAnnotationIfPresent: ComponentAnnotationLangModel?
        get() = impl.getAnnotation(Component::class.java)?.let { RtComponentAnnotationImpl(it) }
    override val moduleAnnotationIfPresent: ModuleAnnotationLangModel?
        get() = impl.getAnnotation(Module::class.java)?.let { RtModuleAnnotationImpl(it) }
    override val conditions: Sequence<ConditionLangModel> by lazy {
        buildList {
            for (annotation in impl.declaredAnnotations) when (annotation) {
                is Condition -> add(RtConditionAnnotationImpl(annotation))
                is AnyCondition -> add(RtAnyConditionAnnotationImpl(annotation))
                is AllConditions -> for (contained in annotation.value) add(RtConditionAnnotationImpl(contained))
                is AnyConditions -> for (contained in annotation.value) add(RtAnyConditionAnnotationImpl(contained))
            }
        }.asSequence()
    }
    override val conditionals: Sequence<ConditionalAnnotationLangModel> by lazy {
        buildList {
            for (annotation in impl.declaredAnnotations) when (annotation) {
                is Conditional -> add(RtConditionalAnnotationImpl(annotation))
                is Conditionals -> for (contained in annotation.value) add(RtConditionalAnnotationImpl(contained))
            }
        }.asSequence()
    }
    override val componentFlavorIfPresent: ComponentFlavorAnnotationLangModel? by lazy {
        impl.getAnnotation(ComponentFlavor::class.java)?.let { RtComponentFlavorAnnotationImpl(it) }
    }

    //endregion

    override fun toString(): String = type.toString()

    private inner class ConstructorImpl(
        impl: Constructor<*>,
        override val constructee: TypeDeclarationLangModel,
    ) : ConstructorLangModel, RtAnnotatedImpl<Constructor<*>>(impl) {
        private val parametersAnnotations by lazy { impl.parameterAnnotations }
        private val parametersTypes by lazy { impl.genericParameterTypes }
        private val parametersNames by lazy { impl.parameterNamesCompat() }

        override val isEffectivelyPublic: Boolean
            get() = impl.isPublic
        override val parameters: Sequence<ParameterLangModel> by lazy {
            Array(impl.getParameterCountCompat(), ::ParameterImpl).asSequence()
        }
        override val platformModel: Constructor<*> get() = impl

        private inner class ParameterImpl(
            private val index: Int,
        ) : ParameterLangModel {
            override val annotations: Sequence<AnnotationLangModel> by lazy {
                parametersAnnotations[index].map { RtAnnotationImpl(it) }.asSequence()
            }

            override val assistedAnnotationIfPresent: AssistedAnnotationLangModel?
                get() = null

            override val name: String
                get() = parametersNames[index]

            override val type: TypeLangModel by lazy {
                genericsInfo?.let { params ->
                    // No need to use "hierarchy aware" variant, as constructor can't be inherited.
                    RtTypeImpl(parametersTypes[index].resolveGenerics(params))
                } ?: RtTypeImpl(parametersTypes[index])
            }

            override fun toString() = "$name: $type"
        }
    }

    internal val typeHierarchy: Sequence<RtTypeDeclarationImpl> by lazy {
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
        }
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

    private val kotlinPropertySetters by lazy {
        buildMap {
            kotlinClass?.properties?.forEach { kmProperty ->
                kmProperty.setterSignature?.let { put(it, kmProperty) }
                kmProperty.getterSignature?.let { put(it, kmProperty) }
            }
        }
    }

    internal fun findKotlinPropertyAccessorFor(method: Method): RtPropertyAccessorImpl? {
        val signature by lazy(LazyThreadSafetyMode.NONE) {
            jvmSignatureOf(method)
        }
        for (declaration in typeHierarchy) {
            val property = declaration.kotlinPropertySetters[signature] ?: continue
            return RtPropertyAccessorImpl(
                property = property,
                kind = if (property.setterSignature == signature) Setter else Getter,
            )
        }
        return null
    }

    companion object Factory : ObjectCache<RtTypeImpl, RtTypeDeclarationImpl>() {
        operator fun invoke(type: RtTypeImpl) = createCached(type, ::RtTypeDeclarationImpl)
        operator fun invoke(type: Type) = createCached(RtTypeImpl(type), ::RtTypeDeclarationImpl)
    }
}
