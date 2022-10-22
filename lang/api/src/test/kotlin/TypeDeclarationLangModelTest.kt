package com.yandex.daggerlite.lang

import org.assertj.core.api.Assertions.assertThat
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class TypeDeclarationLangModelTest(
    driverProvider: () -> LangTestDriver,
) : LangTestDriver by driverProvider() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() = LangTestDriver.all()
    }

    @Test
    fun `test kind`() {
        givenKotlinSource("Source", """
            interface I {
                fun forTest1(): Byte
                fun forTest2(): Char
                fun forTest3(): Double
                fun forTest4(): Float
                fun forTest5(): Int
                fun forTest6(): Long
                fun forTest7(): Short
                fun forTest8(): Boolean
                fun forTest9()
                fun forTest10(): Array<Byte>
                fun forTest11(): Array<Int>
                fun forTest12(): Array<Long>
                fun forTest13(): Array<Short>
                fun forTest14(): Array<Float>
                fun forTest15(): Array<Double>
                fun forTest16(): Array<Char>
                fun forTest17(): Array<Boolean>
                fun forTest18(): ByteArray
                fun forTest19(): IntArray
                fun forTest20(): LongArray
                fun forTest21(): ShortArray
                fun forTest22(): FloatArray
                fun forTest23(): DoubleArray
                fun forTest24(): CharArray
                fun forTest25(): BooleanArray
            }
            class C { companion object }
            enum class E
            annotation class A
            object O
        """.trimIndent())

        inspect {
            softAssertions {
                assertThat(declaration("I").kind).isEqualTo(TypeDeclarationKind.Interface)
                assertThat(declaration("C").kind).isEqualTo(TypeDeclarationKind.Class)
                assertThat(declaration("C", "Companion").kind).isEqualTo(TypeDeclarationKind.KotlinCompanion)
                assertThat(declaration("E").kind).isEqualTo(TypeDeclarationKind.Enum)
                assertThat(declaration("A").kind).isEqualTo(TypeDeclarationKind.Annotation)
                assertThat(declaration("O").kind).isEqualTo(TypeDeclarationKind.KotlinObject)

                for (function in declaration("I").functions) {
                    if ("forTest" !in function.name)
                        continue
                    assertThat(function.returnType.declaration.kind)
                        .`as`(function.name)
                        .isEqualTo(TypeDeclarationKind.None)
                }
            }
        }
    }

    @Test
    fun `test isAbstract`() {
        givenKotlinSource("Source", """
            interface I
            abstract class AC
            class C { companion object }
            open class OC
            object O
            annotation class A
            enum class E { EC }
        """.trimIndent())

        inspect {
            softAssertions {
                assertThat(declaration("I").isAbstract).`as`("interface").isTrue
                assertThat(declaration("AC").isAbstract).`as`("abstract class").isTrue
                assertThat(declaration("C").isAbstract).`as`("class").isFalse
                assertThat(declaration("C", "Companion").isAbstract).`as`("companion").isFalse
                assertThat(declaration("OC").isAbstract).`as`("open class").isFalse
                assertThat(declaration("O").isAbstract).`as`("object").isFalse
                assertThat(declaration("A").isAbstract).`as`("annotation").isFalse
                assertThat(declaration("E").isAbstract).`as`("enum").isFalse
            }
        }
    }

    @Test
    fun `test qualifiedName`() {
        givenKotlinSource("com.example.Source", """
            interface I1<out T>
            interface I2<in T>
            class TopLevel { class Nested { class Nested2 } }
            interface I {
                fun forTest(
                    // Declared platform types
                    i1: String,
                    i2: StringBuilder,
                    i3: Cloneable,

                    // Functional types
                    i4: () -> Unit,
                    i5: String.() -> Int,
                    i6: String.(Long, Long) -> ByteArray,
                    // i6_1: suspend String.(Long, Long) -> ByteArray, TODO: KSP gets this wrong

                    // Primitive
                    i7: Byte, i8: Char, i9: Double, i10: Float, i11: Int, i12: Long, i13: Short, i14: Boolean,

                    // Primitive arrays
                    i15: ByteArray, i16: CharArray, i17: DoubleArray, i18: FloatArray,
                    i19: IntArray, i20: LongArray, i21: ShortArray, i22: BooleanArray,

                    i23: Array<String>,
                    i24: Array<Any>,
                    i25: Array<*>,
                    i26: I1<I>,
                    i27: I2<I>,

                    // Array of boxed
                    i28: Array<Int>,

                    i30: TopLevel.Nested.Nested2,
                )
            }
        """.trimIndent())

        inspect {
            val function = declaration("I", packageName = "com.example")
                .functions.find { it.name == "forTest" }
            assumeNotNull(function)
            assertThat(function!!.parameters.map {
                it.type.declaration.qualifiedName
            }.toList()).containsExactly(
                "java.lang.String",
                "java.lang.StringBuilder",
                "java.lang.Cloneable",
                "kotlin.jvm.functions.Function0",
                "kotlin.jvm.functions.Function1",
                "kotlin.jvm.functions.Function3",
                "byte", "char", "double", "float", "int", "long", "short", "boolean",
                "byte[]", "char[]", "double[]", "float[]", "int[]", "long[]", "short[]", "boolean[]",
                "java.lang.String[]",
                "java.lang.Object[]",
                "java.lang.Object[]",
                "com.example.I1",
                "com.example.I2",
                "java.lang.Integer[]",
                "com.example.TopLevel.Nested.Nested2",
            )
        }
    }

    @Test
    fun `test enclosingType + nestedClasses`() {
        // TODO: test inner classes with generics
        // TODO: test enums
        givenKotlinSource("Source", """
            class T {
                inner class N1
                interface N {
                    class N2
                }
                annotation class A {
                    class N3
                }
            }
        """.trimIndent())

        inspect {
            val t = declaration("T")
            val n = declaration("T", "N")
            val n1 = declaration("T", "N1")
            val n2 = declaration("T", "N", "N2")
            val a = declaration("T", "A")
            val n3 = declaration("T", "A", "N3")

            softAssertions {
                assertThat(t.nestedClasses.toList())
                    .containsOnly(n, a, n1)

                assertThat(n.enclosingType,).isEqualTo(t)
                assertThat(n1.enclosingType).isEqualTo(t)
                assertThat(a.enclosingType).isEqualTo(t)

                assertThat(n.nestedClasses.toList()).containsOnly(n2)
                assertThat(n2.enclosingType).isEqualTo(n)

                assertThat(a.nestedClasses.toList()).containsOnly(n3)
                assertThat(n3.enclosingType).isEqualTo(a)

                assertThat(n1.nestedClasses.toList()).isEmpty()
                assertThat(n2.nestedClasses.toList()).isEmpty()
                assertThat(n3.nestedClasses.toList()).isEmpty()
            }
        }
    }

    @Test
    fun `test interfaces`() {
        givenKotlinSource("Source", """
            interface I1
            interface I2 : I1
            interface I3 : I1
            interface I4
            open class B : I3
            class A : I2, I1, I4, B()
        """.trimIndent())

        inspect {
            softAssertions {
                assertThat(declaration("A").interfaces.map(Any::toString).toList())
                    .containsExactly("I2", "I1", "I4")
                assertThat(declaration("B").interfaces.map(Any::toString).toList())
                    .containsExactly("I3")
                assertThat(declaration("I3").interfaces.map(Any::toString).toList())
                    .containsExactly("I1")
                assertThat(declaration("I1").interfaces.map(Any::toString).toList())
                    .isEmpty()
            }
        }
    }

    @Test
    fun `test interfaces - generics`() {
        givenKotlinSource("Source", """
            class C1<E1, E2>; class C2; open class C3
            interface I1<T1, T2>
            interface I2<T1> : I1<C1<Array<T1>, C3>, T1>
            interface I3 : I2<C2>

            interface I4<T>
            
            class A : I3
            class B : I2<I4<out C3>>
        """.trimIndent())

        inspect {
            softAssertions {
                val a = declaration("A")
                val i3 = a.interfaces.single()
                val i2 = i3.declaration.interfaces.single()
                val i1 = i2.declaration.interfaces.single()
                assertThat(i3.toString()).isEqualTo("I3")
                assertThat(i2.toString()).isEqualTo("I2<C2>")
                assertThat(i1.toString()).isEqualTo("I1<C1<C2[], C3>, C2>")

                val b = declaration("B")
                val bI2 = b.interfaces.single()
                val bI4 = bI2.declaration.interfaces.single()
                assertThat(bI2.toString()).isEqualTo("I2<I4<? extends C3>>")
                assertThat(bI4.toString()).isEqualTo("I1<C1<I4<? extends C3>[], C3>, I4<? extends C3>>")
            }
        }
    }

    @Test
    fun `test superclass`() {
        givenKotlinSource("Source", """
            interface I1
            abstract class B2<T> : I1
            abstract class B1<T> : B2<Array<out T>>(), I1

            class A : B1<I1>(), I1
        """.trimIndent())

        inspect {
            softAssertions {
                val superOfA = declaration("A").superType
                val superOfB1 = superOfA?.declaration?.superType
                val superOfB2 = superOfB1?.declaration?.superType
                assertThat(superOfA.toString()).isEqualTo("B1<I1>")
                assertThat(superOfB1.toString()).isEqualTo("B2<I1[]>")
                assertThat(superOfB2).isNull()

                assertThat(declaration("I1").superType).isNull()
            }
        }
    }

    @Test
    fun `test constructors`() {
        givenKotlinSource("Source", """
            annotation class A
            interface I
            abstract class AC
            class C
            class C1 internal constructor()
            open class C2 protected constructor()
            class C3 private constructor()
        """.trimIndent())

        inspect {
            softAssertions {
                assertThat(declaration("A").constructors.toList()).isEmpty()
                assertThat(declaration("I").constructors.toList()).isEmpty()
                assertThat(declaration("C3").constructors.toList()).isEmpty()

                assertThat(declaration("AC").constructors.map(Any::toString).toList())
                    .containsExactly("AC()")
                assertThat(declaration("C").constructors.map(Any::toString).toList())
                    .containsExactly("C()")
                assertThat(declaration("C1").constructors.map(Any::toString).toList())
                    .containsExactly("C1()")
                assertThat(declaration("C2").constructors.map(Any::toString).toList())
                    .containsExactly("C2()")
            }
        }
    }

    // TODO: Write non-declaration test for types with None-declaration and assert all at once
}
