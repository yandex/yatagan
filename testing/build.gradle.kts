import com.yandex.daggerlite.gradle.ClasspathSourceGeneratorTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("daggerlite.base-module")
}

val kotlinCompileTestingVersion: String by extra
val kspVersion: String by extra
val junitVersion: String by extra
val mockitoKotlinVersion: String by extra

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
    // Third-party test dependencies
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing-ksp:$kotlinCompileTestingVersion")
    testImplementation("junit:junit:$junitVersion")

    // Base test dependencies
    testImplementation(project(":processor"))
    testImplementation(project(":validation-impl"))
    testImplementation(project(":core-impl"))
    testImplementation(project(":graph-impl"))
    testImplementation(project(":api"))
    testImplementation(project(":base"))

    // KSP dependencies
    testImplementation(project(":lang-ksp"))
    testImplementation(project(":processor-ksp"))

    // JAP dependencies
    testImplementation(project(":lang-jap"))
    testImplementation(project(":processor-jap"))

    // RT dependencies
    testImplementation(project(":lang-rt"))

    // Heavy test dependencies
    testImplementation(project(":testing-generator"))

    baseTestRuntime("org.mockito.kotlin:mockito-kotlin:$mockitoKotlinVersion")  // required for heavy tests
    dynamicTestRuntime(project(":api-dynamic"))
    compiledTestRuntime(project(":api-compiled"))
}

kotlin {
    sourceSets {
        test {
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

    test {
        // Needed for "heavy" tests, as they compile a very large Kotlin project in-process.
        jvmArgs = listOf("-Xmx4G", "-Xms256m")
    }
}
