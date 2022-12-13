import com.yandex.yatagan.gradle.ClasspathSourceGeneratorTask

plugins {
    id("yatagan.artifact")
}

val stdLib: Configuration by configurations.creating

dependencies {
    testImplementation(project(":base"))
    testImplementation(project(":lang:jap"))
    testImplementation(project(":lang:ksp"))
    testImplementation(project(":lang:rt"))
    testImplementation(project(":testing:source-set"))

    testImplementation(libs.ksp.api)
    testImplementation(testingLibs.junit4)
    testImplementation(testingLibs.roomCompileTesting)
    testImplementation(testingLibs.assertj)

    stdLib(kotlin("stdlib"))
}

kotlin {
    explicitApi()
}

val generateStdLibClasspath by tasks.registering(ClasspathSourceGeneratorTask::class) {
    packageName.set("com.yandex.yatagan.lang")
    propertyName.set("StdLibClasspath")
    classpath.set(stdLib)
}

kotlin {
    sourceSets {
        test {
            kotlin.srcDir(generateStdLibClasspath.map { it.generatedSourceDir })
        }
    }
}
