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
    implementation("com.github.tschuchortdev:kotlin-compile-testing-ksp:$kotlinCompileTestingVersion")
    implementation("junit:junit:$junitVersion")

    // Base test dependencies
    implementation(project(":processor"))
    implementation(project(":validation-impl"))
    implementation(project(":core-impl"))
    implementation(project(":graph-impl"))
    implementation(project(":api"))
    implementation(project(":base"))

    // KSP dependencies
    implementation(project(":lang-ksp"))
    implementation(project(":processor-ksp"))

    // JAP dependencies
    implementation(project(":lang-jap"))
    implementation(project(":processor-jap"))

    // RT dependencies
    implementation(project(":lang-rt"))

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
