import com.yandex.daggerlite.gradle.ClasspathSourceGeneratorTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("daggerlite.base-module")
    id("java-test-fixtures")
}

val kotlinCompileTestingVersion: String by extra
val kspVersion: String by extra

val baseTestRuntime: Configuration by configurations.creating
val dynamicTestRuntime: Configuration by configurations.creating {
    extendsFrom(baseTestRuntime)
}
val compiledTestRuntime: Configuration by configurations.creating {
    extendsFrom(baseTestRuntime)
}

val generatedSourceDir: Provider<Directory> = project.layout.buildDirectory.dir("generated-sources")

configurations.all {
    resolutionStrategy {
        // Force KSP version as testing framework may depend on an older version.
        force("com.google.devtools.ksp:symbol-processing:$kspVersion")
        force("com.google.devtools.ksp:symbol-processing-api:$kspVersion")
    }
}

dependencies {
    testFixturesApi(project(":api"))
    testFixturesApi("com.github.tschuchortdev:kotlin-compile-testing-ksp:$kotlinCompileTestingVersion")
    testFixturesImplementation(project(":processor"))
    testFixturesImplementation(project(":base"))
    testFixturesImplementation(project(":validation-impl"))
    testFixturesImplementation(kotlin("test"))

    testImplementation(testFixtures(project(":processor-ksp")))
    testImplementation(testFixtures(project(":processor-jap")))
    testImplementation(kotlin("test"))
    testImplementation(project(":validation-impl"))
    testImplementation(project(":lang-ksp"))
    testImplementation(project(":lang-jap"))

    baseTestRuntime(kotlin("test"))
    dynamicTestRuntime(project(":api-dynamic"))
    compiledTestRuntime(project(":api-compiled"))
}

kotlin {
    sourceSets {
        testFixtures {
            kotlin.srcDir(generatedSourceDir)
        }
    }
}

tasks {
    val dynamicApiClasspathTask = register<ClasspathSourceGeneratorTask>("generateDynamicApiClasspath") {
        packageName = "com.yandex.daggerlite.generated"
        propertyName = "DynamicApiClasspath"
        classpath = dynamicTestRuntime
        generatedSource = generatedSourceDir.map { it.file("dynamicClasspath.kt") }
    }

    val compiledApiClasspathTask = register<ClasspathSourceGeneratorTask>("generateCompiledApiClasspath") {
        packageName = "com.yandex.daggerlite.generated"
        propertyName = "CompiledApiClasspath"
        classpath = compiledTestRuntime
        generatedSource = generatedSourceDir.map { it.file("compiledClasspath.kt") }
    }

    withType<KotlinCompile>().configureEach {
        dependsOn(dynamicApiClasspathTask, compiledApiClasspathTask)
    }
}
