package com.example

import dagger.Binds
import dagger.Component
import dagger.Lazy
import dagger.Module
import dagger.Provides
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

interface SomeInterface {
    fun provide(): Int
}

class SomeImpl @Inject constructor() : SomeInterface {
    override fun provide() = 2
}

@Singleton
class SomeObj @Inject constructor(
    i: SomeInterface
) {

}

interface SomeInt2

@Module
interface TestModule {
    @Binds
    fun bind1(i: SomeImpl): SomeInterface

    @Provides
    @Named("hello")
    fun provide1(i: SomeInterface): SomeInt2 {
        return object : SomeInt2 {}
    }
}

@Component(
    modules = [
        TestModule::class,
    ]
)
interface TestComponent {
    val hello: SomeObj
    val someInt: SomeInterface
    val lazyHello: Lazy<SomeObj>

    @Component.Factory
    interface Factory {
        fun create(): Component
    }
}