@file:JvmName("Standalone")

package com.yandex.yatagan.testing.procedural

import com.yandex.yatagan.testing.procedural.GenerationParams.BindingType
import com.yandex.yatagan.testing.procedural.GenerationParams.DependencyKind
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import java.io.File
import kotlin.system.exitProcess

@OptIn(ExperimentalGenerationApi::class)
fun main(args: Array<String>) {
    val parser = ArgParser("yatagan-graphs-generator")
    val projectRootPath by parser.option(ArgType.String, fullName = "output-dir", shortName = "o").required()
    val yataganPath by parser.option(ArgType.String, fullName = "yatagan-dir", shortName = "d").required()
    val forceGenerate by parser.option(ArgType.Boolean, fullName = "force-regenerate", shortName = "f").default(false)
    parser.parse(args)

    val projectRoot = File(projectRootPath)
    println("Project root: $projectRoot")
    if (projectRoot.exists()) {
        if (!forceGenerate) {
            System.err.println("Project root exists, use `--force-regenerate` flag")
            exitProcess(1)
        }
        println("Project root exists, deleting recursively")
        projectRoot.deleteRecursively()
        println("Done")
    }
    val yataganDir = File(yataganPath)
    if (!yataganDir.isDirectory) {
        System.err.println("yatagan project path `$yataganPath` is not an existing directory")
        exitProcess(2)
    }
    println("Creating project root")
    projectRoot.mkdirs()

    val params = GenerationParams(
        componentTreeMaxDepth = 8,
        totalGraphNodesCount = 200,
        bindings = Distribution.build {
            BindingType.Alias exactly 0.1
            BindingType.ComponentDependencyEntryPoint exactly 0.08
            BindingType.ComponentDependency exactly 0.0
            BindingType.Instance exactly 0.08
            theRestUniformly()
        },
        maxProvisionDependencies = 30,
        provisionDependencyKind = Distribution.build {
            DependencyKind.Direct exactly 0.8
            theRestUniformly()
        },
        maxEntryPointsPerComponent = 15,
        maxMemberInjectorsPerComponent = 3,
        maxMembersPerInjectee = 20,
        totalRootCount = 3,
        maxChildrenPerComponent = 5,

        totalRootScopes = 1,
        maxChildrenPerScope = 5,

        seed = 1L,
    )
    println("Generating sources with params: $params...")
    generate(
        params = params,
        sourceDir = projectRoot.resolve("src").resolve("main").resolve("kotlin"),
    )
    println("Generating sources done")

    println("Setting up project files...")
    projectRoot.resolve("build.gradle.kts").writeText("""
        plugins { 
            kotlin("jvm") version "1.6.10"
            id("application")
        }
        repositories {
            mavenCentral()
        }
        dependencies {
            implementation("javax.inject:javax.inject:1")
            implementation("org.mockito.kotlin:mockito-kotlin:4.0.0")
            implementation("com.yandex.yatagan:api-dynamic:0.2.4-rc1")
        }
        application {
            mainClassName = "test.TestCaseKt"
        }
    """.trimIndent())
    projectRoot.resolve("settings.gradle.kts").writeText("""
        pluginManagement {
            repositories {
                gradlePluginPortal()
            }
        }
        
        rootProject.name = "yatagan-performance-test"
        
        includeBuild("${yataganDir.absolutePath}")
    """.trimIndent())
    projectRoot.resolve("gradle.properties").writeText("""
        org.gradle.configureondemand=true
        org.gradle.parallel=true
        org.gradle.daemon=false
        
        org.gradle.jvmargs=-Xmx6G
    """.trimIndent())
    println("Project generation done!")

    ProcessBuilder().run {
        command(yataganDir.resolve("gradlew").absolutePath, "run")
        println("Assembling and running code via `${command()}`")
        directory(projectRoot)
        inheritIO()
        start()
    }.waitFor()
}