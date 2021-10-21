plugins {
    kotlin("jvm")
    id("java-test-fixtures")
    id("com.google.devtools.ksp")
}

group = project.ext["group"] as String
version = project.ext["version"] as String

kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).set11Jdk()
    }

    sourceSets.test {
        kotlin.srcDir("${project.buildDir}/generated/ksp/test/kotlin")
    }
}

java {
    toolchain {
        set11Jdk()
    }
    sourceSets.test {
        java.srcDir("${project.buildDir}/generated/ksp/test/java")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":generator"))

    implementation(kotlin("stdlib"))
    implementation("com.google.devtools.ksp:symbol-processing-api:${Versions.Ksp}")

    testFixturesImplementation(testFixtures(project(":testing")))
    testFixturesImplementation(kotlin("test"))
}