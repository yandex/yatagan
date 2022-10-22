import com.yandex.daggerlite.gradle.ClasspathSourceGeneratorTask

plugins {
    id("daggerlite.base-module")
}

val kotlinCompileTestingVersion: String by extra
val kspVersion: String by extra
val junitVersion: String by extra
val mockitoKotlinVersion: String by extra
val kotlinxCliVersion: String by extra
val javaPoetVersion: String by extra

val baseTestRuntime: Configuration by configurations.creating
val dynamicTestRuntime: Configuration by configurations.creating {
    extendsFrom(baseTestRuntime)
}
val dynamicOptimizedTestRuntime: Configuration by configurations.creating {
    extendsFrom(baseTestRuntime)
}
val compiledTestRuntime: Configuration by configurations.creating {
    extendsFrom(baseTestRuntime)
}

configurations.configureEach {
    resolutionStrategy {
        // Force KSP version as testing framework may depend on an older version.
        force("com.google.devtools.ksp:symbol-processing:$kspVersion")
        force("com.google.devtools.ksp:symbol-processing-api:$kspVersion")
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

dependencies {
    api(project(":testing:source-set"))

    // Third-party test dependencies
    implementation("junit:junit:$junitVersion")

    // Base test dependencies
    implementation(project(":processor:common"))
    implementation(project(":core:model:impl"))
    implementation(project(":core:graph:impl"))
    implementation(project(":api:public"))
    implementation(project(":base"))

    // KSP dependencies
    implementation(project(":lang:ksp"))
    implementation(project(":processor:ksp"))

    // JAP dependencies
    implementation(project(":lang:jap"))
    implementation(project(":processor:jap"))

    // RT dependencies
    implementation(project(":lang:rt"))
    implementation("com.squareup:javapoet:$javaPoetVersion")

    // Standalone launcher dependencies
    implementation("org.jetbrains.kotlinx:kotlinx-cli:$kotlinxCliVersion")

    // Heavy test dependencies
    testImplementation(project(":testing:procedural"))

    // For strings
    testImplementation(project(":validation:format"))

    baseTestRuntime("org.mockito.kotlin:mockito-kotlin:$mockitoKotlinVersion")  // required for heavy tests
    dynamicTestRuntime(project(":api:dynamic", configuration = "runtimeElements"))
    dynamicOptimizedTestRuntime(project(":api:dynamic", configuration = "optimizedRuntimeElements"))
    compiledTestRuntime(project(":api:compiled", configuration = "runtimeElements"))
}

val generateDynamicApiClasspath by tasks.registering(ClasspathSourceGeneratorTask::class) {
    packageName.set("com.yandex.daggerlite.generated")
    propertyName.set("DynamicApiClasspath")
    classpath.set(dynamicTestRuntime)
}

val generateDynamicOptimizedApiClasspath by tasks.registering(ClasspathSourceGeneratorTask::class) {
    packageName.set("com.yandex.daggerlite.generated")
    propertyName.set("DynamicOptimizedApiClasspath")
    classpath.set(dynamicOptimizedTestRuntime)
}

val generateCompiledApiClasspath by tasks.registering(ClasspathSourceGeneratorTask::class) {
    packageName.set("com.yandex.daggerlite.generated")
    propertyName.set("CompiledApiClasspath")
    classpath.set(compiledTestRuntime)
}

tasks.named("compileKotlin") {
    dependsOn(generateDynamicApiClasspath, generateDynamicOptimizedApiClasspath, generateCompiledApiClasspath)
}

val updateGoldenFiles by tasks.registering(Test::class) {
    group = "tools"
    description = "Launch tests in a special 'regenerate-golden' mode, where they are do not fail, " +
            "but write their *actual* results as *expected*. Use with care after you've changed some error-reporting " +
            "format and need to regenerate the actual results in batch"
    // Pass the resource directory absolute path
    systemProperty("com.yandex.daggerlite.updateGoldenFiles",
        sourceSets.test.get().resources.sourceDirectories.singleFile.absolutePath)
}

tasks.test {
    // Needed for "heavy" tests, as they compile a very large Kotlin project in-process.
    jvmArgs = listOf("-Xmx4G", "-Xms256m")
    shouldRunAfter(updateGoldenFiles)
}

kotlin {
    sourceSets {
        main {
            kotlin.srcDir(generateDynamicApiClasspath.map { it.generatedSourceDir })
            kotlin.srcDir(generateDynamicOptimizedApiClasspath.map { it.generatedSourceDir })
            kotlin.srcDir(generateCompiledApiClasspath.map { it.generatedSourceDir })
        }
    }
}
