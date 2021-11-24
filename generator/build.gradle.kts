plugins {
    id("daggerlite.artifact")
}

dependencies {
    implementation(project(":api"))
    implementation(project(":graph"))
    implementation(project(":base"))
    implementation(project(":generator-poetry"))
    implementation(project(":generator-lang"))
    implementation(kotlin("stdlib"))
}