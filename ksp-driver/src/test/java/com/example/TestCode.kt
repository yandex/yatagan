package com.example

import dagger.Binds
import dagger.BindsInstance
import dagger.Component
import dagger.Lazy
import dagger.Module
import dagger.Provides
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider
import javax.inject.Scope
import javax.inject.Singleton
import kotlin.test.Test

@Scope
annotation class SubScope

interface SomeInterface<T> {
    fun provide(): Int
}

class SomeImpl @Inject constructor() : SomeInterface<Unit> {
    override fun provide() = 2
}

@Singleton
class SomeObj<T> @Inject constructor(
    i: SomeInterface<T>
) {

}

interface SomeInt2

@Module(
    subcomponents = [SubComponent::class],
)
interface TestModule {
    @Binds
    fun bind1(i: SomeImpl): SomeInterface<Unit>
}

@Module
object SomeModule {
    @Provides
    @Named("hello")
    fun provide1(i: SomeInterface<Unit>): SomeInt2 {
        return object : SomeInt2 {}
    }
}

@SubScope
class SubScopedClass @Inject constructor(dep: SomeInterface<Unit>)

@SubScope
@Component(
    isRoot = false
)
interface SubComponent {
    val lazyHello: Lazy<SomeObj<Unit>>
    val clzz: SubScopedClass
    val inter: SomeInterface<Unit>

    @Component.Factory
    interface Factory {
        fun create(): SubComponent
    }
}

@Singleton
@Component(
    modules = [
        TestModule::class,
//        SomeModule::class,
    ]
)
interface TestComponent {
    val hello: SomeObj<Unit>
    val lazyHello: Lazy<SomeObj<Unit>>
    val helloProvider: Provider<SomeObj<Unit>>

    val someInt: SomeInterface<Unit>
    val someIntLazy: Lazy<SomeInterface<Unit>>
    val someIntProvider: Provider<SomeInterface<Unit>>

    val someIntImpl: SomeImpl
    val someIntImplLazy: Lazy<SomeImpl>
    val someIntImplProvider: Provider<SomeImpl>

    @Named("hello")
    val someInt2: SomeInt2
    @Named("hello")
    val pSomeInt2: Provider<SomeInt2>
    @Named("hello")
    val lSomeInt2: Lazy<SomeInt2>
    val subFactory: SubComponent.Factory

    @Component.Factory
    interface Factory {
        fun create(@BindsInstance @Named("hello") p: SomeInt2): TestComponent
    }
}

class BasicRuntimeTest {
    @Test
    fun `yanking entry points does not crash`() {
        val component: TestComponent = DaggerTestComponent.factory().create(object : SomeInt2 {})
        component.hello
        component.lazyHello
        component.someInt
    }
}