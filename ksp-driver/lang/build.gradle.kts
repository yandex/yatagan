plugins {
    id("daggerlite.artifact")
}

val kspVersion: String by extra

dependencies {
    implementation(project(":api"))
    implementation(project(":core-lang"))
    implementation(project(":generator-lang"))

    implementation(kotlin("stdlib"))
    implementation("com.google.devtools.ksp:symbol-processing-api:$kspVersion")
}