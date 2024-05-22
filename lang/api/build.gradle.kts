import com.yandex.yatagan.gradle.ClasspathSourceGeneratorTask

plugins {
    id("yatagan.stable-api-artifact")
}

val stdLib: Configuration by configurations.creating

dependencies {
    api(project(":base:api"))

    testImplementation(project(":base:impl"))
    testImplementation(project(":lang:jap"))
    testImplementation(project(":lang:ksp"))
    testImplementation(project(":lang:rt"))
    testImplementation(project(":testing:source-set"))

    testImplementation(libs.ksp.api)
    testImplementation(libs.testing.junit4)
    testImplementation(libs.testing.roomCompileTesting)
    testImplementation(libs.testing.assertj)

    stdLib(kotlin("stdlib"))
}

val generateStdLibClasspath by tasks.registering(ClasspathSourceGeneratorTask::class) {
    packageName.set("com.yandex.yatagan.lang")
    groups.register("GeneratedClasspath") {
        properties.register("StdLib") {
            classpath = stdLib
        }
    }
}

kotlin {
    sourceSets {
        test {
            kotlin.srcDir(generateStdLibClasspath.map { it.generatedSourceDir })
        }
    }
}
