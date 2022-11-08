plugins {
    id("yatagan.base-module")
}

val dokkaVersion: String by extra

dependencies {
    compileOnly("org.jetbrains.dokka:dokka-base:$dokkaVersion")
    compileOnly("org.jetbrains.dokka:dokka-core:$dokkaVersion")
}
