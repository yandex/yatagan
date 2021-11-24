package com.yandex.daggerlite.rt.lang

/*

TODO: Revisit RT implementation once we're ready for it.

import com.yandex.daggerlite.Component
import com.yandex.daggerlite.Module
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.lang.AnnotatedLangModel
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.ComponentAnnotationLangModel
import com.yandex.daggerlite.core.lang.ComponentFlavorAnnotationLangModel
import com.yandex.daggerlite.core.lang.ConditionLangModel
import com.yandex.daggerlite.core.lang.ConditionalAnnotationLangModel
import com.yandex.daggerlite.core.lang.FieldLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.ModuleAnnotationLangModel
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.core.lang.ProvidesAnnotationLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import javax.inject.Qualifier
import javax.inject.Scope
import kotlin.LazyThreadSafetyMode.NONE
import kotlin.reflect.KClass

// TODO: split this into separate source files per class.

internal class RtAnnotationImpl(
    private val impl: Annotation,
) : AnnotationLangModel {
    override val isScope: Boolean
        get() = impl.javaAnnotationClass.isAnnotationPresent(Scope::class.java)
    override val isQualifier: Boolean
        get() = impl.javaAnnotationClass.isAnnotationPresent(Qualifier::class.java)

    override fun <A : Annotation> hasType(type: Class<A>): Boolean {
        return impl.javaAnnotationClass == type
    }

    override fun equals(other: Any?): Boolean {
        return this === other || (other is RtAnnotationImpl && other.impl == impl)
    }

    override fun hashCode() = impl.hashCode()
}

class RtComponentAnnotationImpl(
    impl: Component,
) : ComponentAnnotationLangModel {
    override val isRoot: Boolean = impl.isRoot
    override val modules: Sequence<TypeLangModel> = impl.modules.asSequence()
        .map(KClass<*>::java).map(::RtTypeDeclarationImpl).memoize()
    override val dependencies: Sequence<TypeLangModel> = impl.dependencies.asSequence()
        .map(KClass<*>::java).map(::RtTypeDeclarationImpl).memoize()
    override val variant: Sequence<TypeLangModel> =  impl.variant.asSequence()
        .map(KClass<*>::java).map(::RtTypeDeclarationImpl).memoize()
}

class RtModuleAnnotationImpl(
    impl: Module,
) : ModuleAnnotationLangModel {
    override val includes: Sequence<TypeLangModel> = impl.includes.asSequence()
        .map(KClass<*>::java).map(::RtTypeDeclarationImpl).memoize()
    override val subcomponents: Sequence<TypeLangModel> = impl.subcomponents.asSequence()
        .map(KClass<*>::java).map(::RtTypeDeclarationImpl).memoize()
    override val bootstrap: Sequence<TypeLangModel>
        get() = TODO("Not yet implemented")
}

abstract class RtAnnotatedImpl : AnnotatedLangModel {
    protected abstract val impl: AnnotatedElement

    override val annotations: Sequence<AnnotationLangModel> by lazy(NONE) {
        impl.annotations.asSequence().map(::RtAnnotationImpl).memoize()
    }

    override fun <A : Annotation> isAnnotatedWith(type: Class<A>): Boolean {
        return impl.isAnnotationPresent(type)
    }

    override fun <A : Annotation> getAnnotation(type: Class<A>): AnnotationLangModel {
        return RtAnnotationImpl(impl.getAnnotation(type)!!)
    }
}

class RtTypeDeclarationImpl(
    override val impl: Class<*>,
) : RtAnnotatedImpl(), TypeDeclarationLangModel, TypeLangModel {
    override val isAbstract: Boolean
        get() = Modifier.isAbstract(impl.modifiers)
    override val isKotlinObject: Boolean by lazy(NONE) {
        impl.isAnnotationPresent(Metadata::class.java) && impl.declaredFields.any {
            // TODO: improve this heuristic.
            it.name == "INSTANCE" && Modifier.isStatic(it.modifiers) && Modifier.isFinal(it.modifiers)
        }
    }
    override val qualifiedName: String
        get() = impl.canonicalName
    override val constructors: Sequence<FunctionLangModel> = impl.constructors.asSequence().map {
        RtConstructorImpl(owner = this, impl = it)
    }.memoize()
    override val implementedInterfaces: Sequence<TypeLangModel>
        get() = TODO("Not yet implemented")
    override val allPublicFunctions: Sequence<FunctionLangModel> = impl.declaredMethods.asSequence().map {
        RtFunctionImpl(owner = this, impl = it)
    }
    override val nestedInterfaces: Sequence<TypeDeclarationLangModel> = impl.declaredClasses
        .asSequence().filter(Class<*>::isInterface).map(::RtTypeDeclarationImpl)

    override fun asType(): TypeLangModel {
        require(impl.typeParameters.isEmpty())
        return this
    }

    override val allPublicFields: Sequence<FieldLangModel>
        get() = TODO("Not yet implemented")

    override val declaration: TypeDeclarationLangModel get() = this
    override val typeArguments: Collection<Nothing> get() = emptyList()

    override val componentAnnotationIfPresent: ComponentAnnotationLangModel?
        get() = impl.getAnnotation(Component::class.java)?.let(::RtComponentAnnotationImpl)
    override val moduleAnnotationIfPresent: ModuleAnnotationLangModel?
        get() = impl.getAnnotation(Module::class.java)?.let(::RtModuleAnnotationImpl)

    override fun equals(other: Any?): Boolean {
        return this === other || (other is RtTypeDeclarationImpl && impl == other.impl)
    }

    override fun hashCode() = impl.hashCode()

    override val conditions: Sequence<ConditionLangModel>
        get() = TODO("Not yet implemented")
    override val conditionals: Sequence<ConditionalAnnotationLangModel>
        get() = TODO("Not yet implemented")
    override val componentFlavorIfPresent: ComponentFlavorAnnotationLangModel?
        get() = TODO("Not yet implemented")
    override val isBoolean: Boolean
        get() = TODO("Not yet implemented")
}

class RtTypeImpl(
    private val impl: Type,
) : TypeLangModel {
    override val declaration: TypeDeclarationLangModel by lazy(NONE) {
        RtTypeDeclarationImpl(
            when (impl) {
                is Class<*> -> impl
                is ParameterizedType -> impl.rawType as Class<*>
                else -> throw UnsupportedOperationException("no type declaration implemented for ${impl.javaClass}")
            }
        )
    }
    override val typeArguments: Collection<TypeLangModel> = when (impl) {
        is ParameterizedType -> impl.actualTypeArguments.map(::RtTypeImpl)
        else -> emptyList()
    }
    override val isBoolean: Boolean
        get() = TODO("Not yet implemented")

    override fun equals(other: Any?): Boolean {
        return this === other || (other is RtTypeImpl && impl == other.impl)
    }

    override fun hashCode(): Int = impl.hashCode()
}

class RtParameterImpl(
    override val annotations: Sequence<AnnotationLangModel>,
    override val name: String,
    override val type: TypeLangModel,
) : ParameterLangModel

class RtConstructorImpl(
    override val owner: TypeDeclarationLangModel,
    override val impl: Constructor<*>,
    override val isFromCompanionObject: Boolean = TODO("implement it")
) : RtAnnotatedImpl(), FunctionLangModel {
    override val isConstructor: Boolean get() = true
    override val isAbstract: Boolean get() = false
    override val isStatic: Boolean get() = true
    override val providesAnnotationIfPresent: ProvidesAnnotationLangModel?
        get() = TODO("Not yet implemented")
    override val returnType: TypeLangModel get() = owner.asType()
    override val name: String get() = impl.name
    override val parameters: Sequence<ParameterLangModel> = parametersOf(impl)
}

class RtFunctionImpl(
    override val owner: TypeDeclarationLangModel,
    override val impl: Executable,
    override val isFromCompanionObject: Boolean = TODO("implement it")
) : RtAnnotatedImpl(), FunctionLangModel {
    override val isConstructor: Boolean get() = false
    override val isAbstract: Boolean get() = Modifier.isAbstract(impl.modifiers)
    override val isStatic: Boolean get() = Modifier.isStatic(impl.modifiers)
    override val returnType: TypeLangModel by lazy(NONE) {
        RtTypeImpl(impl.annotatedReturnType.type)
    }
    override val providesAnnotationIfPresent: ProvidesAnnotationLangModel?
        get() = TODO("Not yet implemented")
    override val name: String get() = impl.name
    override val parameters: Sequence<ParameterLangModel> = parametersOf(impl)
}

fun parametersOf(impl: Executable): Sequence<ParameterLangModel> = impl
    .parameters.asSequence()
    .zip(impl.parameterAnnotations.asSequence())
    .map { (param, annotations) ->
        RtParameterImpl(
            annotations = annotations.asSequence().map(::RtAnnotationImpl).memoize(),
            name = param.name,
            type = RtTypeImpl(param.parameterizedType),
        )
    }.memoize()

 */