plugins {
    id("daggerlite.artifact")
}

val javaPoetVersion: String by extra

dependencies {
    api("com.squareup:javapoet:$javaPoetVersion")
    implementation(kotlin("stdlib"))
}