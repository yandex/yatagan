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
import com.yandex.daggerlite.core.lang.ComponentAnnotationLangModel
import com.yandex.daggerlite.core.lang.ComponentFlavorAnnotationLangModel
import com.yandex.daggerlite.core.lang.ConditionLangModel
import com.yandex.daggerlite.core.lang.ConditionalAnnotationLangModel
import com.yandex.daggerlite.core.lang.ConstructorLangModel
import com.yandex.daggerlite.core.lang.FieldLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel.PropertyAccessorKind.Getter
import com.yandex.daggerlite.core.lang.KotlinObjectKind
import com.yandex.daggerlite.core.lang.ModuleAnnotationLangModel
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.staticProperties
import kotlin.reflect.jvm.javaGetter

internal class RtTypeDeclarationImpl private constructor(
        val type: RtTypeImpl,
) : RtAnnotatedImpl<Class<*>>(type.impl.asClass()), TypeDeclarationLangModel {

    private val kotlinClass: KClass<*> by lazy { impl.kotlin }

    override val isInterface: Boolean
        get() = impl.isInterface

    override val isAbstract: Boolean
        get() = Modifier.isAbstract(impl.modifiers)

    override val qualifiedName: String
        get() = impl.canonicalName

    override val kotlinObjectKind: KotlinObjectKind?
        get() = when {
            kotlinClass.isCompanion -> KotlinObjectKind.Companion
            kotlinClass.objectInstance != null -> KotlinObjectKind.Object
            else -> null
        }

    override val enclosingType: TypeDeclarationLangModel?
        get() = impl.enclosingClass?.let { Factory(RtTypeImpl(it)) }

    override val implementedInterfaces: Sequence<TypeLangModel>
        get() = TODO("Not yet implemented")

    override fun asType(): TypeLangModel {
        return type
    }

    override val nestedClasses: Sequence<TypeDeclarationLangModel> by lazy {
        impl.declaredClasses.asSequence().map { Factory(RtTypeImpl(it)) }
    }

    override val constructors: Sequence<ConstructorLangModel> by lazy {
        impl.declaredConstructors.map { constructor ->
            ConstructorImpl(
                impl = constructor,
                constructee = this,
            )
        }.asSequence()
    }

    override val allPublicFunctions: Sequence<FunctionLangModel> by lazy {
        buildList<FunctionLangModel> {
            kotlinClass.companionObject?.let { companion ->
                val companionDeclaration = RtTypeDeclarationImpl(RtTypeImpl(companion.java))
                for (method in companion.java.methods) {
                    if (method.isAnnotationPresent(JvmStatic::class.java)) {
                        // Skip the @JvmStatic companion object member, as there is a static counterpart for it.
                        continue
                    }
                    add(RtFunctionImpl(impl = method, owner = companionDeclaration))
                }
            }
            for (method in impl.getMethodsOverrideAware()) {
                add(RtFunctionImpl(impl = method, owner = this@RtTypeDeclarationImpl))
            }
        }.asSequence()
    }

    override val allPublicFields: Sequence<FieldLangModel> by lazy {
        impl.fields.map {
            RtFieldImpl(
                impl = it,
                owner = this,
            )
        }.asSequence()
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
        override val parameters: Sequence<ParameterLangModel> by lazy {
            impl.resolveParameters(asMemberOf = type.impl).asSequence()
        }
        override val platformModel: Constructor<*> get() = impl
    }

    internal fun findKotlinPropertyAccessorFor(method: Method): RtPropertyAccessorImpl? {
        if (method.parameterCount > 1) return null
        val kotlin = method.declaringClass.kotlin
        val properties = if (method.isStatic) kotlin.staticProperties else kotlin.declaredMemberProperties
        when (method.parameterCount) {
            0 -> properties.forEach {
                if (it.javaGetter == method)
                    return RtPropertyAccessorImpl(Getter, it)
            }
            1 -> properties.forEach {
                if (it is KMutableProperty<*> && it.javaGetter == method)
                    return RtPropertyAccessorImpl(Getter, it)
            }
        }
        return null
    }

    companion object Factory : ObjectCache<RtTypeImpl, RtTypeDeclarationImpl>() {
        operator fun invoke(type: RtTypeImpl) = createCached(type, ::RtTypeDeclarationImpl)
    }
}