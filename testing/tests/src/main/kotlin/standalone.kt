@file:JvmName("Standalone")
package com.yandex.yatagan.testing.tests

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import java.io.File
import kotlin.system.exitProcess

private const val TAG = "[Standalone-Runner]"

fun main(args: Array<String>) {
    val parser = ArgParser("yatagan-testing")
    val backend: Backend by parser.option(
        type = ArgType.Choice<Backend>(),
        fullName = "backend",
        description = """
            What Yatagan backend to use for running test code.
        """.trimIndent(),
    ).required()
    val testCasesDir: String by parser.option(
        type = ArgType.String,
        fullName = "test-cases-dir",
        description = """
            One or more paths to source directories, containing Java and/or Kotlin sources.
            Each source directory will be compiled as a separate test-case.
        """.trimIndent(),
    ).required()
    parser.parse(args)

    val dir = File(testCasesDir)
    val testCaseDirs = dir.listFiles() ?: emptyArray()
    if (testCaseDirs.isEmpty()) {
        println("$TAG No tests")
        return
    } else {
        println("$TAG Discovered ${testCaseDirs.size} test cases")
    }

    println("$TAG Using $backend backend to run tests")

    testCaseDirs.forEach { testCaseDir ->
        if (!testCaseDir.isDirectory) {
            System.err.println("Directory `$testCaseDir` does not exist/not a directory")
            exitProcess(2)
        }
        with(when (backend) {
            Backend.Kapt -> JapCompileTestDriver()
            Backend.Ksp -> KspCompileTestDriver()
            Backend.Rt -> DynamicCompileTestDriver()
        }) {
            testCaseDir.walk()
                .filter { it.isFile}
                .forEach {
                    when(it.extension) {
                        "java" -> {
                            val packageName = it.relativeTo(testCaseDir)
                                .parentFile.path.replace(File.pathSeparatorChar, '.')
                            val name = if (packageName.isNotEmpty()) {
                                "$packageName.${it.nameWithoutExtension}"
                            } else {
                                it.nameWithoutExtension
                            }
                            givenJavaSource(
                                name = name,
                                source = it.readText(),
                                addPackageDirective = false,
                            )
                        }
                        "kt" -> {
                            givenKotlinSource(
                                name = it.nameWithoutExtension,
                                source = it.readText(),
                                addPackageDirective = false,
                            )
                        }
                    }
                }
            println("$TAG Running test from $testCaseDir")
            try {
                compileRunAndValidate()
                println("$TAG OK")
            } catch (e: Exception) {
                System.err.println("$TAG Validation failure for ${testCaseDir.name}: ${e.message}")
                exitProcess(3)
            }
        }
    }
}