plugins {
    id("daggerlite.artifact")
}

val kspVersion: String by extra
val junitVersion: String by extra

dependencies {
    implementation(project(":api:public"))
    implementation(project(":processor:common"))
    implementation(project(":lang:ksp"))

    implementation(kotlin("stdlib"))
    implementation("com.google.devtools.ksp:symbol-processing-api:$kspVersion")
}