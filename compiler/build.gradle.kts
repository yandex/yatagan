plugins {
    kotlin("jvm")
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

    kspTest(project(":compiler"))
    testImplementation(kotlin("test"))
    testImplementation(project(":generator:poetry"))  // MAYBE: remove this as we don't need it in tests.
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing-ksp:${Versions.KotlinCompileTesting}")
}