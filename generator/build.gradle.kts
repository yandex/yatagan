plugins {
    id("daggerlite.artifact")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":generator-poetry"))
    implementation(project(":generator-lang"))
    implementation(kotlin("stdlib"))
}