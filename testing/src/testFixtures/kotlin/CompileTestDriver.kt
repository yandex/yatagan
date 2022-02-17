package com.yandex.daggerlite.testing

interface CompileTestDriver : SourceSet {
    interface CompilationResultClause {
        fun withError(message: String)
        fun withWarning(message: String)
        fun withNoWarnings()
        fun withNoErrors()
        fun generatesJavaSources(name: String)
        fun inspectGeneratedClass(name: String, callback: (Class<*>) -> Unit)
    }

    fun givenSourceSet(block: SourceSet.() -> Unit): SourceSet
    fun useSourceSet(sources: SourceSet)

    fun precompile(sources: SourceSet)

    fun failsToCompile(block: CompilationResultClause.() -> Unit)
    fun compilesSuccessfully(block: CompilationResultClause.() -> Unit)

    val backendUnderTest: Backend

    enum class Backend {
        Jap,
        Ksp,
    }
}

fun CompileTestDriver.CompilationResultClause.withNoMoreErrors() = withNoErrors()
fun CompileTestDriver.CompilationResultClause.withNoMoreWarnings() = withNoWarnings()