plugins {
    id("daggerlite.artifact")
}

dependencies {
    api(project(":api"))

    implementation(project(":dynamic"))
}