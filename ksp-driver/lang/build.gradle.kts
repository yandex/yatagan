plugins {
    id("daggerlite.artifact")
}

val kspVersion: String by extra

dependencies {
    implementation(project(":api"))
    implementation(project(":base"))
    api(project(":core-lang"))
    api(project(":generator-lang"))

    implementation(kotlin("stdlib"))
    implementation("com.google.devtools.ksp:symbol-processing-api:$kspVersion")
}