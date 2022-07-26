plugins {
    id("daggerlite.artifact")
}

dependencies {
    api(project(":core"))
    api(project(":graph"))
    api(project(":validation"))

    implementation(kotlin("stdlib"))

    testImplementation(kotlin("test"))
}