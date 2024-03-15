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

@file:OptIn(ConditionsApi::class, VariantApi::class)

package com.yandex.yatagan.lang.rt

import com.yandex.yatagan.AllConditions
import com.yandex.yatagan.AnyCondition
import com.yandex.yatagan.AnyConditions
import com.yandex.yatagan.AssistedFactory
import com.yandex.yatagan.AssistedInject
import com.yandex.yatagan.Component
import com.yandex.yatagan.ComponentFlavor
import com.yandex.yatagan.ComponentVariantDimension
import com.yandex.yatagan.Condition
import com.yandex.yatagan.ConditionExpression
import com.yandex.yatagan.Conditional
import com.yandex.yatagan.Conditionals
import com.yandex.yatagan.ConditionsApi
import com.yandex.yatagan.Module
import com.yandex.yatagan.VariantApi
import com.yandex.yatagan.base.ObjectCache
import com.yandex.yatagan.base.ifOrElseNull
import com.yandex.yatagan.base.memoize
import com.yandex.yatagan.lang.Annotated
import com.yandex.yatagan.lang.AnnotationDeclaration
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.Constructor
import com.yandex.yatagan.lang.Field
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.Parameter
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.TypeDeclarationKind
import com.yandex.yatagan.lang.common.ConstructorBase
import com.yandex.yatagan.lang.common.TypeDeclarationBase
import javax.inject.Inject
import kotlin.LazyThreadSafetyMode.PUBLICATION

internal class RtTypeDeclarationImpl private constructor(
    val type: RtTypeImpl,
) : TypeDeclarationBase(), Annotated by RtAnnotatedImpl(type.impl.asClass()) {
    private val impl = type.impl.asClass()

    override val isEffectivelyPublic: Boolean
        get() = impl.isPublic

    override val isAbstract: Boolean
        get() = impl.isAbstract && !impl.isAnnotation

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

    override val enclosingType: TypeDeclaration?
        get() = impl.enclosingClass?.let { RtTypeImpl(it).declaration }

    override val interfaces: Sequence<Type>
        get() = superTypes
            .mapNotNull {
                ifOrElseNull(it is RtTypeDeclarationImpl && it.impl.isInterface) {
                    it.asType()
                }
            }

    override val superType: Type?
        get() = superTypes
            .firstOrNull { it is RtTypeDeclarationImpl && !it.impl.isInterface }
            ?.takeUnless { it.qualifiedName == "java.lang.Object" }?.asType()

    override fun asType(): Type {
        return type
    }

    override fun asAnnotationDeclaration(): AnnotationDeclaration {
        check(impl.isAnnotation) { "Not an annotation declaration: $kind" }
        return RtAnnotationImpl.AnnotationClassImpl(impl)
    }

    override val nestedClasses: Sequence<TypeDeclaration> by lazy {
        impl.declaredClasses.asSequence()
            .filter { !it.isPrivate }
            .map { RtTypeImpl(it).declaration }
            .memoize()
    }

    override val constructors: Sequence<Constructor> by lazy {
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

    override val methods: Sequence<Method> by lazy {
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
                RtMethodImpl(impl = it, owner = this)
            }.memoize()
    }

    override val fields: Sequence<Field> by lazy {
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

    override val defaultCompanionObjectDeclaration: TypeDeclaration? by lazy {
        ifOrElseNull(impl.isFromKotlin()) {
            impl.declaredClasses.find {
                it.simpleName == "Companion"
            }?.let { maybeCompanion ->
                ifOrElseNull(impl.declaredFields.any {
                    it.name == "Companion" && it.isPublicStaticFinal
                }) { RtTypeImpl(maybeCompanion).declaration }
            }
        }
    }

    override val platformModel: Class<*>
        get() = impl

    override fun <T : BuiltinAnnotation.OnClass> getAnnotation(
        which: BuiltinAnnotation.Target.OnClass<T>
    ): T? {
        val value: BuiltinAnnotation.OnClass? = when (which) {
            BuiltinAnnotation.AssistedFactory -> (which as BuiltinAnnotation.AssistedFactory)
                .takeIf { impl.isAnnotationPresent(AssistedFactory::class.java) }
            BuiltinAnnotation.Module ->
                impl.getDeclaredAnnotation(Module::class.java)?.let { RtModuleAnnotationImpl(it) }
            BuiltinAnnotation.Component ->
                impl.getDeclaredAnnotation(Component::class.java)?.let { RtComponentAnnotationImpl(it) }
            BuiltinAnnotation.ComponentFlavor ->
                impl.getDeclaredAnnotation(ComponentFlavor::class.java)?.let { RtComponentFlavorAnnotationImpl(it) }
            BuiltinAnnotation.ComponentVariantDimension -> (which as BuiltinAnnotation.ComponentVariantDimension)
                .takeIf {
                    impl.isAnnotationPresent(ComponentVariantDimension::class.java)
                }
            BuiltinAnnotation.Component.Builder -> (which as BuiltinAnnotation.Component.Builder)
                .takeIf { impl.isAnnotationPresent(Component.Builder::class.java) }
            BuiltinAnnotation.ConditionExpression ->
                impl.getAnnotation(ConditionExpression::class.java)?.let { RtConditionExpressionAnnotationImpl(it) }
        }
        return which.modelClass.cast(value)
    }

    override fun <T : BuiltinAnnotation.OnClassRepeatable> getAnnotations(
        which: BuiltinAnnotation.Target.OnClassRepeatable<T>
    ): List<T> {
        return when (which) {
            BuiltinAnnotation.ConditionFamily -> buildList {
                for (annotation in impl.declaredAnnotations) when (annotation) {
                    is Condition -> add(which.modelClass.cast(RtConditionAnnotationImpl(annotation)))
                    is AnyCondition -> add(which.modelClass.cast(RtAnyConditionAnnotationImpl(annotation)))
                    is AllConditions -> for (contained in annotation.value)
                        add(which.modelClass.cast(RtConditionAnnotationImpl(contained)))
                    is AnyConditions -> for (contained in annotation.value)
                        add(which.modelClass.cast(RtAnyConditionAnnotationImpl(contained)))
                }
            }
            BuiltinAnnotation.Conditional -> buildList {
                for (annotation in impl.declaredAnnotations) when (annotation) {
                    is Conditional -> add(which.modelClass.cast(RtConditionalAnnotationImpl(annotation)))
                    is Conditionals -> for (contained in annotation.value)
                        add(which.modelClass.cast(RtConditionalAnnotationImpl(contained)))
                }
            }
        }
    }

    private inner class ConstructorImpl(
        override val platformModel: ReflectConstructor,
        override val constructee: TypeDeclaration,
    ) : ConstructorBase(), Annotated by RtAnnotatedImpl(platformModel) {
        private val parametersAnnotations by lazy { platformModel.parameterAnnotations }
        private val parametersTypes by lazy { platformModel.genericParameterTypes }
        private val parametersNames by lazy { platformModel.parameterNamesCompat() }

        override val isEffectivelyPublic: Boolean
            get() = platformModel.isPublic

        override val parameters: Sequence<Parameter> by lazy {
            Array(platformModel.getParameterCountCompat(), ::ParameterImpl).asSequence()
        }

        override fun <T : BuiltinAnnotation.OnConstructor> getAnnotation(
            which: BuiltinAnnotation.Target.OnConstructor<T>
        ): T? {
            val value: BuiltinAnnotation.OnConstructor? = when(which) {
                BuiltinAnnotation.AssistedInject -> (which as BuiltinAnnotation.AssistedInject)
                    .takeIf { platformModel.isAnnotationPresent(AssistedInject::class.java) }
                BuiltinAnnotation.Inject -> (which as BuiltinAnnotation.Inject)
                    .takeIf { platformModel.isAnnotationPresent(Inject::class.java) }
            }
            return which.modelClass.cast(value)
        }

        private inner class ParameterImpl(
            private val index: Int,
        ) : RtParameterBase() {
            override val parameterAnnotations: Array<kotlin.Annotation>
                get() = parametersAnnotations[index]

            override val name: String
                get() = parametersNames[index]

            override val type: Type by lazy {
                genericsInfo?.let { params ->
                    // No need to use "hierarchy aware" variant, as constructor can't be inherited.
                    RtTypeImpl(parametersTypes[index].resolveGenerics(params))
                } ?: RtTypeImpl(parametersTypes[index])
            }
        }
    }

    internal val superTypes: Sequence<TypeDeclaration> by lazy(PUBLICATION) {
        sequence {
            impl.genericSuperclass?.let { superClass ->
                yield(RtTypeImpl(superClass.resolveGenerics(genericsInfo)).declaration)
            }
            for (superInterface in impl.genericInterfaces) {
                yield(RtTypeImpl(superInterface.resolveGenerics(genericsInfo)).declaration)
            }
        }.memoize()
    }

    internal val genericsInfo: Lazy<Map<ReflectTypeVariable, ReflectType>>? = when (val type = type.impl) {
        is ReflectParameterizedType -> lazy {
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
    }
}
