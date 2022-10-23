package com.yandex.daggerlite.lang.jap

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.base.mapToArray
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.lang.AnnotatedLangModel
import com.yandex.daggerlite.lang.Constructor
import com.yandex.daggerlite.lang.Field
import com.yandex.daggerlite.lang.Method
import com.yandex.daggerlite.lang.Parameter
import com.yandex.daggerlite.lang.Type
import com.yandex.daggerlite.lang.TypeDeclarationKind
import com.yandex.daggerlite.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.lang.common.ConstructorBase
import com.yandex.daggerlite.lang.compiled.CtAnnotatedLangModel
import com.yandex.daggerlite.lang.compiled.CtTypeDeclarationLangModel
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.NestingKind
import javax.lang.model.element.TypeElement
import javax.lang.model.element.TypeParameterElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.type.TypeVariable
import javax.lang.model.type.WildcardType
import javax.lang.model.util.TypeKindVisitor7
import kotlin.LazyThreadSafetyMode.PUBLICATION

internal class JavaxTypeDeclarationImpl private constructor(
    val type: DeclaredType,
) : CtAnnotatedLangModel by JavaxAnnotatedImpl(type.asTypeElement()), CtTypeDeclarationLangModel() {
    private val impl = type.asTypeElement()

    override val isEffectivelyPublic: Boolean
        get() = impl.isPublic

    override val isAbstract: Boolean
        get() = impl.isAbstract &&
                impl.kind != ElementKind.ANNOTATION_TYPE  // Do not treat @interface as abstract for consistency

    override val kind: TypeDeclarationKind
        get() = when(impl.kind) {
            ElementKind.ENUM -> TypeDeclarationKind.Enum
            ElementKind.CLASS -> when {
                impl.isDefaultCompanionObject() -> TypeDeclarationKind.KotlinCompanion
                impl.isKotlinSingleton() -> TypeDeclarationKind.KotlinObject
                else -> TypeDeclarationKind.Class
            }
            ElementKind.ANNOTATION_TYPE -> TypeDeclarationKind.Annotation
            ElementKind.INTERFACE -> TypeDeclarationKind.Interface
            else -> TypeDeclarationKind.None
        }

    override val qualifiedName: String
        get() = impl.qualifiedName.toString()

    override val enclosingType: TypeDeclarationLangModel?
        get() = when (impl.nestingKind) {
            NestingKind.MEMBER -> Factory(impl.enclosingElement.asType().asDeclaredType())
            else -> null
        }

    override val interfaces: Sequence<Type> by lazy {
        impl.interfaces.asSequence()
            .map { JavaxTypeImpl(it.asMemberOfThis()) }
            .memoize()
    }

    override val superType: Type? by lazy {
        impl.superclass.takeIf {
            it.kind == TypeKind.DECLARED && it.asTypeElement() != Utils.objectType
        }?.let { superClass ->
            JavaxTypeImpl(superClass.asMemberOfThis())
        }
    }

    override val constructors: Sequence<Constructor> by lazy {
        impl.enclosedElements
        .asSequence()
        .filter { it.kind == ElementKind.CONSTRUCTOR && !it.isPrivate }
        .map {
            ConstructorImpl(platformModel = it.asExecutableElement())
        }.memoize()
    }

    override val methods: Sequence<Method> by lazy {
        impl.allNonPrivateMethods()
        .run {
            when (kind) {
                TypeDeclarationKind.KotlinCompanion -> filterNot {
                    // Such methods already have a truly static counterpart so skip them.
                    it.isAnnotatedWith<JvmStatic>()
                }
                else -> this
            }
        }
        .map {
            JavaxMethodImpl(
                owner = this@JavaxTypeDeclarationImpl,
                impl = it,
            )
        }.memoize()
    }

    override val fields: Sequence<Field> by lazy {
        impl.allNonPrivateFields()
        .map { JavaxFieldImpl(owner = this, impl = it) }
        .memoize()
    }

    override val nestedClasses: Sequence<TypeDeclarationLangModel> by lazy {
        impl.enclosedElements
        .asSequence()
        .filter {
            when (it.kind) {
                ElementKind.ENUM, ElementKind.CLASS,
                ElementKind.ANNOTATION_TYPE, ElementKind.INTERFACE,
                -> !it.isPrivate
                else -> false
            }
        }
        .map { Factory(it.asType().asDeclaredType()) }
        .memoize()
    }

    override val defaultCompanionObjectDeclaration: TypeDeclarationLangModel? by lazy {
        if (impl.isFromKotlin()) {
            impl.enclosedElements.find {
                it.kind == ElementKind.CLASS && it.asTypeElement().isDefaultCompanionObject()
            }?.let { JavaxTypeDeclarationImpl(it.asType().asDeclaredType()) }
        } else null
    }

    override fun asType(): Type {
        return JavaxTypeImpl(type)
    }

    override val platformModel: TypeElement get() = impl

    private val genericsInfo: Map<TypeParameterElement, TypeMirror> by lazy(PUBLICATION) {
        impl.typeParameters
            .zip(type.typeArguments)
            .toMap()
    }

    private fun TypeMirror.asMemberOfThis(): TypeMirror {
        return accept(object : TypeKindVisitor7<TypeMirror, Nothing?>(this) {
            override fun visitTypeVariable(type: TypeVariable, p: Nothing?): TypeMirror {
                // Actual resolution happens here
                return genericsInfo[type.asElement().asTypeParameterElement()] ?: type
            }

            override fun visitDeclared(type: DeclaredType, p: Nothing?): TypeMirror {
                if (type.typeArguments.isEmpty())
                    return type

                val resolved = type.typeArguments.mapToArray { it.asMemberOfThis() }
                return Utils.types.getDeclaredType(type.asTypeElement(), *resolved)
            }

            override fun visitWildcard(type: WildcardType, p: Nothing?): TypeMirror {
                return Utils.types.getWildcardType(
                    type.extendsBound?.asMemberOfThis(),
                    type.superBound?.asMemberOfThis(),
                )
            }

            override fun visitArray(type: ArrayType, p: Nothing?): TypeMirror {
                return Utils.types.getArrayType(
                    type.componentType.asMemberOfThis(),
                )
            }
        }, null)
    }

    companion object Factory : ObjectCache<TypeMirrorEquivalence, JavaxTypeDeclarationImpl>() {
        operator fun invoke(
            impl: DeclaredType,
        ): JavaxTypeDeclarationImpl {
            return createCached(TypeMirrorEquivalence(impl)) {
                JavaxTypeDeclarationImpl(type = impl)
            }
        }
    }

    private inner class ConstructorImpl(
        override val platformModel: ExecutableElement,
    ) : ConstructorBase(), AnnotatedLangModel by JavaxAnnotatedImpl(platformModel) {
        override val isEffectivelyPublic: Boolean get() = platformModel.isPublic
        override val constructee: TypeDeclarationLangModel get() = this@JavaxTypeDeclarationImpl
        override val parameters: Sequence<Parameter> = parametersSequenceFor(platformModel, type)
    }
}