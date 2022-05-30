import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("daggerlite.artifact")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + listOf(
            // We rely heavily on jvm types/signatures discovery, so no use scattering opt-ins throughout the code.
            "-opt-in=com.google.devtools.ksp.KspExperimental",
        )
    }
}

val kspVersion: String by extra

dependencies {
    implementation(project(":api"))
    implementation(project(":base"))
    api(project(":lang"))
    api(project(":generator-lang"))

    implementation(kotlin("stdlib"))
    implementation("com.google.devtools.ksp:symbol-processing-api:$kspVersion")
}