package com.yandex.daggerlite.testing

interface CompileTestDriver : SourceSet {
    interface CompilationResultClause {
        fun withErrorContaining(message: String)
        fun withWarningContaining(message: String)
        fun withNoWarnings()
        fun withNoErrors()
        fun generatesJavaSources(name: String)
        fun inspectGeneratedClass(name: String, callback: (Class<*>) -> Unit)
    }

    fun givenSourceSet(block: SourceSet.() -> Unit): SourceSet
    fun useSourceSet(sources: SourceSet)

    fun failsToCompile(block: CompilationResultClause.() -> Unit)
    fun compilesSuccessfully(block: CompilationResultClause.() -> Unit)
}