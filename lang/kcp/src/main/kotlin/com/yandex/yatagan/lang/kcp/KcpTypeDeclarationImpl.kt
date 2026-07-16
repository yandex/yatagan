/*
 * Copyright 2026 Yandex LLC
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

@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package com.yandex.yatagan.lang.kcp

import com.yandex.yatagan.lang.Constructor
import com.yandex.yatagan.lang.Field
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.TypeDeclarationKind
import com.yandex.yatagan.lang.compiled.CtAnnotationBase
import com.yandex.yatagan.lang.compiled.CtTypeDeclarationBase
import com.yandex.yatagan.lang.scope.LexicalScope
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.AbstractIrTypeSubstitutor
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isObject
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.FqName

internal class KcpTypeDeclarationImpl(
    internal val type: KcpTypeImpl,
) : CtTypeDeclarationBase(), LexicalScope by type {
    internal val impl: IrClass = checkNotNull(type.impl.classOrNull).owner

    init {
        // Feed IC lookup recording: the graph's shape depends on every class wrapped here.
        kcpScope.resolvedClassesMutable.add(impl)
    }

    // Materialized once: annotations are queried constantly (scopes, qualifiers,
    // conditionals) and re-wrapping on every access dominates graph construction time.
    override val annotations: Sequence<CtAnnotationBase> by lazy {
        impl.annotations.map { KcpAnnotationImpl(this, it) }.asSequence()
    }

    override val platformModel: IrClassSymbol
        get() = impl.symbol

    override val kind: TypeDeclarationKind
        get() = when {
            impl.isCompanion && impl.name.asString() == "Companion" -> TypeDeclarationKind.KotlinCompanion
            impl.isObject -> TypeDeclarationKind.KotlinObject
            else -> when (impl.kind) {
                ClassKind.INTERFACE -> TypeDeclarationKind.Interface
                ClassKind.CLASS -> TypeDeclarationKind.Class
                ClassKind.ENUM_CLASS, ClassKind.ENUM_ENTRY -> TypeDeclarationKind.Enum
                ClassKind.ANNOTATION_CLASS -> TypeDeclarationKind.Annotation
                else -> TypeDeclarationKind.Class
            }
        }

    override val isAbstract: Boolean
        get() = impl.modality == Modality.ABSTRACT

    override val isEffectivelyPublic: Boolean
        get() = impl.visibility == DescriptorVisibilities.PUBLIC ||
                impl.visibility == DescriptorVisibilities.INTERNAL

    override val qualifiedName: String
        get() = type.nameModel.let { model ->
            when (model) {
                is com.yandex.yatagan.lang.compiled.ParameterizedNameModel -> model.raw.toString()
                else -> model.toString()
            }
        }

    override val enclosingType: TypeDeclaration?
        get() = (impl.parent as? IrClass)?.let { kcpType(it.symbol.defaultType).declaration }

    override val interfaces: Sequence<Type>
        get() = impl.superTypes.asSequence()
            .filter { it.classOrNull?.owner?.kind == ClassKind.INTERFACE }
            .map { kcpType(refineDeclaredType(it)) }

    override val superType: Type?
        get() = impl.superTypes.firstOrNull { superType ->
            val superClass = superType.classOrNull ?: return@firstOrNull false
            superClass.owner.kind != ClassKind.INTERFACE &&
                    superClass != kcpScope.pluginContext.irBuiltIns.anyClass
        }?.let { kcpType(refineDeclaredType(it)) }

    override val constructors: Sequence<Constructor> by lazy {
        if (kind == TypeDeclarationKind.Annotation) {
            emptySequence()
        } else {
            impl.constructors
                .filter { it.isNonPrivate }
                .map { KcpConstructorImpl(it, this) }
                .toList()
                .asSequence()
        }
    }

    override val methods: Sequence<Method> by lazy {
        buildList<KcpMethodImpl> {
            when (kind) {
                TypeDeclarationKind.KotlinObject -> addMethodsFrom(impl)
                TypeDeclarationKind.KotlinCompanion -> addMethodsFrom(
                    declaration = impl,
                    excludeJvmStatic = true,
                )
                else -> {
                    addMethodsFrom(impl)
                    impl.companionObjectOrNull()?.let { companion ->
                        addMethodsFrom(
                            declaration = companion,
                            forceStatic = true,
                            onlyJvmStatic = true,
                        )
                    }
                }
            }
        }.distinctBy { method ->
            method.name to method.parameters.map { it.type.toString() }.toList()
        }.asSequence()
    }

    override val fields: Sequence<Field> by lazy {
        buildList<Field> {
            when (kind) {
                TypeDeclarationKind.KotlinObject -> {
                    addFieldsFromHierarchy(impl, rootIsObject = true)
                    add(KcpSyntheticFieldImpl(owner = this@KcpTypeDeclarationImpl, name = "INSTANCE"))
                }
                TypeDeclarationKind.KotlinCompanion -> Unit
                else -> {
                    addFieldsFromHierarchy(impl, rootIsObject = false)
                    impl.companionObjectOrNull()?.let { companion ->
                        addExposedKotlinFields(companion, forceStatic = true, exposeObjectFields = true)
                        add(KcpSyntheticFieldImpl(
                            owner = this@KcpTypeDeclarationImpl,
                            type = kcpType(companion.symbol.defaultType),
                            name = companion.name.asString(),
                        ))
                    }
                }
            }
        }.distinctBy { field -> field.name to (field.platformModel ?: field) }.asSequence()
    }

    override val nestedClasses: Sequence<TypeDeclaration>
        get() = impl.declarations.asSequence()
            .filterIsInstance<IrClass>()
            .filter { it.visibility != DescriptorVisibilities.PRIVATE }
            .map { kcpType(it.symbol.defaultType).declaration }

    override val defaultCompanionObjectDeclaration: TypeDeclaration?
        get() = impl.declarations.asSequence()
            .filterIsInstance<IrClass>()
            .singleOrNull { it.isCompanion && it.name.asString() == "Companion" }
            ?.let { kcpType(it.symbol.defaultType).declaration }

    override fun asType(): Type = type

    internal fun refine(memberType: IrType, declaredIn: IrClass): IrType {
        return AbstractIrTypeSubstitutor.forSuperClass(declaredIn.symbol, type.impl)
            ?.substitute(memberType)
            ?: memberType
    }

    private fun refineDeclaredType(superType: IrType): IrType {
        return AbstractIrTypeSubstitutor.forType(type.impl).substitute(superType)
    }

    private fun MutableList<KcpMethodImpl>.addMethodsFrom(
        declaration: IrClass,
        forceStatic: Boolean = false,
        onlyJvmStatic: Boolean = false,
        excludeJvmStatic: Boolean = false,
    ) {
        for (current in classHierarchy(declaration)) {
            for (member in current.declarations) {
                when (member) {
                    is IrSimpleFunction -> {
                        if (member.isFakeOverride || !member.isNonPrivate) continue
                        val isJvmStatic = member.hasAnnotation(JvmStaticFqName)
                        if (onlyJvmStatic && !isJvmStatic || excludeJvmStatic && isJvmStatic) continue
                        add(KcpMethodImpl(
                            impl = member,
                            owner = this@KcpTypeDeclarationImpl,
                            name = member.jvmNameOrNull() ?: member.name.asString(),
                            isStatic = forceStatic || member.dispatchReceiverParameter == null || isJvmStatic,
                        ))
                    }
                    is IrProperty -> {
                        if (member.isFakeOverride || !member.isNonPrivate || member.isJvmField()) continue
                        val propertyIsJvmStatic = member.hasAnnotation(JvmStaticFqName)
                        member.getter?.let { getter ->
                            if (!getter.isNonPrivate) return@let
                            val accessorIsJvmStatic = getter.hasAnnotation(JvmStaticFqName)
                            val isJvmStatic = propertyIsJvmStatic || accessorIsJvmStatic
                            if (onlyJvmStatic && !isJvmStatic || excludeJvmStatic && isJvmStatic) return@let
                            add(KcpMethodImpl(
                                impl = getter,
                                owner = this@KcpTypeDeclarationImpl,
                                name = getter.jvmNameOrNull() ?: member.getterName(),
                                isStatic = forceStatic || getter.dispatchReceiverParameter == null || isJvmStatic,
                            ))
                        }
                        member.setter?.let { setter ->
                            if (!setter.isNonPrivate || setter.visibility == DescriptorVisibilities.PROTECTED) return@let
                            val accessorIsJvmStatic = setter.hasAnnotation(JvmStaticFqName)
                            val isJvmStatic = propertyIsJvmStatic || accessorIsJvmStatic
                            if (onlyJvmStatic && !isJvmStatic || excludeJvmStatic && isJvmStatic) return@let
                            add(KcpMethodImpl(
                                impl = setter,
                                owner = this@KcpTypeDeclarationImpl,
                                name = setter.jvmNameOrNull() ?: member.setterName(),
                                isStatic = forceStatic || setter.dispatchReceiverParameter == null || isJvmStatic,
                            ))
                        }
                    }
                }
            }
        }
    }

    private fun MutableList<Field>.addFieldsFromHierarchy(
        declaration: IrClass,
        rootIsObject: Boolean,
    ) {
        classHierarchy(declaration).forEachIndexed { index, current ->
            val forceStatic = rootIsObject && index == 0
            if (current.origin == IrDeclarationOrigin.IR_EXTERNAL_JAVA_DECLARATION_STUB) {
                current.declarations.asSequence()
                    .mapNotNull { member ->
                        when (member) {
                            is IrField -> member.takeIf { it.isNonPrivate }
                            // Java fields surface as properties with a backing field.
                            is IrProperty -> member.backingField
                                ?.takeIf { member.isNonPrivate }

                            else -> null
                        }
                    }
                    .mapTo(this) { field ->
                        KcpFieldImpl(
                            impl = field,
                            owner = this@KcpTypeDeclarationImpl,
                            isStatic = forceStatic || field.isStatic,
                            declaredIn = current,
                        )
                    }
            } else {
                addExposedKotlinFields(
                    declaration = current,
                    forceStatic = forceStatic,
                    exposeObjectFields = index == 0 && rootIsObject,
                )
            }
        }
    }

    private fun MutableList<Field>.addExposedKotlinFields(
        declaration: IrClass,
        forceStatic: Boolean,
        exposeObjectFields: Boolean,
    ) {
        declaration.declarations.asSequence()
            .filterIsInstance<IrProperty>()
            .filter { property ->
                property.isNonPrivate && (
                        property.isLateinit || exposeObjectFields && (property.isConst || property.isJvmField())
                )
            }
            .mapNotNull { property -> property.backingField?.let { property to it } }
            .mapTo(this) { (property, field) ->
                KcpFieldImpl(
                    impl = field,
                    owner = this@KcpTypeDeclarationImpl,
                    isStatic = forceStatic || field.isStatic,
                    declaredIn = declaration,
                    effectivelyPublic = property.isEffectivelyPublic,
                )
            }
    }

    private fun classHierarchy(root: IrClass): List<IrClass> = buildList {
        val visited = mutableSetOf<IrClassSymbol>()

        fun visit(current: IrClass) {
            if (!visited.add(current.symbol)) return
            add(current)
            current.superTypes.forEach { superType ->
                val superClass = superType.classOrNull ?: return@forEach
                if (superClass != kcpScope.pluginContext.irBuiltIns.anyClass) {
                    visit(superClass.owner)
                }
            }
        }

        visit(root)
    }

    private fun IrClass.companionObjectOrNull(): IrClass? {
        return declarations.asSequence()
            .filterIsInstance<IrClass>()
            .singleOrNull { it.isCompanion }
    }
}

private fun IrSimpleFunction.jvmNameOrNull(): String? {
    return getAnnotation(JvmNameFqName)
        ?.arguments
        ?.firstOrNull()
        ?.let { (it as? IrConst)?.value as? String }
}

private fun IrProperty.getterName(): String {
    return JvmAbi.getterName(name.asString())
}

private fun IrProperty.setterName(): String {
    return JvmAbi.setterName(name.asString())
}

private fun IrProperty.isJvmField(): Boolean {
    return isConst || hasAnnotation(JvmFieldFqName) || backingField?.hasAnnotation(JvmFieldFqName) == true
}

private val JvmFieldFqName = FqName("kotlin.jvm.JvmField")
private val JvmNameFqName = FqName("kotlin.jvm.JvmName")
private val JvmStaticFqName = FqName("kotlin.jvm.JvmStatic")
