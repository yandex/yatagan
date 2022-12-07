import com.yandex.yatagan.gradle.ClasspathSourceGeneratorTask

plugins {
    id("yatagan.base-module")
}

val baseTestRuntime: Configuration by configurations.creating
val dynamicTestRuntime: Configuration by configurations.creating {
    extendsFrom(baseTestRuntime)
}
val compiledTestRuntime: Configuration by configurations.creating {
    extendsFrom(baseTestRuntime)
}

configurations.configureEach {
    resolutionStrategy {
        // Force KSP version as testing framework may depend on an older version.
        force(libs.ksp.impl)
        force(libs.ksp.api)
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
    implementation(testingLibs.junit4)
    implementation(testingLibs.roomCompileTesting)

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
    implementation(libs.poets.java)

    // Heavy test dependencies
    testImplementation(project(":testing:procedural"))

    // For strings
    testImplementation(project(":validation:format"))

    baseTestRuntime(testingLibs.mockito.kotlin.get())  // required for heavy tests
    dynamicTestRuntime(project(":api:dynamic"))
    compiledTestRuntime(project(":api:compiled"))
}

val generateDynamicApiClasspath by tasks.registering(ClasspathSourceGeneratorTask::class) {
    packageName.set("com.yandex.yatagan.generated")
    propertyName.set("DynamicApiClasspath")
    classpath.set(dynamicTestRuntime)
}

val generateCompiledApiClasspath by tasks.registering(ClasspathSourceGeneratorTask::class) {
    packageName.set("com.yandex.yatagan.generated")
    propertyName.set("CompiledApiClasspath")
    classpath.set(compiledTestRuntime)
}

tasks.named("compileKotlin") {
    dependsOn(generateDynamicApiClasspath, generateCompiledApiClasspath)
}

val updateGoldenFiles by tasks.registering(Test::class) {
    group = "tools"
    description = "Launch tests in a special 'regenerate-golden' mode, where they are do not fail, " +
            "but write their *actual* results as *expected*. Use with care after you've changed some error-reporting " +
            "format and need to regenerate the actual results in batch"
    // Pass the resource directory absolute path
    systemProperty("com.yandex.yatagan.updateGoldenFiles",
        sourceSets.test.get().resources.sourceDirectories.singleFile.absolutePath)
}

tasks.test {
    // Needed for "heavy" tests, as they compile a very large Kotlin project in-process.
    jvmArgs = listOf("-Xmx4G", "-Xms256m")
    shouldRunAfter(updateGoldenFiles)

    // Increasing this will likely get a negative effect on tests performance as kotlin-compilation seems to be shared
    // between compilation invocations and I still haven't found a way to make it in-process.
    maxParallelForks = 2
}

kotlin {
    sourceSets {
        main {
            kotlin.srcDir(generateDynamicApiClasspath.map { it.generatedSourceDir })
            kotlin.srcDir(generateCompiledApiClasspath.map { it.generatedSourceDir })
        }
    }
}
