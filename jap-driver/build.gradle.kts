plugins {
    kotlin("jvm")
    kotlin("kapt")
}

group = project.ext["group"] as String
version = project.ext["version"] as String

kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).set11Jdk()
    }

    sourceSets.test {
        kotlin.srcDir("${project.buildDir}/generated/kapt/test/kotlin")
    }
}

java {
    toolchain {
        set11Jdk()
    }
    sourceSets.test {
        java.srcDir("${project.buildDir}/generated/kapt/test/java")
    }
}


dependencies {
    implementation(project(":core"))
    implementation(project(":generator"))
    implementation(kotlin("stdlib"))

    implementation("com.google.auto:auto-common:${Versions.AutoCommon}")
}