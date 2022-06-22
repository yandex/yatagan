plugins {
    id("daggerlite.artifact")
}

dependencies {
    api(project(":lang-common"))

    implementation(project(":api"))
    implementation(project(":base"))
    implementation(kotlin("stdlib"))
}