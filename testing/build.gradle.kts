plugins {
    kotlin("jvm")
    id("java-test-fixtures")
}

kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).set11Jdk()
    }
}

java {
    toolchain {
        set11Jdk()
    }
}

dependencies {
    implementation(kotlin("stdlib"))

    testFixturesApi(project(":api"))
    testFixturesApi("com.github.tschuchortdev:kotlin-compile-testing-ksp:${Versions.KotlinCompileTesting}")

    testImplementation(testFixtures(project(":ksp-driver")))
    testImplementation(kotlin("test"))
}