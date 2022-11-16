plugins {
    id("yatagan.base-module")
}

val dokkaVersion: String by extra

dependencies {
    compileOnly(libs.dokka.base)
    compileOnly(libs.dokka.core)
}
