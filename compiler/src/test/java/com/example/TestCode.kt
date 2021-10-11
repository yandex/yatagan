package com.example

import dagger.Binds
import dagger.Component
import dagger.Lazy
import dagger.Module
import dagger.Provides
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.test.Test

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

@Module
interface TestModule {
    @Binds
    fun bind1(i: SomeImpl): SomeInterface<Unit>

    @Provides
    @Named("hello")
    fun provide1(i: SomeInterface<Unit>): SomeInt2 {
        return object : SomeInt2 {}
    }
}

@Component(
    modules = [
        TestModule::class,
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

    @Component.Factory
    interface Factory {
        fun create(): TestComponent
    }
}

class Test {
    @Test
    fun `general test`() {
        val component: TestComponent = DaggerTestComponent()
        component.hello
        component.lazyHello
        component.someInt
    }
}