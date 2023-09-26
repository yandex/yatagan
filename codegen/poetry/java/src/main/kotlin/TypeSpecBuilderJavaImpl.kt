package com.yandex.yatagan.codegen.poetry.java

import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.TypeSpec
import com.squareup.javapoet.TypeVariableName
import com.yandex.yatagan.codegen.poetry.Access
import com.yandex.yatagan.codegen.poetry.ClassName
import com.yandex.yatagan.codegen.poetry.FieldSpecBuilder
import com.yandex.yatagan.codegen.poetry.MethodSpecBuilder
import com.yandex.yatagan.codegen.poetry.TypeName
import com.yandex.yatagan.codegen.poetry.TypeSpecBuilder
import com.yandex.yatagan.lang.Method
import javax.lang.model.element.Modifier

internal class TypeSpecBuilderJavaImpl(
    private val builder: TypeSpec.Builder,
) : TypeSpecBuilder {
    fun build(): TypeSpec = builder.build()

    override fun addSuppressWarningsAnnotation(vararg warnings: String) {
        val format = warnings.joinToString(prefix = "{", postfix = "}") { "\$S" }
        builder.addAnnotation(
            AnnotationSpec.builder(SuppressWarnings::class.java)
                .addMember("value", format, *warnings)
                .build()
        )
    }

    override fun generic(typeVariable: TypeName.TypeVariable) {
        builder.addTypeVariable(TypeVariableName.get(typeVariable.name))
    }

    override fun implements(name: TypeName) {
        builder.addSuperinterface(JavaTypeName(name))
    }

    override fun primaryConstructor(access: Access, block: MethodSpecBuilder.() -> Unit) {
        val constructorBuilder = MethodSpec.constructorBuilder()
        when(access) {
            Access.Public -> constructorBuilder.addModifiers(Modifier.PUBLIC)
            Access.Private -> constructorBuilder.addModifiers(Modifier.PRIVATE)
            Access.Internal -> {}
        }
        builder.addMethod(MethodSpecBuilderJavaImpl(constructorBuilder).apply(block).build())
    }

    override fun field(
        type: TypeName,
        name: String,
        isMutable: Boolean,
        access: Access,
        block: FieldSpecBuilder.() -> Unit,
    ) {
        val fieldBuilder = FieldSpec.builder(
            JavaTypeName(type), name
        )
        if (!isMutable) {
            fieldBuilder.addModifiers(Modifier.FINAL)
        }
        when(access) {
            Access.Public -> fieldBuilder.addModifiers(Modifier.PUBLIC)
            Access.Private -> fieldBuilder.addModifiers(Modifier.PRIVATE)
            Access.Internal -> {}
        }
        builder.addField(FieldSpecBuilderJavaImpl(fieldBuilder).apply(block).build())
    }

    override fun method(name: String, access: Access, isStatic: Boolean, block: MethodSpecBuilder.() -> Unit) {
        val methodBuilder = MethodSpec.methodBuilder(name)
        when(access) {
            Access.Public -> methodBuilder.addModifiers(Modifier.PUBLIC)
            Access.Private -> methodBuilder.addModifiers(Modifier.PRIVATE)
            Access.Internal -> {}
        }
        if (isStatic) {
            methodBuilder.addModifiers(Modifier.STATIC)
        }
        builder.addMethod(MethodSpecBuilderJavaImpl(methodBuilder).apply(block).build())
    }

    override fun overrideMethod(method: Method, block: MethodSpecBuilder.() -> Unit) {
        val methodBuilder = MethodSpec.methodBuilder(method.name)
            .addAnnotation(Override::class.java)
            .addModifiers(Modifier.PUBLIC)
            .returns(JavaTypeName(method.returnType))
            .apply {
                for (parameter in method.parameters) {
                    addParameter(JavaTypeName(parameter.type), parameter.name)
                }
            }
        builder.addMethod(MethodSpecBuilderJavaImpl(methodBuilder).apply(block).build())
    }

    override fun nestedClass(name: ClassName, access: Access, isInner: Boolean, block: TypeSpecBuilder.() -> Unit) {
        val nestedClassBuilder = TypeSpec.classBuilder(JavaClassName(name))
            .addModifiers(Modifier.FINAL)
        when (access) {
            Access.Public -> nestedClassBuilder.addModifiers(Modifier.PUBLIC)
            Access.Private -> nestedClassBuilder.addModifiers(Modifier.PRIVATE)
            Access.Internal -> {}
        }
        if (!isInner) {
            nestedClassBuilder.addModifiers(Modifier.STATIC)
        }
        builder.addType(TypeSpecBuilderJavaImpl(nestedClassBuilder).apply(block).build())
    }
}