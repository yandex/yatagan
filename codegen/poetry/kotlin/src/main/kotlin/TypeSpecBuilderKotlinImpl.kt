package com.yandex.yatagan.codegen.poetry.kotlin

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSPropertyGetter
import com.google.devtools.ksp.symbol.KSPropertySetter
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.jvm.jvmStatic
import com.squareup.kotlinpoet.ksp.toTypeName
import com.yandex.yatagan.base.api.Internal
import com.yandex.yatagan.codegen.poetry.Access
import com.yandex.yatagan.codegen.poetry.ClassName
import com.yandex.yatagan.codegen.poetry.FieldSpecBuilder
import com.yandex.yatagan.codegen.poetry.MethodSpecBuilder
import com.yandex.yatagan.codegen.poetry.TypeName
import com.yandex.yatagan.codegen.poetry.TypeSpecBuilder
import com.yandex.yatagan.lang.Method

internal class TypeSpecBuilderKotlinImpl(
    private val builder: TypeSpec.Builder,
) : TypeSpecBuilder {
    private val onCompanion = mutableListOf<TypeSpec.Builder.() -> Unit>()
    private val onProperty = mutableMapOf<KSPropertyDeclaration, MutableList<PropertySpec.Builder.() -> Unit>>()

    fun build(): TypeSpec {
        if (onCompanion.isNotEmpty()) {
            val companionBuilder = TypeSpec.companionObjectBuilder()
            companionBuilder.addModifiers(KModifier.PUBLIC)
            for (builderAction in onCompanion) {
                companionBuilder.builderAction()
            }
            builder.addType(companionBuilder.build())
        }

        for ((property, builderActions) in onProperty) {
            val propertyBuilder = PropertySpec.builder(
                name = property.simpleName.asString(),
                type = property.type.toTypeName(),
                KModifier.PUBLIC, KModifier.OVERRIDE,
            )
            propertyBuilder.mutable(property.isMutable)
            for (builderAction in builderActions) {
                propertyBuilder.builderAction()
            }
            builder.addProperty(propertyBuilder.build())
        }
        return builder.build()
    }

    override fun addSuppressWarningsAnnotation(vararg warnings: String) {
        val format = warnings.joinToString { "%S" }
        builder.addAnnotation(AnnotationSpec.builder(Suppress::class)
            .addMember(format, *warnings)
            .build())
    }

    override fun generic(typeVariable: TypeName.TypeVariable) {
        builder.addTypeVariable(TypeVariableName(typeVariable))
    }

    override fun implements(name: TypeName) {
        builder.addSuperinterface(KotlinTypeName(name))
    }

    override fun primaryConstructor(access: Access, block: MethodSpecBuilder.() -> Unit) {
        val constructorBuilder = FunSpec.constructorBuilder()
        constructorBuilder.addModifiers(when (access) {
            Access.Public -> KModifier.PUBLIC
            Access.Internal -> KModifier.INTERNAL
            Access.Private -> KModifier.PRIVATE
        })
        builder.primaryConstructor(MethodSpecBuilderKotlinImpl(constructorBuilder).apply(block).build())
    }

    override fun field(
        type: TypeName,
        name: String,
        isMutable: Boolean,
        access: Access,
        block: FieldSpecBuilder.() -> Unit,
    ) {
        val kotlinType = KotlinTypeName(type)
        val fieldBuilder = PropertySpec.builder(
            name = name,
            type = kotlinType,
        )
        fieldBuilder.mutable(mutable = isMutable)
            .addModifiers(when (access) {
                Access.Public -> KModifier.PUBLIC
                Access.Internal -> KModifier.INTERNAL
                Access.Private -> KModifier.PRIVATE
            })
        builder.addProperty(FieldSpecBuilderKotlinImpl(
            builder = fieldBuilder,
            defaultInitializer = defaultInitializerFor(kotlinType).takeIf { isMutable },
        ).apply(block).build())
    }

    override fun method(name: String, access: Access, isStatic: Boolean, block: MethodSpecBuilder.() -> Unit) {
        val functionBuilder = FunSpec.builder(name)
        functionBuilder.addModifiers(when (access) {
            Access.Public -> KModifier.PUBLIC
            Access.Internal -> KModifier.INTERNAL
            Access.Private -> KModifier.PRIVATE
        })
        if (isStatic) {
            functionBuilder.jvmStatic()
        }
        val spec = MethodSpecBuilderKotlinImpl(functionBuilder).apply(block).build()
        if (isStatic) {
            onCompanion += { addFunction(spec) }
        } else {
            builder.addFunction(spec)
        }
    }

    @OptIn(Internal::class)
    override fun overrideMethod(method: Method, block: MethodSpecBuilder.() -> Unit) {
        when(val model = method.platformModel) {
            is KSPropertyGetter -> {
                val property = model.receiver
                onProperty.getOrPut(property, ::mutableListOf).add {
                    val getterBuilder = FunSpec.getterBuilder()
                    getter(MethodSpecBuilderKotlinImpl(getterBuilder).apply(block).build())
                }
            }
            is KSPropertySetter -> {
                val property = model.receiver
                onProperty.getOrPut(property, ::mutableListOf).add {
                    val setterBuilder = FunSpec.setterBuilder()
                    setter(MethodSpecBuilderKotlinImpl(setterBuilder).apply(block).build())
                }
            }
            is KSFunctionDeclaration -> {
                val functionBuilder = FunSpec.builder(model.simpleName.asString())
                    .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                    .returns(model.returnType!!.toTypeName())
                    .apply {
                        for (parameter in model.parameters) {
                            addParameter(
                                name = parameter.name!!.asString(),
                                type = parameter.type.toTypeName(),
                            )
                        }
                    }
                builder.addFunction(MethodSpecBuilderKotlinImpl(functionBuilder).apply(block).build())
            }
            else -> {
                val functionBuilder = FunSpec.builder(method.name)
                    .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                    .returns(KotlinTypeName(method.returnType))
                    .apply {
                        for (parameter in method.parameters) {
                            addParameter(
                                name = parameter.name,
                                type = KotlinTypeName(parameter.type),
                            )
                        }
                    }
                builder.addFunction(MethodSpecBuilderKotlinImpl(functionBuilder).apply(block).build())
            }
        }
    }

    override fun nestedClass(name: ClassName, access: Access, isInner: Boolean, block: TypeSpecBuilder.() -> Unit) {
        val classBuilder = TypeSpec.classBuilder(KotlinClassName(name))
        classBuilder.addModifiers(when (access) {
            Access.Public -> KModifier.PUBLIC
            Access.Internal -> KModifier.INTERNAL
            Access.Private -> KModifier.PRIVATE
        })
        if (isInner) {
            classBuilder.addModifiers(KModifier.INNER)
        }
        builder.addType(TypeSpecBuilderKotlinImpl(classBuilder).apply(block).build())
    }
}