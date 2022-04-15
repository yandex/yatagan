plugins {
    id("daggerlite.base-module")
    application
}

val kotlinPoetVersion: String by extra

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.4")
    implementation("com.squareup:kotlinpoet:$kotlinPoetVersion")
}

application {
    mainClass.set("com.yandex.daggerlite.testing.generation.Standalone")
}