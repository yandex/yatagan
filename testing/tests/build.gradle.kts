import com.yandex.yatagan.gradle.ClasspathSourceGeneratorTask

plugins {
    id("yatagan.base-module")
}

val enableCoverage: Boolean by extra

val versionsToCheckLoaderCompatibility = listOf(
    "1.0.0",
    "1.1.0",
    "1.2.0",
    "1.3.0",
    "1.5.0",
)

val baseTestRuntime by configurations.registering
val dynamicTestRuntime by configurations.registering {
    extendsFrom(baseTestRuntime.get())
}
val compiledTestRuntime by configurations.registering {
    extendsFrom(baseTestRuntime.get())
}

val daggerApi by configurations.registering {
    extendsFrom(compiledTestRuntime.get())
}
val daggerProcessor by configurations.registering

versionsToCheckLoaderCompatibility.forEach { version ->
    configurations.register("kaptForCompatCheck$version")
    configurations.register("apiForCompatCheck$version")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

@Suppress("UnstableApiUsage")
dependencies {
    api(project(":testing:source-set"))

    // Third-party test dependencies
    implementation(libs.testing.junit4)
    implementation(libs.testing.roomCompileTesting)

    // Base test dependencies
    implementation(project(":processor:common"))
    implementation(project(":core:model:impl"))
    implementation(project(":core:graph:impl"))
    implementation(project(":api:public"))
    implementation(project(":base:impl"))

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

    dynamicTestRuntime(project(":api:dynamic"))
    compiledTestRuntime(project(":api:public"))

    daggerApi(libs.testing.dagger.api)
    daggerProcessor(libs.testing.dagger.processor)
    daggerApi(kotlin("stdlib"))

    versionsToCheckLoaderCompatibility.forEach { version ->
        add("kaptForCompatCheck$version", "com.yandex.yatagan:processor-jap:$version")
        add("apiForCompatCheck$version", if (version < "2.0.0")
            "com.yandex.yatagan:api-compiled:$version" else "com.yandex.yatagan:api-public:$version")
    }
}

val generateClasspathProperties by tasks.registering(ClasspathSourceGeneratorTask::class) {
    packageName.set("com.yandex.yatagan.generated")
    groups {
        register("ClasspathForCompatCheck") {
            properties {
                versionsToCheckLoaderCompatibility.forEach { version ->
                    val versionId = version.replace("[.-]".toRegex(), "_")
                    register("Kapt$versionId") {
                        classpath = configurations.named("kaptForCompatCheck$version")
                    }
                    register("Api$versionId") {
                        classpath = configurations.named("apiForCompatCheck$version")
                    }
                }
            }
        }
        register("CurrentClasspath") {
            properties {
                register("ApiDynamic") { classpath = dynamicTestRuntime }
                register("ApiCompiled") { classpath = compiledTestRuntime }
            }
        }
        register("DaggerClasspath") {
            properties {
                register("Api") { classpath = daggerApi }
                register("Processor") { classpath = daggerProcessor }
            }
        }
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateClasspathProperties)
}

val updateGoldenFiles by tasks.registering(Test::class) {
    group = "tools"
    description = "Launch tests in a special 'regenerate-golden' mode, where they are do not fail, " +
            "but write their *actual* results as *expected*. Use with care after you've changed some error-reporting " +
            "format and need to regenerate the actual results in batch"
    // Pass the resource directory absolute path
    systemProperty("com.yandex.yatagan.updateGoldenFiles",
        sourceSets.test.get().resources.sourceDirectories.singleFile.absolutePath)

    // Kover runs all the `Test` tasks, but we don't need it to run this one, so explicitly disable it.
    enabled = !enableCoverage
}

tasks.test {
    // Needed for "heavy" tests, as they compile a very large Kotlin project in-process.
    shouldRunAfter(updateGoldenFiles)

    // Increasing this will likely get a negative effect on tests performance as kotlin-compilation seems to be shared
    // between compilation invocations and I still haven't found a way to make it in-process.
    maxParallelForks = 1
}

kotlin {
    sourceSets {
        main {
            kotlin.srcDir(generateClasspathProperties.map { it.generatedSourceDir })
        }
    }
}
