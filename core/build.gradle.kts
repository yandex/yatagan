plugins {
    kotlin("jvm")
}

group = project.ext["group"] as String
version = project.ext["version"] as String

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
    api(project(":api"))

    implementation(kotlin("stdlib"))
}