plugins {
    id("daggerlite.artifact")
}

val kotlinCoroutinesCoreVersion: String by extra

dependencies {
    implementation(project(":base"))
    implementation(project(":validation-impl"))
    implementation(project(":validation-format"))
    implementation(project(":graph-impl"))
    implementation(project(":core-impl"))
    implementation(project(":generator"))
    implementation(project(":spi-impl"))
    implementation(kotlin("stdlib"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutinesCoreVersion")
}