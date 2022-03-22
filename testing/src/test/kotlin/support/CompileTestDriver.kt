package com.yandex.daggerlite.testing.support

interface CompileTestDriver : SourceSet {
    fun givenPrecompiledModule(
        sources: SourceSet,
    )

    fun expectValidationResults(
        vararg messages: Message,
    )

    val backendUnderTest: Backend

    fun expectSuccessfulValidation() = expectValidationResults(/*none*/)
}

enum class Backend {
    Kapt,
    Ksp,
    Rt,
}

enum class MessageKind {
    Warning,
    Error,
}

data class Message(
    val kind: MessageKind,
    val text: String,
) : Comparable<Message> {
    override fun compareTo(other: Message): Int {
        kind.compareTo(other.kind).let {
            if (it != 0) return it
        }
        text.compareTo(other.text).let {
            if (it != 0) return it
        }
        return 0
    }
}

fun errorMessage(text: String) = Message(kind = MessageKind.Error, text = text)

fun warningMessage(text: String) = Message(kind = MessageKind.Warning, text = text)