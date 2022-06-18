package com.yandex.daggerlite.jap

import java.io.Writer
import javax.annotation.processing.Filer
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.ExecutableType
import javax.lang.model.type.PrimitiveType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.JavaFileManager

/**
 * Global lock for javax.lang.model.* environment, as it's not required to be thread-safe by itself.
 * All the objects obtained from the environment are, supposedly, safe to use in multithreaded environment.
 */
private val Javax = Any()

// @formatter:off
@Suppress("HasPlatformType")
internal class ThreadSafeFiler(
    private val f: Filer,
) : Filer {
    override fun createSourceFile(p0: CharSequence?, vararg p1: Element?) = synchronized(Javax) { f.createSourceFile(p0, *p1) }
    override fun createClassFile(p0: CharSequence?, vararg p1: Element?) = synchronized(Javax) { f.createClassFile(p0, *p1) }
    override fun createResource(p0: JavaFileManager.Location?, p1: CharSequence?, p2: CharSequence?, vararg p3: Element?) = synchronized(Javax) { f.createResource(p0, p1, p2, *p3) }
    override fun getResource(p0: JavaFileManager.Location?, p1: CharSequence?, p2: CharSequence?) = synchronized(Javax) { f.getResource(p0, p1, p2) }
}

@Suppress("HasPlatformType", "UsePropertyAccessSyntax")
internal class ThreadSafeTypes(
    private val t: Types,
) : Types {
    override fun asElement(p0: TypeMirror?) = synchronized(Javax) { t.asElement(p0) }
    override fun isSameType(p0: TypeMirror?, p1: TypeMirror?) = synchronized(Javax) { t.isSameType(p0, p1) }
    override fun isSubtype(p0: TypeMirror?, p1: TypeMirror?) = synchronized(Javax) { t.isSubtype(p0, p1) }
    override fun isAssignable(p0: TypeMirror?, p1: TypeMirror?) = synchronized(Javax) { t.isAssignable(p0, p1) }
    override fun contains(p0: TypeMirror?, p1: TypeMirror?)= synchronized(Javax) { t.contains(p0, p1) }
    override fun isSubsignature(p0: ExecutableType?, p1: ExecutableType?) = synchronized(Javax) { t.isSubsignature(p0, p1) }
    override fun directSupertypes(p0: TypeMirror?) = synchronized(Javax) { t.directSupertypes(p0) }
    override fun erasure(p0: TypeMirror?) = synchronized(Javax) { t.erasure(p0) }
    override fun boxedClass(p0: PrimitiveType?) = synchronized(Javax) { t.boxedClass(p0) }
    override fun unboxedType(p0: TypeMirror?) = synchronized(Javax) { t.unboxedType(p0) }
    override fun capture(p0: TypeMirror?) = synchronized(Javax) { t.capture(p0) }
    override fun getPrimitiveType(p0: TypeKind?) = synchronized(Javax) { t.getPrimitiveType(p0) }
    override fun getNullType() = synchronized(Javax) { t.getNullType() }
    override fun getNoType(p0: TypeKind?) = synchronized(Javax) { t.getNoType(p0) }
    override fun getArrayType(p0: TypeMirror?) = synchronized(Javax) { t.getArrayType(p0) }
    override fun getWildcardType(p0: TypeMirror?, p1: TypeMirror?) = synchronized(Javax) { t.getWildcardType(p0, p1) }
    override fun getDeclaredType(p0: TypeElement?, vararg p1: TypeMirror?) = synchronized(Javax) { t.getDeclaredType(p0, *p1) }
    override fun getDeclaredType(p0: DeclaredType?, p1: TypeElement?, vararg p2: TypeMirror?) = synchronized(Javax) { t.getDeclaredType(p0, p1, *p2) }
    override fun asMemberOf(p0: DeclaredType?, p1: Element?) = synchronized(Javax) { t.asMemberOf(p0, p1) }
}


@Suppress("HasPlatformType")
internal class ThreadSafeElements(
    private val i: Elements,
) : Elements by i /* delegation is required to support methods for JDK 9+ */ {
    override fun getPackageElement(p0: CharSequence?) = synchronized(Javax) { i.getPackageElement(p0) }
    override fun getTypeElement(p0: CharSequence?) = synchronized(Javax) { i.getTypeElement(p0) }
    override fun getElementValuesWithDefaults(p0: AnnotationMirror?) = synchronized(Javax) { i.getElementValuesWithDefaults(p0) }
    override fun getDocComment(p0: Element?) = synchronized(Javax) { i.getDocComment(p0) }
    override fun isDeprecated(p0: Element?) = synchronized(Javax) { i.isDeprecated(p0) }
    override fun getBinaryName(p0: TypeElement?) = synchronized(Javax) { i.getBinaryName(p0) }
    override fun getPackageOf(p0: Element?) = synchronized(Javax) { i.getPackageOf(p0) }
    override fun getAllMembers(p0: TypeElement?) = synchronized(Javax) { i.getAllMembers(p0) }
    override fun getAllAnnotationMirrors(p0: Element?) = synchronized(Javax) { i.getAllAnnotationMirrors(p0) }
    override fun hides(p0: Element?, p1: Element?) = synchronized(Javax) { i.hides(p0, p1) }
    override fun overrides(p0: ExecutableElement?, p1: ExecutableElement?, p2: TypeElement?) = synchronized(Javax) { i.overrides(p0, p1, p2) }
    override fun getConstantExpression(p0: Any?) = synchronized(Javax) { i.getConstantExpression(p0) }
    override fun printElements(p0: Writer?, vararg p1: Element?) = synchronized(Javax) { i.printElements(p0, *p1) }
    override fun getName(p0: CharSequence?) = synchronized(Javax) { i.getName(p0) }
    override fun isFunctionalInterface(p0: TypeElement?) = synchronized(Javax) { i.isFunctionalInterface(p0) }
}
// @formatter:on