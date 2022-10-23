plugins {
    id("daggerlite.artifact")
    id("daggerlite.documented")
}

dependencies {
    api(project(":lang:api"))
    api(project(":validation:api"))
    implementation(project(":api:public"))

    implementation(kotlin("stdlib"))
}