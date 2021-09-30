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
    // Include javax.inject API
    api("javax.inject:javax.inject:1")

    // no stdlib required in api module.
    //implementation(kotlin("stdlib"))
}