plugins {
    id("daggerlite.base-module")
    application
}

val kotlinPoetVersion: String by extra
val kotlinxCliVersion: String by extra

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-cli:$kotlinxCliVersion")
    implementation("com.squareup:kotlinpoet:$kotlinPoetVersion")
}

application {
    mainClass.set("com.yandex.daggerlite.testing.generation.Standalone")
}